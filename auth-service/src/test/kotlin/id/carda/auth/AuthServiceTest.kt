package id.carda.auth

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

class AuthServiceTest {
    private class CountingEncoder : PasswordEncoder {
        var comparisons = 0
        var encodings = 0
        override fun encode(rawPassword: CharSequence?): String {
            encodings++
            return "test-hash"
        }
        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
            comparisons++
            return false
        }
    }

    private val repository = mock(IdentityRepository::class.java)
    private val limiter = mock(RateLimiter::class.java)
    private val mailer = mock(ActionMailer::class.java)
    private val deliveries = mock(ActionDeliveryAfterCommit::class.java)
    private val tokenFactory = TokenFactory()
    private val passwords = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
    private val now = Instant.parse("2026-09-29T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val service = AuthService(repository, tokenFactory, passwords, limiter, mailer, deliveries, clock)

    @Test
    fun `verified existing registration performs hash work before generic response`() {
        val counting = CountingEncoder()
        val checked = AuthService(repository, tokenFactory, counting, limiter, mailer, deliveries, clock)
        counting.encodings = 0 // Ignore the service's non-existent-login hash created at startup.
        `when`(repository.findAccount("exists@example.org")).thenReturn(
            AccountRecord(UUID.randomUUID(), "exists@example.org", "test-hash", now),
        )

        val result = checked.register(RegisterRequest("exists@example.org", "synthetic-password"), "127.0.0.1")

        assertEquals("accepted", result.status)
        assertEquals(1, counting.encodings)
    }

    @Test
    fun `registration queues code and returns generic response`() {
        `when`(repository.createAccount(
            any(UUID::class.java) ?: UUID(0, 0), anyString(), anyString(),
            any(Instant::class.java) ?: Instant.EPOCH,
        )).thenReturn(true)
        val result = service.register(RegisterRequest("new@example.org", "synthetic-password"), "127.0.0.1")

        assertEquals("accepted", result.status)
        assertEquals(1, mockingDetails(repository).invocations.count { it.method.name == "createActionToken" })
        assertEquals(1, mockingDetails(deliveries).invocations.count { it.method.name == "verification" })
    }

    @Test
    fun `unverified account can request another code after a delivery failure`() {
        val email = "unverified@example.org"
        `when`(repository.findAccount(email)).thenReturn(AccountRecord(
            UUID.randomUUID(), email, passwords.encode("synthetic-password")!!, null,
        ))
        val request = RegisterRequest(email, "synthetic-password")

        assertEquals("accepted", service.register(request, "127.0.0.1").status)
        assertEquals("accepted", service.register(request, "127.0.0.1").status)
        assertEquals(2, mockingDetails(repository).invocations.count { it.method.name == "createActionToken" })
        assertEquals(2, mockingDetails(deliveries).invocations.count { it.method.name == "verification" })
    }

    @Test
    fun `registration of existing unverified email issues new code despite different provisional password`() {
        val email = "victim@example.org"
        `when`(repository.findAccount(email)).thenReturn(AccountRecord(
            UUID.randomUUID(), email, passwords.encode("attacker-provisional-password")!!, null,
        ))

        val response = service.register(
            RegisterRequest(email, "recipient-chosen-password"), "127.0.0.1",
        )

        assertEquals("accepted", response.status)
        assertEquals(1, mockingDetails(repository).invocations.count { it.method.name == "createActionToken" })
        assertEquals(1, mockingDetails(deliveries).invocations.count { it.method.name == "verification" })
    }

    @Test
    fun `email code redemption must replace provisional password while verifying`() {
        val id = UUID.randomUUID()
        val token = "synthetic-verification-token"
        val chosenPassword = "recipient-chosen-password"
        `when`(repository.consumeActionToken("verify", tokenFactory.hash(token), now)).thenReturn(id)
        `when`(repository.verifyUnverifiedAccountWithPassword(
            any(UUID::class.java) ?: UUID(0, 0), anyString(), any(Instant::class.java) ?: Instant.EPOCH,
        )).thenReturn(true)

        service.verifyEmail(token, chosenPassword, "127.0.0.1")

        val replacement = mockingDetails(repository).invocations.single {
            it.method.name == "verifyUnverifiedAccountWithPassword"
        }
        assertEquals(id, replacement.arguments[0])
        assertTrue(passwords.matches(chosenPassword, replacement.arguments[1] as String))
        assertEquals(now, replacement.arguments[2])
    }

    @Test
    fun `code for already verified account cannot replace password`() {
        val token = "old-verification-token"
        `when`(repository.consumeActionToken("verify", tokenFactory.hash(token), now)).thenReturn(UUID.randomUUID())
        // The repository's conditional update reports zero rows when verified_at is already set.
        val error = assertThrows(ApiFailure::class.java) {
            service.verifyEmail(token, "another-chosen-password", "127.0.0.1")
        }
        assertEquals(HttpStatus.BAD_REQUEST, error.status)
        assertEquals("invalid_token", error.code)
    }

    @Test
    fun `unknown verification code cannot modify an account`() {
        val error = assertThrows(ApiFailure::class.java) {
            service.verifyEmail("wrong-verification-token", "recipient-chosen-password", "127.0.0.1")
        }
        assertEquals(HttpStatus.BAD_REQUEST, error.status)
        assertEquals("invalid_token", error.code)
        assertEquals(0, mockingDetails(repository).invocations.count {
            it.method.name == "verifyUnverifiedAccountWithPassword"
        })
    }

    @Test
    fun `unknown and existing account both perform password verification on failed login`() {
        val counting = CountingEncoder()
        val checked = AuthService(repository, tokenFactory, counting, limiter, mailer, deliveries, clock)
        `when`(repository.findAccount("exists@example.org")).thenReturn(
            AccountRecord(UUID.randomUUID(), "exists@example.org", "test-hash", now),
        )

        val missing = assertThrows(ApiFailure::class.java) {
            checked.login(LoginRequest("missing@example.org", "wrong-password"), "127.0.0.1")
        }
        assertEquals(1, counting.comparisons, "Missing accounts must not skip the expensive password comparison")
        val wrongPassword = assertThrows(ApiFailure::class.java) {
            checked.login(LoginRequest("exists@example.org", "wrong-password"), "127.0.0.1")
        }
        assertEquals(2, counting.comparisons)
        assertEquals(HttpStatus.UNAUTHORIZED, missing.status)
        assertEquals(missing.status, wrongPassword.status)
        assertEquals("invalid_credentials", missing.code)
        assertEquals(missing.code, wrongPassword.code)
    }

    @Test
    fun `unverified account cannot log in`() {
        val id = UUID.randomUUID()
        `when`(repository.findAccount("person@example.org"))
            .thenReturn(AccountRecord(id, "person@example.org", passwords.encode("long-secret-password")!!, null))

        val error = assertThrows(ApiFailure::class.java) {
            service.login(LoginRequest("Person@Example.org", "long-secret-password"), "127.0.0.1")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, error.status)
        assertEquals("invalid_credentials", error.code)
    }

    @Test
    fun `login creates expiring opaque token pair for verified account`() {
        val id = UUID.randomUUID()
        `when`(repository.findAccount("person@example.org"))
            .thenReturn(AccountRecord(id, "person@example.org", passwords.encode("long-secret-password")!!, now))

        val result = service.login(LoginRequest("Person@Example.org", "long-secret-password"), "127.0.0.1")

        assertEquals(id, result.accountId)
        assertEquals(900, result.expiresInSeconds)
        assertTrue(result.accessToken.length >= 40)
        assertTrue(result.refreshToken.length >= 40)
        assertTrue(result.accessToken != result.refreshToken)
        val stored = mockingDetails(repository).invocations.single { it.method.name == "createSession" }
        assertEquals(id, stored.arguments[1])
        assertEquals(now.plusSeconds(900), stored.arguments[4])
        assertEquals(now.plusSeconds(30L * 86400), stored.arguments[5])
    }

    @Test
    fun `invalid refresh token produces no session`() {
        val error = assertThrows(ApiFailure::class.java) { service.refresh("invalid", "127.0.0.1") }
        assertEquals(HttpStatus.UNAUTHORIZED, error.status)
    }

    @Test
    fun `reset request has same public response for missing account`() {
        val result = service.requestPasswordReset("missing@example.org", "127.0.0.1")
        assertEquals("accepted", result.status)
    }

    @Test
    fun `reset request for known email queues code with generic response`() {
        val email = "person@example.org"
        `when`(repository.findAccount(email)).thenReturn(AccountRecord(
            UUID.randomUUID(), email, passwords.encode("long-secret-password")!!, now,
        ))
        val result = service.requestPasswordReset(email, "127.0.0.1")

        assertEquals("accepted", result.status)
        assertEquals(1, mockingDetails(deliveries).invocations.count { it.method.name == "passwordReset" })
    }

    @Test
    fun `password reset revokes prior sessions`() {
        val id = UUID.randomUUID()
        val token = "valid-reset-token"
        `when`(repository.consumeActionToken("reset", tokenFactory.hash(token), now)).thenReturn(id)

        service.confirmPasswordReset(token, "new-long-secret-password", "127.0.0.1")

        val replacement = mockingDetails(repository).invocations.single { it.method.name == "replacePassword" }
        assertEquals(id, replacement.arguments[0])
        assertTrue(passwords.matches("new-long-secret-password", replacement.arguments[1] as String))
    }

    @Test
    fun `delete account requires current password`() {
        val id = UUID.randomUUID()
        `when`(repository.findAccount(id))
            .thenReturn(AccountRecord(id, "person@example.org", passwords.encode("long-secret-password")!!, now))

        val error = assertThrows(ApiFailure::class.java) { service.deleteAccount(id, "wrong", "127.0.0.1") }
        assertEquals(HttpStatus.UNAUTHORIZED, error.status)
        val throttle = mockingDetails(limiter).invocations.single { it.method.name == "requireBelow" }
        assertEquals("delete", throttle.arguments[0])
        assertEquals("127.0.0.1", throttle.arguments[1])
        assertEquals(id.toString(), throttle.arguments[2])
        assertEquals(50, throttle.arguments[3])
        assertEquals(5, throttle.arguments[4])
    }
}
