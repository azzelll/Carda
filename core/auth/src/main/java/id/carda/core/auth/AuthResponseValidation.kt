package id.carda.core.auth

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

internal const val MAX_AUTH_RESPONSE_BYTES = 65_536

internal fun validateSessionFields(session: AuthSession) {
    require(UUID.fromString(session.accountId).toString() == session.accountId) { "Invalid account ID" }
    require(session.accessExpiresAtEpochMillis > 0) { "Invalid session expiry" }
    require(listOf(session.accessToken, session.refreshToken).all { token ->
        token.length in 1..512 && token.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' }
    }) { "Invalid session token" }
}

internal fun sessionExpiry(nowEpochMillis: Long, expiresInSeconds: Long): Long {
    require(nowEpochMillis > 0 && expiresInSeconds in 1..3_600) { "Invalid access token lifetime" }
    return Math.addExact(nowEpochMillis, Math.multiplyExact(expiresInSeconds, 1_000))
}

/** Read at most one response budget plus a sentinel, before JSON parsing or storage. */
internal fun InputStream.readAuthPayload(): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4_096)
    while (true) {
        val remaining = MAX_AUTH_RESPONSE_BYTES + 1 - output.size()
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count == -1) break
        output.write(buffer, 0, count)
        require(output.size() <= MAX_AUTH_RESPONSE_BYTES) { "Auth response exceeds budget" }
    }
    return output.toString(Charsets.UTF_8.name())
}
