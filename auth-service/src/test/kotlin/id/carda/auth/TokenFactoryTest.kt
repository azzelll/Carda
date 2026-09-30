package id.carda.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenFactoryTest {
    @Test
    fun `tokens are random url safe and only hashes need storage`() {
        val factory = TokenFactory()
        val first = factory.issue()
        val second = factory.issue()
        assertNotEquals(first.plaintext, second.plaintext)
        assertEquals(64, first.hash.length)
        assertEquals(first.hash, factory.hash(first.plaintext))
        assertTrue(first.plaintext.matches(Regex("[A-Za-z0-9_-]+")))
    }
}
