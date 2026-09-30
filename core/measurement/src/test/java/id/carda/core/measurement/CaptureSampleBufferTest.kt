package id.carda.core.measurement

import id.carda.core.model.CaptureDuration
import id.carda.core.ppg.PpgSample
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToLong

class CaptureSampleBufferTest {
    @Test fun bothDurationsRetainTheFullEndpointAt120ObservationsPerSecond() {
        CaptureDuration.entries.forEach { duration ->
            val buffer = CaptureSampleBuffer(duration)
            for (index in 0..duration.seconds * 120) {
                assertTrue("Endpoint rejected for ${duration.seconds}s", buffer.offer(sample(index)))
            }
            assertEquals(duration.seconds * 1_000_000_000L,
                buffer.values.last().timestampNanos - buffer.values.first().timestampNanos)
        }
    }

    @Test fun overflowRejectsWithoutDiscardingTheStartOrGrowingMemory() {
        val buffer = CaptureSampleBuffer(CaptureDuration.THIRTY_SECONDS)
        var index = 0
        while (buffer.offer(sample(index))) index++
        val accepted = buffer.values.size
        assertFalse(buffer.offer(sample(index + 1)))
        assertEquals(accepted, buffer.values.size)
        assertEquals(0L, buffer.values.first().timestampNanos)
        assertTrue(accepted <= 30 * 120 + 2)
    }

    @Test fun clearingDropsPriorSessionSamplesAndRestoresCapacity() {
        val buffer = CaptureSampleBuffer(CaptureDuration.SIXTY_SECONDS)
        assertTrue(buffer.offer(sample(0)))
        buffer.clear()
        assertTrue(buffer.values.isEmpty())
        assertTrue(buffer.offer(sample(120)))
        assertEquals(1_000_000_000L, buffer.values.first().timestampNanos)
    }

    @Test fun terminalEvaluationIsReachedAtQuantized120FpsBeforeBufferOverflow() {
        CaptureDuration.entries.forEach { duration ->
            val buffer = CaptureSampleBuffer(duration)
            var lastEvaluation: Long? = null
            var evaluatedAtTarget = false
            for (index in 0..duration.seconds * 120 + 1) {
                val current = sampleAt(index * 8_333_333L)
                assertTrue(buffer.offer(current))
                val elapsed = current.timestampNanos - buffer.values.first().timestampNanos
                if (shouldEvaluateCapture(current.timestampNanos, lastEvaluation,
                        elapsed, duration.seconds)) {
                    lastEvaluation = current.timestampNanos
                    if (elapsed >= duration.seconds * 1_000_000_000L) evaluatedAtTarget = true
                }
            }
            assertTrue("Terminal window skipped for ${duration.seconds}s", evaluatedAtTarget)
        }
    }

    private fun sample(index: Int) = sampleAt((index * 1_000_000_000.0 / 120).roundToLong())

    private fun sampleAt(timestampNanos: Long) = PpgSample(timestampNanos = timestampNanos,
        red = 120.0, green = 20.0, blue = 10.0, coverageFraction = 1.0, saturatedFraction = 0.0,
        clippedFraction = 0.0, motionScore = 0.0, torchOn = true)
}
