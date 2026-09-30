package id.carda.core.ml

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class CpuInsightBackendInstrumentedTest {
    private val spec = InsightModelSpec("artifact-test-only", "a".repeat(64), 16, 512, 128.0, "fixture")

    @Test fun missingSizeAndHashInvalidArtifactsAreRejectedBeforeNativeLoad() = runBlocking<Unit> {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "invalid-model-tests")
        root.mkdirs()
        try {
            withContext(Dispatchers.Default) {
                val absent = File(root, "absent.tflite")
                assertThrows(IllegalArgumentException::class.java) { CpuInsightBackend.open(absent, spec) }
                absent.writeBytes(ByteArray(8))
                assertThrows(IllegalArgumentException::class.java) { CpuInsightBackend.open(absent, spec) }
                absent.writeBytes(ByteArray(16))
                assertThrows(IllegalArgumentException::class.java) { CpuInsightBackend.open(absent, spec) }
                val digest = MessageDigest.getInstance("SHA-256").digest(absent.readBytes())
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                assertThrows(IllegalArgumentException::class.java) {
                    CpuInsightBackend.open(absent, spec.copy(sha256 = digest)) // Hash-valid, invalid FlatBuffer.
                }
            }
        } finally { root.deleteRecursively() }
    }
}
