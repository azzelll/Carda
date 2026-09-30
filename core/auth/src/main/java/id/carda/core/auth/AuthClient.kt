package id.carda.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AuthSession(
    val accountId: String,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochMillis: Long,
) {
    init { validateSessionFields(this) }
    override fun toString(): String = "AuthSession(accountId=$accountId, tokens=redacted)"
}

class AuthApiException(val status: Int, val code: String) : Exception(code)

/** No endpoint may downgrade to plaintext transport outside a loopback debug harness. */
object AuthEndpointPolicy {
    fun isAllowed(baseUrl: String, allowDebugLoopbackHttp: Boolean): Boolean = try {
        val uri = URI(baseUrl)
        val host = uri.host?.lowercase()
        uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.path.orEmpty().trimEnd('/').isEmpty() &&
            ((uri.scheme.equals("https", true) && host != null) ||
                (allowDebugLoopbackHttp && uri.scheme.equals("http", true) &&
                    host in setOf("localhost", "127.0.0.1", "10.0.2.2")))
    } catch (_: Exception) { false }
}

/** Identity-only HTTP client. It sends no profile, measurement, PPG, frame, or device data. */
class AuthClient(
    private val baseUrl: String,
    private val store: EncryptedSessionStore,
    allowDebugLoopbackHttp: Boolean = false,
) : IdentityAccess {
    init { require(AuthEndpointPolicy.isAllowed(baseUrl, allowDebugLoopbackHttp)) }

    fun offlineSession(): AuthSession? = store.read()

    override suspend fun register(email: String, password: String) { request(
        "POST", "/register", JSONObject().put("email", email).put("password", password), null,
    ) }

    override suspend fun verifyEmail(token: String, newPassword: String) { request(
        "POST", "/verify-email", JSONObject().put("token", token).put("newPassword", newPassword), null,
    ) }

    override suspend fun login(email: String, password: String): AuthSession {
        val response = request("POST", "/login",
            JSONObject().put("email", email).put("password", password), null)
        return parseSession(response).also(store::write)
    }

    suspend fun refresh(): AuthSession {
        val existing = store.read() ?: throw AuthApiException(401, "no_local_session")
        val response = request("POST", "/refresh",
            JSONObject().put("refreshToken", existing.refreshToken), null)
        return validateRefreshedSession(existing, parseSession(response)).also(store::write)
    }

    override suspend fun logout() {
        if (store.read() == null) return
        try {
            val current = activeSession()
            request("POST", "/logout", JSONObject(), current.accessToken)
        } finally {
            // A failed network revocation is surfaced to the caller; local access still ends.
            store.clear()
        }
    }

    override suspend fun requestPasswordReset(email: String) { request(
        "POST", "/password-reset/request", JSONObject().put("email", email), null,
    ) }

    override suspend fun confirmPasswordReset(token: String, newPassword: String) { request(
        "POST", "/password-reset/confirm",
        JSONObject().put("token", token).put("newPassword", newPassword), null,
    ) }

    override suspend fun deleteAccount(password: String) {
        val current = activeSession()
        request("DELETE", "/account", JSONObject().put("password", password), current.accessToken)
        store.clear()
    }

    private suspend fun activeSession(): AuthSession {
        val existing = store.read() ?: throw AuthApiException(401, "no_local_session")
        return if (existing.accessExpiresAtEpochMillis <= System.currentTimeMillis() + 30_000L)
            refresh() else existing
    }

    private fun parseSession(response: JSONObject): AuthSession =
        AuthSession(
            accountId = response.getString("accountId"),
            accessToken = response.getString("accessToken"),
            refreshToken = response.getString("refreshToken"),
            accessExpiresAtEpochMillis = sessionExpiry(System.currentTimeMillis(),
                response.getLong("expiresInSeconds")),
        )

    private suspend fun request(
        method: String, route: String, body: JSONObject, bearer: String?,
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl.trimEnd('/') + "/api/v1/auth" + route).openConnection()
            as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            // Redirects are not part of the identity API contract; never resend credentials elsewhere.
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val payload = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readAuthPayload() }.orEmpty()
            if (status !in 200..299) {
                val code = runCatching { JSONObject(payload).getString("code") }.getOrDefault("request_failed")
                throw AuthApiException(status, code)
            }
            if (payload.isBlank()) JSONObject() else JSONObject(payload)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun validateRefreshedSession(existing: AuthSession, refreshed: AuthSession): AuthSession {
    require(refreshed.accountId == existing.accountId) { "Refreshed account differs from local session" }
    return refreshed
}

/** AES-GCM with a non-exportable Android Keystore key; the ciphertext is excluded from backup. */
class EncryptedSessionStore(context: Context) : SessionStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, "carda_auth_session.bin"))

    override fun read(): AuthSession? {
        if (!file.baseFile.exists()) return null
        return runCatching {
            val blob = file.openRead().use { it.readBytes() }
            require(blob.size > IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_SIZE))
            val plain = cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
            val json = JSONObject(String(plain, Charsets.UTF_8))
            AuthSession(json.getString("accountId"), json.getString("accessToken"),
                json.getString("refreshToken"), json.getLong("accessExpiresAtEpochMillis"))
        }.getOrElse {
            clear()
            null
        }
    }

    fun write(session: AuthSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val json = JSONObject()
            .put("accountId", session.accountId)
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("accessExpiresAtEpochMillis", session.accessExpiresAtEpochMillis)
        val blob = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try {
            stream.write(blob)
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            file.failWrite(stream)
            throw failure
        }
    }

    override fun clear() { file.delete() }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "carda.auth.session.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
