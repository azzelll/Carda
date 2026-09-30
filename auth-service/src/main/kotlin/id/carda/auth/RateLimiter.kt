package id.carda.auth

import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class RateLimiter(
    private val repository: IdentityRepository,
    private val clock: Clock,
    @Value("\${app.rate-limit-pepper}") private val pepper: String,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun requireBelow(action: String, ip: String, email: String?, ipLimit: Int, emailLimit: Int) {
        val now = clock.instant()
        val boundary = now.minus(Duration.ofMinutes(15))
        if (repository.recordAttempt(key("$action:ip:$ip"), now, boundary) > ipLimit) {
            throw ApiFailure(HttpStatus.TOO_MANY_REQUESTS, "rate_limited")
        }
        if (email != null && repository.recordAttempt(key("$action:email:$email"), now, boundary) > emailLimit) {
            throw ApiFailure(HttpStatus.TOO_MANY_REQUESTS, "rate_limited")
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun clearEmail(action: String, email: String) = repository.clearAttempt(key("$action:email:$email"))

    private fun key(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pepper.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
