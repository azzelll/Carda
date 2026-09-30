package id.carda.core.ml

import androidx.test.platform.app.InstrumentationRegistry
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Explicit opt-in synthetic research test. Skipping is not successful model evidence. */
class ResearchModelCpuInstrumentedTest {
    @Test fun externalSyntheticResearchModelRunsCpuWithHostParityAndNoConfidence() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        assumeTrue("Requires external -PcardaResearchAssets", assets.list("")!!.contains("manifest.json"))
        val manifest = JSONObject(assets.open("manifest.json").bufferedReader().use { it.readText() })
        assertTrue(manifest.getBoolean("synthetic_input_only"))
        val tolerance = manifest.getDouble("absolute_tolerance")
        assertEquals(1e-5, tolerance, 0.0)
        val model = File(instrumentation.targetContext.cacheDir, "research-model.tflite")
        try {
            assets.open("research-model.tflite").use { input -> model.outputStream().use { input.copyTo(it) } }
            val spec = InsightModelSpec("cardiogan-research-float32", manifest.getString("model_sha256"),
                manifest.getLong("model_bytes"), 512, 128.0, "synthetic-no-camera-preprocessing")
            withContext(Dispatchers.Default) {
                fun pss() = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss
                val memorySamples = mutableListOf(pss())
                val loadStarted = SystemClock.elapsedRealtimeNanos()
                CpuInsightBackend.open(model, spec).use { backend ->
                    val loadMillis = (SystemClock.elapsedRealtimeNanos() - loadStarted) / 1_000_000.0
                    memorySamples += pss()
                    val invocationMillis = mutableListOf<Double>()
                    var maximumError = 0.0
                    val count = manifest.getInt("fixtures")
                    assertEquals(4, count)
                    for (index in 0 until count) {
                        fun floats(name: String): FloatArray {
                            val bytes = assets.open(name).use { it.readBytes() }
                            assertEquals(512 * 4, bytes.size)
                            return FloatArray(512).also {
                                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it)
                            }
                        }
                        val expected = floats("output-$index.bin")
                        val input = floats("input-$index.bin")
                        val started = SystemClock.elapsedRealtimeNanos()
                        val predicted = backend.infer(input)
                        invocationMillis += (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
                        memorySamples += pss()
                        assertNull(predicted.calibratedConfidence)
                        val output = predicted.copyWaveform()
                        assertEquals(512, output.size)
                        assertTrue(output.all { it.isFinite() })
                        maximumError = maxOf(maximumError, output.indices.maxOf { abs(output[it] - expected[it]).toDouble() })
                    }
                    // A failure message contains aggregates only, never waveform samples.
                    assertTrue("Synthetic host/Android max absolute difference: $maximumError", maximumError <= tolerance)
                    val report = JSONObject().put("synthetic_input_only", true).put("model_sha256", spec.sha256)
                        .put("runtime", "bundled LiteRT 2.2.0 CPU; one thread; XNNPACK disabled")
                        .put("android_api", Build.VERSION.SDK_INT).put("manufacturer", Build.MANUFACTURER)
                        .put("model", Build.MODEL).put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                        .put("load_millis", loadMillis).put("invocation_millis", JSONArray(invocationMillis))
                        .put("sampled_total_pss_kib", JSONArray(memorySamples))
                        .put("max_observed_pss_kib", memorySamples.max())
                        .put("max_absolute_difference", maximumError).put("absolute_tolerance", tolerance)
                        .put("confidence_available", false)
                        .put("limitations", "Emulator synthetic parity only; sampled PSS is not peak RAM; no camera/physiology validation")
                    File(instrumentation.targetContext.cacheDir, "research-cpu-report.json").writeText(report.toString(2))
                    // Technical aggregates from synthetic fixtures only; no input/output trace is logged.
                    Log.i("CardaResearchCpu", report.toString())
                    backend.close()
                    assertThrows(IllegalStateException::class.java) { backend.infer(FloatArray(512)) }
                }
            }
        } finally { model.delete() }
    }
}
