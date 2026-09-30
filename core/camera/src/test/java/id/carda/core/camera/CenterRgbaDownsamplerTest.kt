package id.carda.core.camera

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CenterRgbaDownsamplerTest {
    @Test fun centerPixelsRespectRowPaddingAndLeaveBufferPositionUntouched() {
        val width = 8
        val height = 8
        val rowStride = width * 4 + 8
        val input = ByteBuffer.allocate(rowStride * height)
        for (y in 0 until height) for (x in 0 until width) {
            val offset = y * rowStride + x * 4
            input.put(offset, (y * width + x).toByte())
            input.put(offset + 1, 100.toByte())
            input.put(offset + 2, 50.toByte())
            input.put(offset + 3, 255.toByte())
        }
        input.position(5)

        val roi = downsampleCenterRgba(input, width, height, rowStride, 4)

        assertNotNull(roi)
        assertEquals(4, roi!!.width)
        assertEquals(4, roi.height)
        for (y in 0 until roi.height) for (x in 0 until roi.width) {
            val offset = (y * roi.width + x) * 4
            assertEquals((y + 2) * width + (x + 2), roi.pixels[offset].toInt() and 0xff)
            assertEquals(100, roi.pixels[offset + 1].toInt() and 0xff)
            assertEquals(50, roi.pixels[offset + 2].toInt() and 0xff)
            assertEquals(255, roi.pixels[offset + 3].toInt() and 0xff)
        }
        assertEquals(5, input.position())
    }

    @Test fun truncatedOrImpossiblePlaneIsRejected() {
        assertNull(downsampleCenterRgba(ByteBuffer.allocate(10), 8, 8, 40, 4))
        assertNull(downsampleCenterRgba(ByteBuffer.allocate(100), 8, 8, 31, 4))
        assertNull(downsampleCenterRgba(ByteBuffer.allocate(100), Int.MAX_VALUE, 8,
            Int.MAX_VALUE, 4))
    }
}
