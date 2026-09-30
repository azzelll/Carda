package id.carda.core.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NominalDeviceAssessmentTest {
    private val baseline = DeviceHardwareSnapshot(26, listOf("arm64-v8a"), 4,
        3_000_000_000L, 250_000_000L)

    @Test fun matchingHardwareStillNeedsStreamAndCadence() {
        val pending = assessNominalDevice(baseline, null, null, null)
        assertNull(pending.analysis720p)
        assertNull(pending.observed30Fps)
        assertFalse(pending.allPassed)
        assertTrue(assessNominalDevice(baseline, 720, 1280, 30.0).allPassed)
    }

    @Test fun eachNominalShortfallIsVisibleWithoutGuessingSupport() {
        val limited = baseline.copy(abis = listOf("armeabi-v7a"), availableProcessors = 2,
            totalRamBytes = 2_000_000_000L, freeStorageBytes = 100_000_000L)
        val report = assessNominalDevice(limited, 640, 480, 24.0)
        assertFalse(report.arm64)
        assertFalse(report.fourProcessors)
        assertFalse(report.threeGbRam)
        assertFalse(report.storage250Mb)
        assertFalse(report.analysis720p!!)
        assertFalse(report.observed30Fps!!)
    }
}
