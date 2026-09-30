package id.carda.core.camera

import androidx.camera.core.TorchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class CameraCapabilitySnapshotTest {
    @Test fun reportsCompensationWithoutClaimingActualSensorExposure() {
        assertEquals(
            "kompensasi eksposur +0.67 EV (indeks 2; rentang -3..3)",
            formatExposureCompensation(2, -3, 3, 1.0 / 3.0),
        )
    }

    @Test fun invalidCameraMetadataDoesNotProduceAClaim() {
        assertNull(formatExposureCompensation(4, -3, 3, 1.0 / 3.0))
        assertNull(formatExposureCompensation(0, 3, -3, 1.0 / 3.0))
        assertNull(formatExposureCompensation(0, -3, 3, 0.0))
        assertNull(formatExposureCompensation(0, -3, 3, Double.NaN))
    }

    @Test fun torchSnapshotOnlyDescribesObservedState() {
        assertEquals("torch aktif saat kamera siap", formatObservedTorchState(TorchState.ON))
        assertEquals("torch mati saat kamera siap", formatObservedTorchState(TorchState.OFF))
        assertNull(formatObservedTorchState(null))
        assertNull(formatObservedTorchState(-1))
    }

    @Test fun formatDoesNotChangeWithDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(
                "kompensasi eksposur -0.50 EV (indeks -1; rentang -2..2)",
                formatExposureCompensation(-1, -2, 2, 0.5),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }
}
