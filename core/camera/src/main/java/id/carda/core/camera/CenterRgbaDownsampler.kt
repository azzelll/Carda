package id.carda.core.camera

import java.nio.ByteBuffer

/** Frame pixels are transient and confined to the camera analysis executor. */
internal class CenterRgbaRoi(val width: Int, val height: Int, val pixels: ByteArray)

/**
 * Selects a bounded center region from an RGBA_8888 plane, respecting row/pixel strides.
 * Absolute reads leave CameraX's buffer position untouched. Invalid planes produce no frame.
 */
internal fun downsampleCenterRgba(
    input: ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
): CenterRgbaRoi? {
    if (width <= 0 || height <= 0 || pixelStride < 4 || rowStride < 0 ||
        width.toLong() * pixelStride > rowStride.toLong()
    ) return null

    val outputWidth = minOf(width / 2, 64).coerceAtLeast(1)
    val outputHeight = minOf(height / 2, 64).coerceAtLeast(1)
    val output = ByteArray(outputWidth * outputHeight * 4)
    val left = width / 4
    val top = height / 4
    for (y in 0 until outputHeight) {
        val sourceY = top + y * (height / 2).coerceAtLeast(1) / outputHeight
        for (x in 0 until outputWidth) {
            val sourceX = left + x * (width / 2).coerceAtLeast(1) / outputWidth
            val source = sourceY.toLong() * rowStride + sourceX.toLong() * pixelStride
            if (source + 3 >= input.limit().toLong()) return null
            val target = (y * outputWidth + x) * 4
            for (channel in 0..3) output[target + channel] = input.get(source.toInt() + channel)
        }
    }
    return CenterRgbaRoi(outputWidth, outputHeight, output)
}
