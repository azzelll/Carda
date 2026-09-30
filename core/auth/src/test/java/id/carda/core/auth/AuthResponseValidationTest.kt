package id.carda.core.auth

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthResponseValidationTest {
    private val account = "ec87a9d1-6035-443b-b14a-ad36d879c9d8"

    @Test fun malformedSessionCannotBePersisted() {
        assertThrows(IllegalArgumentException::class.java) { AuthSession("not-uuid", "a", "r", 1L) }
        assertThrows(IllegalArgumentException::class.java) { AuthSession(account, "", "r", 1L) }
        assertThrows(IllegalArgumentException::class.java) { AuthSession(account, "a\r\n", "r", 1L) }
        assertThrows(IllegalArgumentException::class.java) { AuthSession(account, "a", "r", -1L) }
    }

    @Test fun serverLifetimeIsBoundedAndCannotOverflow() {
        assertEquals(901_000L, sessionExpiry(1_000L, 900L))
        assertThrows(IllegalArgumentException::class.java) { sessionExpiry(1_000L, -1L) }
        assertThrows(IllegalArgumentException::class.java) { sessionExpiry(1_000L, 3_601L) }
        assertThrows(ArithmeticException::class.java) { sessionExpiry(Long.MAX_VALUE, 900L) }
    }

    @Test fun oversizedPayloadRejectedBeforeParsingAndTokensAreRedacted() {
        assertEquals("{}", ByteArrayInputStream("{}".toByteArray()).readAuthPayload())
        assertThrows(IllegalArgumentException::class.java) {
            ByteArrayInputStream(ByteArray(MAX_AUTH_RESPONSE_BYTES + 1)).readAuthPayload()
        }
        val session = AuthSession(account, "secret-access", "secret-refresh", 1L)
        assertFalse(session.toString().contains("secret-access"))
        assertFalse(session.toString().contains("secret-refresh"))
    }
}
