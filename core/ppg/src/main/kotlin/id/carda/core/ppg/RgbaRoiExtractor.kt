package id.carda.core.ppg

import kotlin.math.abs

/**
 * Extracts transient ROI aggregates from tightly packed RGBA pixels with optional row padding.
 * A single instance is confined to one camera-analysis thread and one capture session.
 * Only a 4x4 grid of previous red-channel means is retained for a motion proxy; no frame is kept.
 * Thresholds are provisional engineering heuristics, not calibrated finger-contact evidence.
 */
class RgbaRoiExtractor(private val thresholds: ExtractionThresholds = ExtractionThresholds()) {
    private var previousGrid: DoubleArray? = null
    private var previousWidth: Int? = null
    private var previousHeight: Int? = null

    fun extract(
        timestampNanos: Long,
        rgba: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        torchOn: Boolean,
    ): PpgSample {
        require(timestampNanos >= 0)
        require(width > 0 && height > 0)
        require(rowStride.toLong() >= width.toLong() * 4L)
        require((height - 1L) * rowStride + width.toLong() * 4L <= rgba.size.toLong())

        val blockSums = DoubleArray(GRID_SIZE * GRID_SIZE)
        val blockCounts = IntArray(GRID_SIZE * GRID_SIZE)
        val pixelCount = width.toLong() * height.toLong()
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var covered = 0L
        var saturated = 0L
        var clipped = 0L
        var greenClipped = 0L

        for (y in 0 until height) {
            val rowOffset = y * rowStride
            for (x in 0 until width) {
                val offset = rowOffset + x * 4
                val red = rgba[offset].toInt() and 0xff
                val green = rgba[offset + 1].toInt() and 0xff
                val blue = rgba[offset + 2].toInt() and 0xff
                redSum += red
                greenSum += green
                blueSum += blue
                // Red dominance is a tentative illuminated-finger proxy, not proof of contact.
                if (red >= thresholds.minimumCoveredRed && red >= green * thresholds.redDominance &&
                    red >= blue * thresholds.redDominance
                ) covered++
                if (red >= thresholds.saturationLevel || green >= thresholds.saturationLevel ||
                    blue >= thresholds.saturationLevel
                ) saturated++
                // The current PPG estimator uses red. A dark blue/green channel
                // alone must not label its usable red waveform as clipped.
                if (red <= thresholds.clipLow || red >= thresholds.clipHigh) clipped++
                if (green <= thresholds.clipLow || green >= thresholds.clipHigh) greenClipped++

                val blockX = (x.toLong() * GRID_SIZE / width).toInt().coerceAtMost(GRID_SIZE - 1)
                val blockY = (y.toLong() * GRID_SIZE / height).toInt().coerceAtMost(GRID_SIZE - 1)
                val blockIndex = blockY * GRID_SIZE + blockX
                blockSums[blockIndex] += red
                blockCounts[blockIndex]++
            }
        }

        val currentGrid = DoubleArray(blockSums.size) { index ->
            if (blockCounts[index] == 0) Double.NaN else blockSums[index] / blockCounts[index]
        }
        val previous = previousGrid?.takeIf { previousWidth == width && previousHeight == height }
        var blockDifference = 0.0
        var comparableBlocks = 0
        if (previous != null) {
            for (index in currentGrid.indices) {
                if (currentGrid[index].isFinite() && previous[index].isFinite()) {
                    blockDifference += abs(currentGrid[index] - previous[index])
                    comparableBlocks++
                }
            }
        }
        previousGrid = currentGrid
        previousWidth = width
        previousHeight = height
        val motionScore = if (comparableBlocks == 0) 0.0 else
            (blockDifference / comparableBlocks / 255.0).coerceIn(0.0, 1.0)

        return PpgSample(
            timestampNanos = timestampNanos,
            red = redSum.toDouble() / pixelCount,
            green = greenSum.toDouble() / pixelCount,
            blue = blueSum.toDouble() / pixelCount,
            coverageFraction = covered.toDouble() / pixelCount,
            saturatedFraction = saturated.toDouble() / pixelCount,
            clippedFraction = clipped.toDouble() / pixelCount,
            motionScore = motionScore,
            torchOn = torchOn,
            greenClippedFraction = greenClipped.toDouble() / pixelCount,
        )
    }

    fun reset() {
        previousGrid = null
        previousWidth = null
        previousHeight = null
    }

    private companion object {
        const val GRID_SIZE = 4
    }
}
