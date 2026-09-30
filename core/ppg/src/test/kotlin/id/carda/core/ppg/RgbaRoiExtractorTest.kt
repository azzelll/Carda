package id.carda.core.ppg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RgbaRoiExtractorTest {
    private val extractor = RgbaRoiExtractor()

    @Test fun extractionConfigurationControlsPixelGatesAndRejectsInvalidParameters() {
        val configured = RgbaRoiExtractor(ExtractionThresholds(minimumCoveredRed = 150,
            saturationLevel = 200, clipHigh = 220))
        val sample = configured.extract(1L, rgba(4, 4, 16) { _, _ -> 120 to (70 to 30) },
            4, 4, 16, true)
        assertEquals(0.0, sample.coverageFraction, 0.001)
        assertThrows(IllegalArgumentException::class.java) { ExtractionThresholds(redDominance = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { ExtractionThresholds(clipLow = 253, clipHigh = 2) }
    }

    @Test fun paddedRgbaRoiProducesAggregateObservations() {
        val input = rgba(width = 4, height = 4, rowStride = 20) { _, _ -> 120 to (75 to 50) }

        val sample = extractor.extract(1L, input, 4, 4, 20, torchOn = true)

        assertEquals(120.0, sample.red, 0.001)
        assertEquals(75.0, sample.green, 0.001)
        assertEquals(50.0, sample.blue, 0.001)
        assertEquals(1.0, sample.coverageFraction, 0.001)
        assertEquals(0.0, sample.saturatedFraction, 0.001)
        assertEquals(0.0, sample.clippedFraction, 0.001)
        assertEquals(0.0, sample.greenClippedFraction, 0.001)
        assertEquals(0.0, sample.motionScore, 0.001)
        assertTrue(sample.torchOn)
    }

    @Test fun flagsDarkSaturatedAndClippedPixelsSeparately() {
        val input = rgba(width = 4, height = 4, rowStride = 16) { x, _ ->
            if (x < 2) 10 to (10 to 10) else 255 to (100 to 50)
        }

        val sample = extractor.extract(2L, input, 4, 4, 16, torchOn = true)

        assertEquals(0.5, sample.coverageFraction, 0.001)
        assertEquals(0.5, sample.saturatedFraction, 0.001)
        assertEquals(0.5, sample.clippedFraction, 0.001)
    }

    @Test fun lowBlueChannelDoesNotMarkUsableRedPpgAsClipped() {
        val input = rgba(width = 4, height = 4, rowStride = 16) { _, _ ->
            120 to (70 to 0)
        }

        val sample = extractor.extract(2L, input, 4, 4, 16, torchOn = true)

        assertEquals(1.0, sample.coverageFraction, 0.001)
        assertEquals(0.0, sample.clippedFraction, 0.001)
    }

    @Test fun greenClippingIsRecordedSeparatelyFromUsableRedPpg() {
        val input = rgba(width = 4, height = 4, rowStride = 16) { x, _ ->
            120 to ((if (x == 0) 0 else 70) to 30)
        }
        val sample = extractor.extract(3L, input, 4, 4, 16, torchOn = true)
        assertEquals(0.0, sample.clippedFraction, 0.001)
        assertEquals(0.25, sample.greenClippedFraction, 0.001)
    }

    @Test fun motionUsesOnlyPreviousAggregatesAndResetClearsThem() {
        val firstBytes = rgba(width = 4, height = 4, rowStride = 16) { _, _ -> 100 to (60 to 40) }
        extractor.extract(1L, firstBytes, 4, 4, 16, torchOn = true)
        firstBytes.indices.forEach { firstBytes[it] = 0 }

        val same = extractor.extract(
            2L, rgba(4, 4, 16) { _, _ -> 100 to (60 to 40) }, 4, 4, 16, torchOn = true,
        )
        assertEquals(0.0, same.motionScore, 0.001)

        val changed = extractor.extract(
            3L, rgba(4, 4, 16) { _, _ -> 220 to (60 to 40) }, 4, 4, 16, torchOn = true,
        )
        assertTrue(changed.motionScore > 0.4)

        extractor.reset()
        val afterReset = extractor.extract(
            4L, rgba(4, 4, 16) { _, _ -> 100 to (60 to 40) }, 4, 4, 16, torchOn = true,
        )
        assertEquals(0.0, afterReset.motionScore, 0.001)
    }

    @Test fun rejectsIncompleteRoiBuffer() {
        assertThrows(IllegalArgumentException::class.java) {
            extractor.extract(1L, ByteArray(8), 4, 4, 16, torchOn = true)
        }
    }

    @Test fun resolutionChangeStartsAnewMotionBaseline() {
        extractor.extract(
            1L, rgba(4, 4, 16) { _, _ -> 100 to (60 to 40) }, 4, 4, 16, torchOn = true,
        )
        val resized = extractor.extract(
            2L, rgba(2, 2, 8) { _, _ -> 220 to (60 to 40) }, 2, 2, 8, torchOn = true,
        )
        assertEquals(0.0, resized.motionScore, 0.001)
    }

    private fun rgba(
        width: Int,
        height: Int,
        rowStride: Int,
        pixel: (Int, Int) -> Pair<Int, Pair<Int, Int>>,
    ): ByteArray {
        val bytes = ByteArray(rowStride * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val (red, greenBlue) = pixel(x, y)
                val (green, blue) = greenBlue
                val offset = y * rowStride + x * 4
                bytes[offset] = red.toByte()
                bytes[offset + 1] = green.toByte()
                bytes[offset + 2] = blue.toByte()
                bytes[offset + 3] = 255.toByte()
            }
        }
        return bytes
    }
}
