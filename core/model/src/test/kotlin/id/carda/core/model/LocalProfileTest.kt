package id.carda.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalProfileTest {
    @Test fun bmiUsesKilogramsAndMetersSquared() {
        val profile = LocalProfile("account", heightCm = 170.0, weightKg = 68.0)
        assertEquals(23.5294, profile.bmi!!, 0.0001)
    }

    @Test fun incompleteDimensionsDoNotGuessBmi() {
        assertNull(LocalProfile("account", heightCm = 170.0).bmi)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidWeightIsRejected() {
        LocalProfile("account", heightCm = 170.0, weightKg = -1.0)
    }
}
