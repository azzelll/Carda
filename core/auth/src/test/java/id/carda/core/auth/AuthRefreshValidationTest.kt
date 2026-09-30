package id.carda.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthRefreshValidationTest {
    private val accountA = "ec87a9d1-6035-443b-b14a-ad36d879c9d8"
    private val accountB = "43718466-854c-4c97-b2c8-50dfb369fa2a"
    private val existing = AuthSession(accountA, "old-access", "old-refresh", 1L)

    @Test fun acceptsNewTokensForSameAccount() {
        val refreshed = AuthSession(accountA, "new-access", "new-refresh", 2L)
        assertEquals(refreshed, validateRefreshedSession(existing, refreshed))
    }

    @Test fun rejectsCrossAccountRefreshBeforePersistence() {
        val wrongAccount = AuthSession(accountB, "wrong-access", "wrong-refresh", 2L)
        assertThrows(IllegalArgumentException::class.java) {
            validateRefreshedSession(existing, wrongAccount)
        }
    }
}
