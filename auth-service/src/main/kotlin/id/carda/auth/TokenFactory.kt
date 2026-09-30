package id.carda.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.springframework.stereotype.Component

@Component
class TokenFactory {
    private val random = SecureRandom()

    fun issue(): IssuedToken {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return IssuedToken(token, hash(token))
    }

    fun hash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun issueSession(): SessionTokens = SessionTokens(issue(), issue())
}
