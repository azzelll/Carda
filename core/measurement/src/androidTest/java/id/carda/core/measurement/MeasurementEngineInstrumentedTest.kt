package id.carda.core.measurement

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.carda.core.camera.CameraCapability
import id.carda.core.camera.CameraSession
import id.carda.core.camera.RgbaRoiFrame
import id.carda.core.model.MeasurementState
import id.carda.core.model.CaptureDuration
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.QualityIssue
import id.carda.core.model.SupportClassification
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic camera frames exercise the real engine and PPG path, not phone physiology. */
@RunWith(AndroidJUnit4::class)
class MeasurementEngineInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun acceptedSyntheticFramesReachQualityGatedSummaryAndStopCamera() {
        val camera = SyntheticCamera()
        val engine = MeasurementEngine(context, camera = camera)
        val owner = TestOwner()
        try {
            instrumentation.runOnMainSync {
                owner.registry.currentState = Lifecycle.State.RESUMED
                engine.start(owner, PreviewView(context))
            }
            for (index in 0..300) camera.emit(index, redPulse = true)
            assertEquals(MeasurementState.MEASURING, engine.state.value.state)
            assertEquals(10, engine.state.value.progressSeconds)
            for (index in 301..900) camera.emit(index, redPulse = true)

            val state = engine.state.value
            assertEquals(MeasurementState.COMPLETE, state.state)
            assertTrue(state.sessionStopped)
            assertTrue(state.quality!!.passed)
            val summary = state.summary
            assertNotNull(summary)
            assertEquals("ppg-0.3", summary!!.quality.pipelineVersion)
            val heartRate = summary.metrics[MetricKind.HEART_RATE_BPM] as MetricResult.Available<Double>
            assertEquals(72.0, heartRate.value, 3.0)
            assertTrue(summary.metrics[MetricKind.PRV_RMSSD_MS] is MetricResult.Available<*>)
            assertTrue(summary.metrics[MetricKind.PRV_SDNN_MS] is MetricResult.Available<*>)
            assertEquals(1, camera.stops)
        } finally {
            instrumentation.runOnMainSync { engine.close() }
        }
    }

    @Test fun sixtySecondCaptureDoesNotFinishAtThirtySeconds() {
        val camera = SyntheticCamera()
        val engine = MeasurementEngine(context, duration = CaptureDuration.SIXTY_SECONDS,
            camera = camera)
        val owner = TestOwner()
        try {
            instrumentation.runOnMainSync {
                owner.registry.currentState = Lifecycle.State.RESUMED
                engine.start(owner, PreviewView(context))
            }
            for (index in 0..900) camera.emit(index, redPulse = true)

            val midpoint = engine.state.value
            assertEquals(MeasurementState.MEASURING, midpoint.state)
            assertEquals(60, midpoint.targetSeconds)
            assertEquals(30, midpoint.progressSeconds)
            assertNull(midpoint.summary)
            assertEquals(0, camera.stops)

            for (index in 901..1800) camera.emit(index, redPulse = true)
            val finished = engine.state.value
            assertEquals(MeasurementState.COMPLETE, finished.state)
            assertEquals(60, finished.progressSeconds)
            assertNotNull(finished.summary)
            assertTrue(finished.summary!!.metrics[MetricKind.HEART_RATE_BPM] is MetricResult.Available<*>)
            assertEquals(1, camera.stops)
        } finally {
            instrumentation.runOnMainSync { engine.close() }
        }
    }

    @Test fun rejectedDarkProbeClearsProgressAndCannotReuseItsSamples() {
        val camera = SyntheticCamera()
        val engine = MeasurementEngine(context, camera = camera)
        val owner = TestOwner()
        try {
            instrumentation.runOnMainSync {
                owner.registry.currentState = Lifecycle.State.RESUMED
                engine.start(owner, PreviewView(context))
            }
            for (index in 0..100) camera.emit(index, redPulse = false)
            assertEquals(MeasurementState.PREPARING, engine.state.value.state)
            assertEquals(0, engine.state.value.progressSeconds)
            assertNull(engine.state.value.heartRateBpm)
            for (index in 101..300) camera.emit(index, redPulse = false)

            val rejected = engine.state.value
            assertEquals(MeasurementState.TOO_DARK, rejected.state)
            assertEquals(0, rejected.progressSeconds)
            assertNull(rejected.heartRateBpm)
            assertNull(rejected.summary)
            assertTrue(rejected.tracePoints.isEmpty())

            for (index in 301..400) camera.emit(index, redPulse = true)
            val freshProbe = engine.state.value
            assertEquals(MeasurementState.PREPARING, freshProbe.state)
            assertNull(freshProbe.quality)
            assertNull(freshProbe.deviceProfile)
            assertNull(freshProbe.heartRateBpm)
            assertEquals(0, freshProbe.progressSeconds)

            for (index in 401..1201) camera.emit(index, redPulse = true)
            val accepted = engine.state.value
            assertEquals(MeasurementState.COMPLETE, accepted.state)
            assertTrue(accepted.quality!!.passed)
            assertEquals(1, camera.stops)
            assertNotNull(accepted.summary)
        } finally {
            instrumentation.runOnMainSync { engine.close() }
        }
    }

    @Test fun backgroundingClearsTransientSignalAndStopsTheCameraSession() {
        val camera = SyntheticCamera()
        val engine = MeasurementEngine(context, camera = camera)
        val owner = TestOwner()
        try {
            instrumentation.runOnMainSync {
                owner.registry.currentState = Lifecycle.State.RESUMED
                engine.start(owner, PreviewView(context))
            }
            for (index in 0..100) camera.emit(index, redPulse = true)
            assertTrue(engine.state.value.tracePoints.isNotEmpty())

            instrumentation.runOnMainSync { owner.registry.currentState = Lifecycle.State.CREATED }

            val stopped = engine.state.value
            assertEquals(MeasurementState.RETRY, stopped.state)
            assertTrue(stopped.sessionStopped)
            assertTrue(stopped.tracePoints.isEmpty())
            assertNull(stopped.heartRateBpm)
            assertNull(stopped.summary)
            assertEquals(1, camera.stops)
        } finally {
            instrumentation.runOnMainSync { engine.close() }
        }
    }

    @Test fun cameraFailureAfterTemporaryHeartRateClearsEveryTransientOutput() {
        val camera = SyntheticCamera()
        val engine = MeasurementEngine(context, camera = camera)
        val owner = TestOwner()
        try {
            instrumentation.runOnMainSync {
                owner.registry.currentState = Lifecycle.State.RESUMED
                engine.start(owner, PreviewView(context))
            }
            for (index in 0..360) camera.emit(index, redPulse = true)
            assertEquals(MeasurementState.MEASURING, engine.state.value.state)
            assertNotNull(engine.state.value.heartRateBpm)

            instrumentation.runOnMainSync {
                camera.fail(IllegalStateException("synthetic camera failure"))
            }

            val failed = engine.state.value
            assertEquals(MeasurementState.ERROR, failed.state)
            assertTrue(failed.sessionStopped)
            assertNull(failed.heartRateBpm)
            assertNull(failed.rmssdMs)
            assertNull(failed.sdnnMs)
            assertNull(failed.summary)
            assertNull(failed.researchDiagnostics)
            assertTrue(failed.tracePoints.isEmpty())
            assertEquals(1, camera.stops)
        } finally {
            instrumentation.runOnMainSync { engine.close() }
        }
    }

    @Test fun torchLossAfterTemporaryHeartRateClearsItAndRequiresFreshWindow() {
        val camera = SyntheticCamera()
        val engine = MeasurementEngine(context, camera = camera)
        val owner = TestOwner()
        try {
            instrumentation.runOnMainSync {
                owner.registry.currentState = Lifecycle.State.RESUMED
                engine.start(owner, PreviewView(context))
            }
            for (index in 0..360) camera.emit(index, redPulse = true)
            assertNotNull(engine.state.value.heartRateBpm)

            for (index in 361..390) camera.emit(index, redPulse = true, torchOn = false)
            val rejected = engine.state.value
            assertEquals(MeasurementState.TOO_DARK, rejected.state)
            assertEquals(0, rejected.progressSeconds)
            assertTrue(QualityIssue.TORCH_OFF in rejected.quality!!.issues)
            assertNull(rejected.heartRateBpm)
            assertNull(rejected.summary)
            assertEquals(0, camera.stops)

            for (index in 391..1291) camera.emit(index, redPulse = true)
            val recovered = engine.state.value
            assertEquals(MeasurementState.COMPLETE, recovered.state)
            assertEquals(30, recovered.progressSeconds)
            assertNotNull(recovered.summary)
            assertEquals(1, camera.stops)
        } finally {
            instrumentation.runOnMainSync { engine.close() }
        }
    }

    @Test fun analysisThreadSampleBudgetStopDoesNotCrashLifecycleRegistry() {
        val camera = SyntheticCamera()
        val engine = MeasurementEngine(context, camera = camera)
        val owner = TestOwner()
        try {
            instrumentation.runOnMainSync {
                owner.registry.currentState = Lifecycle.State.RESUMED
                engine.start(owner, PreviewView(context))
            }
            // 200 fps reaches the bounded sample budget before 30 valid seconds.
            for (index in 0..3602) camera.emit(index, redPulse = true, fps = 200.0)

            val stopped = engine.state.value
            assertEquals(MeasurementState.RETRY, stopped.state)
            assertTrue(stopped.sessionStopped)
            assertTrue(QualityIssue.SAMPLE_BUDGET_EXCEEDED in stopped.quality!!.issues)
            assertNull(stopped.heartRateBpm)
            assertNull(stopped.summary)
            assertEquals(1, camera.stops)
        } finally {
            instrumentation.runOnMainSync { engine.close() }
        }
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }

    private class SyntheticCamera : CameraSession {
        private var onFrame: ((RgbaRoiFrame) -> Unit)? = null
        private var onFailure: ((Throwable) -> Unit)? = null
        var stops = 0
            private set

        override fun start(
            owner: LifecycleOwner,
            previewView: PreviewView,
            onFrame: (RgbaRoiFrame) -> Unit,
            onReady: (CameraCapability) -> Unit,
            onFailure: (Throwable) -> Unit,
        ) {
            this.onFrame = onFrame
            this.onFailure = onFailure
            onReady(CameraCapability(true, true, 1280, 720,
                SupportClassification.RESTRICTED, emptySet()))
        }

        fun fail(error: Throwable) { onFailure?.invoke(error) }

        fun emit(index: Int, redPulse: Boolean, fps: Double = 30.0,
                 torchOn: Boolean = true) {
            val timestamp = (index * 1_000_000_000.0 / fps).toLong()
            val seconds = timestamp / 1_000_000_000.0
            val red = if (redPulse) (120.0 + 12.0 * sin(2.0 * PI * 1.2 * seconds)).roundToInt() else 10
            val pixels = ByteArray(8 * 8 * 4)
            for (pixel in 0 until 8 * 8) {
                val offset = pixel * 4
                pixels[offset] = red.toByte()
                pixels[offset + 1] = if (redPulse) 70 else 5
                pixels[offset + 2] = if (redPulse) 40 else 3
                pixels[offset + 3] = 255.toByte()
            }
            onFrame?.invoke(RgbaRoiFrame(timestamp, 8, 8, pixels, torchOn))
        }

        override fun stop() { stops++; onFrame = null; onFailure = null }
        override fun close() { stop() }
    }
}
