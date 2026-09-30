package id.carda.core.camera

import androidx.camera.core.ImageProxy
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ImageProxyOwnershipTest {
    @Test fun deliveredImageClosesOnceWhenProcessedOrIgnored() {
        val processed = ImageStub()
        var calls = 0
        consumeImageProxy(processed.proxy, onFailure = { throw it }) { calls++ }
        assertEquals(1, calls)
        assertEquals(1, processed.closes)

        val ignored = ImageStub()
        consumeImageProxy(ignored.proxy, onFailure = { throw it }) { /* stale session */ }
        assertEquals(1, ignored.closes)
    }

    @Test fun processingFailureStillClosesOnceAndReachesFailureHandler() {
        val image = ImageStub()
        val failure = IllegalStateException("synthetic extraction failure")
        var received: Throwable? = null

        consumeImageProxy(image.proxy, onFailure = { received = it }) { throw failure }

        assertSame(failure, received)
        assertEquals(1, image.closes)
    }

    private class ImageStub {
        var closes = 0
        val proxy: ImageProxy = Proxy.newProxyInstance(
            ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "close" -> { closes++; Unit }
                else -> throw AssertionError("Unexpected ImageProxy call: ${method.name}")
            }
        } as ImageProxy
    }
}
