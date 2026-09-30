package id.carda.auth

import java.time.Clock
import java.time.Duration
import java.util.Locale
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val repository: IdentityRepository,
    private val tokenFactory: TokenFactory,
    private val passwords: PasswordEncoder,
    private val limiter: RateLimiter,
    private val mailer: ActionMailer,
    private val deliveries: ActionDeliveryAfterCommit,
    private val clock: Clock,
) {
    private val accessLife = Duration.ofMinutes(15)
    private val refreshLife = Duration.ofDays(30)
    // Keep the costly password check on the failed-login path even when no account exists.
    // The random plaintext is discarded; only this service-instance hash remains in memory.
    private val nonexistentAccountHash = passwords.encode(UUID.randomUUID().toString())!!

    @Transactional
    fun register(request: RegisterRequest, remoteIp: String): AcceptedResponse {
        val email = normalize(request.email)
        limiter.requireBelow("register", remoteIp, email, 100, 5)
        mailer.ensureConfigured()
        val existing = repository.findAccount(email)
        if (existing?.verifiedAt != null) {
            // Match the Argon2 work performed for a new account before returning the generic response.
            passwords.encode(request.password)!!
            return AcceptedResponse()
        }
        val accountId = if (existing == null) {
            val id = UUID.randomUUID()
            if (!repository.createAccount(id, email, passwords.encode(request.password)!!, clock.instant())) return AcceptedResponse()
            id
        } else {
            // Anyone may have pre-registered this address. Never let its provisional password block its owner.
            passwords.encode(request.password)!!
            existing.id
        }
        val token = tokenFactory.issue()
        repository.createActionToken(accountId, "verify", token.hash, clock.instant().plus(Duration.ofHours(24)))
        deliveries.verification(email, token.plaintext)
        return AcceptedResponse()
    }

    @Transactional
    fun verifyEmail(token: String, newPassword: String, remoteIp: String) {
        limiter.requireBelow("verify", remoteIp, null, 300, 0)
        val now = clock.instant()
        val id = repository.consumeActionToken("verify", tokenFactory.hash(token), now)
            ?: throw ApiFailure(HttpStatus.BAD_REQUEST, "invalid_token")
        if (!repository.verifyUnverifiedAccountWithPassword(id, passwords.encode(newPassword)!!, now)) {
            throw ApiFailure(HttpStatus.BAD_REQUEST, "invalid_token")
        }
    }

    @Transactional
    fun login(request: LoginRequest, remoteIp: String): SessionResponse {
        val email = normalize(request.email)
        limiter.requireBelow("login", remoteIp, email, 300, 8)
        val account = repository.findAccount(email)
        val validPassword = passwords.matches(request.password, account?.passwordHash ?: nonexistentAccountHash)
        if (account == null || !validPassword || account.verifiedAt == null) {
            throw ApiFailure(HttpStatus.UNAUTHORIZED, "invalid_credentials")
        }
        limiter.clearEmail("login", email)
        val now = clock.instant()
        val tokens = tokenFactory.issueSession()
        repository.createSession(UUID.randomUUID(), account.id, tokens, now, now.plus(accessLife), now.plus(refreshLife))
        return response(account.id, tokens)
    }

    @Transactional
    fun refresh(refreshToken: String, remoteIp: String): SessionResponse {
        limiter.requireBelow("refresh", remoteIp, null, 300, 0)
        val now = clock.instant()
        val tokens = tokenFactory.issueSession()
        val accountId = repository.rotateSession(tokenFactory.hash(refreshToken), tokens, now, now.plus(accessLife), now.plus(refreshLife))
            ?: throw ApiFailure(HttpStatus.UNAUTHORIZED, "invalid_credentials")
        return response(accountId, tokens)
    }

    @Transactional
    fun logout(accountId: UUID, accessToken: String) {
        repository.revokeAccess(accountId, tokenFactory.hash(accessToken), clock.instant())
    }

    @Transactional
    fun requestPasswordReset(emailInput: String, remoteIp: String): AcceptedResponse {
        val email = normalize(emailInput)
        limiter.requireBelow("reset", remoteIp, email, 100, 3)
        mailer.ensureConfigured()
        val account = repository.findAccount(email)
        if (account != null && account.verifiedAt != null) {
            val token = tokenFactory.issue()
            repository.createActionToken(account.id, "reset", token.hash, clock.instant().plus(Duration.ofMinutes(30)))
            deliveries.passwordReset(email, token.plaintext)
        }
        return AcceptedResponse()
    }

    @Transactional
    fun confirmPasswordReset(token: String, newPassword: String, remoteIp: String) {
        limiter.requireBelow("reset-confirm", remoteIp, null, 300, 0)
        val now = clock.instant()
        val id = repository.consumeActionToken("reset", tokenFactory.hash(token), now)
            ?: throw ApiFailure(HttpStatus.BAD_REQUEST, "invalid_token")
        repository.replacePassword(id, passwords.encode(newPassword)!!, now)
    }

    @Transactional
    fun deleteAccount(id: UUID, password: String, remoteIp: String) {
        limiter.requireBelow("delete", remoteIp, id.toString(), 50, 5)
        val account = repository.findAccount(id) ?: throw ApiFailure(HttpStatus.UNAUTHORIZED, "invalid_credentials")
        if (!passwords.matches(password, account.passwordHash)) {
            throw ApiFailure(HttpStatus.UNAUTHORIZED, "invalid_credentials")
        }
        repository.deleteAccount(id)
    }

    private fun response(id: UUID, tokens: SessionTokens) = SessionResponse(id, tokens.access.plaintext, tokens.refresh.plaintext, accessLife.seconds)
    private fun normalize(email: String) = email.trim().lowercase(Locale.ROOT)
}
