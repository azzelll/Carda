package id.carda.core.measurement

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.carda.core.camera.CameraCapability
import id.carda.core.camera.CameraSession
import id.carda.core.camera.CameraSessionController
import id.carda.core.camera.RgbaRoiFrame
import id.carda.core.camera.DeviceHardwareSnapshot
import id.carda.core.camera.assessNominalDevice
import id.carda.core.camera.readDeviceHardware
import id.carda.core.model.DeviceProfile
import id.carda.core.model.NominalDeviceAssessment
import id.carda.core.model.MeasurementState
import id.carda.core.model.MeasurementActivity
import id.carda.core.model.CaptureDuration
import id.carda.core.model.MetricKind
import id.carda.core.model.MetricResult
import id.carda.core.model.MetricUnavailableReason
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.QualityIssue
import id.carda.core.model.QualityReport
import id.carda.core.model.SupportClassification
import id.carda.core.ppg.PpgPipeline
import id.carda.core.ppg.QualityThresholds
import id.carda.core.ppg.PpgSample
import id.carda.core.ppg.RgbaRoiExtractor
import id.carda.core.ppg.ResearchSignalDiagnostics
import id.carda.core.ppg.evaluateResearchSignalDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI-safe capture state: no frame pixels or timestamped sample stream can reach a ViewModel. */
data class CaptureState(
    val state: MeasurementState = MeasurementState.PREPARING,
    val progressSeconds: Int = 0,
    val targetSeconds: Int = 30,
    /** Transient, normalized drawing points from this session's actual camera ROI. Never persist. */
    val tracePoints: List<Float> = emptyList(),
    val quality: QualityReport? = null,
    val heartRateBpm: Double? = null,
    val rmssdMs: Double? = null,
    val sdnnMs: Double? = null,
    val capability: CameraCapability? = null,
    val nominalDevice: NominalDeviceAssessment? = null,
    val deviceProfile: DeviceProfile? = null,
    val summary: MeasurementSummary? = null,
    /** Present only in a debuggable research build; never part of the persisted summary. */
    val researchDiagnostics: ResearchSignalDiagnostics? = null,
    val sessionStopped: Boolean = false,
    val message: String = "Siapkan jari di kamera belakang dan lampu kilat.",
)

/** One disposable capture session. It owns all transient signal samples and the CameraX lifecycle. */
class MeasurementEngine(
    context: Context,
    private val activity: MeasurementActivity = MeasurementActivity.UNSPECIFIED,
    private val duration: CaptureDuration = CaptureDuration.THIRTY_SECONDS,
    private val researchMode: Boolean = false,
    private val camera: CameraSession = CameraSessionController(context.applicationContext),
) : AutoCloseable {
    private val appVersion = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"
    private val thresholds = QualityThresholds()
    private val pipeline = PpgPipeline(thresholds)
    private val extractor = RgbaRoiExtractor(thresholds.extraction)
    private val hardware = runCatching { readDeviceHardware(context.applicationContext) }.getOrElse {
        DeviceHardwareSnapshot(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList(),
            0, 0, 0)
    }
    private val lock = Any()
    private val captureBuffer = CaptureSampleBuffer(duration)
    private val samples get() = captureBuffer.values
    private val mutableState = MutableStateFlow(CaptureState(targetSeconds = duration.seconds))
    val state: StateFlow<CaptureState> = mutableState.asStateFlow()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastEvaluationNanos: Long? = null
    private var capability: CameraCapability? = null
    private var owner: LifecycleOwner? = null
    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private val timeout = Runnable {
        synchronized(lock) {
            if (running) {
                stop()
                mutableState.value = mutableState.value.copy(
                    state = MeasurementState.RETRY,
                    heartRateBpm = null,
                    rmssdMs = null,
                    sdnnMs = null,
                    summary = null,
                    researchDiagnostics = null,
                    sessionStopped = true,
                    tracePoints = emptyList(),
                    message = "Waktu pengukuran habis. Coba lagi setelah menyiapkan posisi jari.",
                )
            }
        }
    }

    fun start(owner: LifecycleOwner, preview: PreviewView) {
        synchronized(lock) {
            if (running) return
            running = true
            samples.clear()
            extractor.reset()
            lastEvaluationNanos = null
            capability = null
            mutableState.value = CaptureState(targetSeconds = duration.seconds)
        }
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                synchronized(lock) {
                    // A delayed callback from an old observer cannot stop a newer retry.
                    if (lifecycleObserver !== this || mutableState.value.state == MeasurementState.COMPLETE) return
                    stop()
                    mutableState.value = mutableState.value.copy(
                        state = MeasurementState.RETRY,
                        heartRateBpm = null,
                        rmssdMs = null,
                        sdnnMs = null,
                        summary = null,
                        researchDiagnostics = null,
                        sessionStopped = true,
                        tracePoints = emptyList(),
                        message = "Pengukuran dihentikan saat aplikasi tidak aktif. Mulai sesi baru.",
                    )
                }
            }
        }
        this.owner = owner
        lifecycleObserver = observer
        owner.lifecycle.addObserver(observer)
        mainHandler.postDelayed(timeout, SESSION_TIMEOUT_MILLIS)
        camera.start(owner, preview, ::onFrame, ::onReady) { failure ->
            stop()
            mutableState.value = mutableState.value.copy(
                state = MeasurementState.ERROR,
                progressSeconds = 0,
                tracePoints = emptyList(),
                quality = null,
                heartRateBpm = null,
                rmssdMs = null,
                sdnnMs = null,
                summary = null,
                researchDiagnostics = null,
                sessionStopped = true,
                message = "Kamera gagal dibuka: ${failure.javaClass.simpleName}. Coba ulangi.",
            )
        }
    }

    private fun onReady(ready: CameraCapability) {
        val unsupported = ready.classification == SupportClassification.NOT_SUPPORTED
        synchronized(lock) {
            if (!running) return
            capability = ready
            if (unsupported) {
                running = false
                mutableState.value = mutableState.value.copy(
                    state = MeasurementState.UNSUPPORTED_DEVICE,
                    sessionStopped = true,
                    capability = ready,
                    nominalDevice = assessNominalDevice(hardware, ready.width, ready.height, null),
                    deviceProfile = makeDeviceProfile(ready, null, null, SupportClassification.NOT_SUPPORTED),
                    message = if (!ready.rearCamera) "Kamera belakang tidak tersedia."
                        else "Lampu kilat kamera belakang tidak tersedia.",
                )
            } else {
                mutableState.value = mutableState.value.copy(
                    capability = ready,
                    nominalDevice = assessNominalDevice(hardware, ready.width, ready.height, null),
                )
            }
        }
        if (unsupported) stop()
    }

    private fun onFrame(frame: RgbaRoiFrame) {
        synchronized(lock) {
            if (!running) return
            val sample = extractor.extract(
                timestampNanos = frame.timestampNanos,
                rgba = frame.rgba,
                width = frame.width,
                height = frame.height,
                rowStride = frame.width * 4,
                torchOn = frame.torchOn,
            )
            if (!captureBuffer.offer(sample)) {
                val observedFps = observedFramesPerSecond(samples)
                val quality = QualityReport(false, 0.0, setOf(QualityIssue.SAMPLE_BUDGET_EXCEEDED),
                    samples.size, 0L, thresholds.version)
                val profile = makeDeviceProfile(capability, quality, observedFps, SupportClassification.RESTRICTED)
                stop()
                mutableState.value = mutableState.value.copy(
                    state = MeasurementState.RETRY, sessionStopped = true, progressSeconds = 0,
                    tracePoints = emptyList(), quality = quality, deviceProfile = profile,
                    heartRateBpm = null, rmssdMs = null, sdnnMs = null, summary = null,
                    researchDiagnostics = null,
                    message = "Aliran kamera melampaui batas sesi ini. Sesi dihentikan tanpa hasil. Coba ulangi.",
                )
                return
            }
            val elapsedCaptureNanos = if (samples.size > 1)
                sample.timestampNanos - samples.first().timestampNanos else 0L
            if (!shouldEvaluateCapture(sample.timestampNanos, lastEvaluationNanos,
                    elapsedCaptureNanos, duration.seconds)) return
            lastEvaluationNanos = sample.timestampNanos

            val durationSeconds = (elapsedCaptureNanos / 1_000_000_000L).toInt()
            val trace = transientTrace(samples)
            if (durationSeconds < MINIMUM_PROBE_SECONDS) {
                mutableState.value = mutableState.value.copy(
                    state = MeasurementState.PREPARING,
                    // Probe time is credited only after the full window passes SQI.
                    progressSeconds = 0,
                    tracePoints = trace,
                    quality = null,
                    nominalDevice = assessNominalDevice(hardware, capability?.width,
                        capability?.height, null),
                    deviceProfile = null,
                    heartRateBpm = null,
                    rmssdMs = null,
                    sdnnMs = null,
                    summary = null,
                    researchDiagnostics = null,
                    message = "Tahan jari dengan ringan dan tetap diam. Memeriksa kualitas sinyal.",
                )
                return
            }

            val quality = pipeline.evaluateQuality(samples.toList())
            val observedFps = observedFramesPerSecond(samples)
            val nominal = assessNominalDevice(hardware, capability?.width, capability?.height, observedFps)
            if (!quality.passed) {
                val issue = quality.issues.firstOrNull()
                val state = when (issue) {
                    QualityIssue.TOO_DARK, QualityIssue.TORCH_OFF -> MeasurementState.TOO_DARK
                    QualityIssue.EXCESSIVE_MOTION, QualityIssue.UNSTABLE_FRAME_CADENCE ->
                        MeasurementState.MOTION_DETECTED
                    QualityIssue.POOR_CONTACT, QualityIssue.SATURATED, QualityIssue.CLIPPED ->
                        MeasurementState.POOR_CONTACT
                    else -> MeasurementState.RETRY
                }
                samples.clear()
                extractor.reset()
                mutableState.value = mutableState.value.copy(
                    state = state,
                    progressSeconds = 0,
                    tracePoints = emptyList(),
                    quality = quality,
                    nominalDevice = nominal,
                    deviceProfile = makeDeviceProfile(capability, quality, observedFps,
                        SupportClassification.RESTRICTED),
                    heartRateBpm = null,
                    rmssdMs = null,
                    sdnnMs = null,
                    researchDiagnostics = null,
                    message = qualityInstruction(issue),
                )
                return
            }

            if (durationSeconds < duration.seconds) {
                val provisional = pipeline.analyze(samples.toList())
                mutableState.value = mutableState.value.copy(
                    state = MeasurementState.MEASURING,
                    progressSeconds = durationSeconds.coerceAtMost(duration.seconds),
                    tracePoints = trace,
                    quality = quality,
                    nominalDevice = nominal,
                    heartRateBpm = provisionalHeartRate(provisional),
                    message = "Sinyal terbaca. Pertahankan posisi jari hingga selesai.",
                )
                return
            }

            val finalWindow = samples.toList()
            val analysis = pipeline.analyze(finalWindow)
            val heartRate = (analysis.heartRateBpm as? MetricResult.Available)?.value
            if (!analysis.quality.passed || heartRate == null) {
                samples.clear()
                extractor.reset()
                mutableState.value = mutableState.value.copy(
                    state = MeasurementState.RETRY,
                    progressSeconds = 0,
                    tracePoints = emptyList(),
                    quality = analysis.quality,
                    heartRateBpm = null,
                    researchDiagnostics = null,
                    message = "Sinyal belum cukup untuk menghitung denyut. Coba ukur ulang.",
                )
                return
            }
            val profile = makeDeviceProfile(capability, analysis.quality, observedFps,
                if (nominal.allPassed) SupportClassification.COMPATIBLE else SupportClassification.RESTRICTED)
            val unavailable = MetricResult.Unavailable(MetricUnavailableReason.METHOD_NOT_VALIDATED)
            val researchDiagnostics = if (researchMode) {
                evaluateResearchSignalDiagnostics(finalWindow, analysis.quality, pipeline)
            } else null
            val summary = MeasurementSummary(
                measuredAtEpochMillis = System.currentTimeMillis(),
                validDurationMillis = analysis.quality.validDurationMillis,
                quality = analysis.quality,
                deviceProfile = profile,
                metrics = mapOf(
                    MetricKind.HEART_RATE_BPM to MetricResult.Available(heartRate),
                    MetricKind.PRV_RMSSD_MS to analysis.rmssdMs,
                    MetricKind.PRV_SDNN_MS to analysis.sdnnMs,
                    MetricKind.ESTIMATED_RESPIRATORY_RATE_BPM to unavailable,
                    MetricKind.ESTIMATED_SPO2_PERCENT to unavailable,
                    MetricKind.ESTIMATED_SYSTOLIC_MMHG to unavailable,
                    MetricKind.ESTIMATED_DIASTOLIC_MMHG to unavailable,
                ),
                activity = activity,
            )
            running = false
            mainHandler.removeCallbacks(timeout)
            mutableState.value = mutableState.value.copy(
                state = MeasurementState.COMPLETE,
                sessionStopped = true,
                progressSeconds = duration.seconds,
                tracePoints = trace,
                quality = analysis.quality,
                nominalDevice = nominal,
                heartRateBpm = heartRate,
                rmssdMs = (analysis.rmssdMs as? MetricResult.Available)?.value,
                sdnnMs = (analysis.sdnnMs as? MetricResult.Available)?.value,
                deviceProfile = profile,
                summary = summary,
                researchDiagnostics = researchDiagnostics,
                message = "Pengukuran selesai. Ini informasi wellness, bukan diagnosis.",
            )
            samples.clear()
            camera.stop()
        }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            mainHandler.removeCallbacks(timeout)
            val previousOwner = owner
            val previousObserver = lifecycleObserver
            owner = null
            lifecycleObserver = null
            if (previousOwner != null && previousObserver != null) {
                val remove: () -> Unit = { previousOwner.lifecycle.removeObserver(previousObserver) }
                if (Looper.myLooper() == Looper.getMainLooper()) remove() else mainHandler.post(remove)
            }
            samples.clear()
            extractor.reset()
            camera.stop()
        }
    }

    override fun close() {
        stop()
        camera.close()
    }

    private fun makeDeviceProfile(
        ready: CameraCapability?,
        quality: QualityReport?,
        observedFps: Double?,
        classification: SupportClassification,
    ): DeviceProfile = DeviceProfile(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidApiLevel = Build.VERSION.SDK_INT,
        abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        appVersion = appVersion,
        pipelineVersion = quality?.pipelineVersion ?: thresholds.version,
        hasRearCamera = ready?.rearCamera == true,
        hasTorch = ready?.torch == true,
        analysisWidth = ready?.width,
        analysisHeight = ready?.height,
        observedFramesPerSecond = observedFps,
        exposureDescription = ready?.exposureDescription,
        flashDescription = ready?.flashDescription,
        rejectionReasons = ready?.reasons.orEmpty() + quality?.issues.orEmpty(),
        lastQualityScore = quality?.score,
        supportClassification = classification,
    )

    private companion object {
        const val MINIMUM_PROBE_SECONDS = 10
        const val SESSION_TIMEOUT_MILLIS = 90_000L
    }
}

private fun observedFramesPerSecond(samples: Collection<PpgSample>): Double? {
    if (samples.size < 2) return null
    val duration = samples.last().timestampNanos - samples.first().timestampNanos
    return if (duration <= 0) null else (samples.size - 1) * 1_000_000_000.0 / duration
}

private fun transientTrace(samples: Collection<PpgSample>): List<Float> {
    val values = samples.toList().takeLast(150).map { it.red }
    if (values.size < 2) return emptyList()
    val minimum = values.min()
    val range = values.max() - minimum
    if (range <= 0.0) return List(values.size) { 0.5f }
    return values.map { ((it - minimum) / range).toFloat() }
}

private fun qualityInstruction(issue: QualityIssue?): String = when (issue) {
    QualityIssue.TOO_DARK, QualityIssue.TORCH_OFF -> "Sinyal terlalu gelap. Pastikan lampu kilat menyala dan jari menutup kamera."
    QualityIssue.SATURATED, QualityIssue.CLIPPED -> "Sinyal terlalu terang atau terpotong. Atur posisi jari dan coba lagi."
    QualityIssue.POOR_CONTACT -> "Jari belum menutup kamera dengan stabil. Atur posisi dan coba lagi."
    QualityIssue.EXCESSIVE_MOTION -> "Terlalu banyak gerakan. Letakkan ponsel dan tahan jari tetap diam."
    QualityIssue.UNSTABLE_FRAME_CADENCE -> "Kamera tidak menghasilkan frame yang stabil. Hentikan sesi lalu coba lagi."
    else -> "Kualitas sinyal belum cukup. Atur posisi jari dan coba lagi."
}

/** CameraX and raw frame types stay inside core:measurement/core:camera. */
@Composable
fun MeasurementPreview(engine: MeasurementEngine, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    AndroidView(
        factory = { context -> PreviewView(context).also { engine.start(owner, it) } },
        modifier = modifier,
    )
    DisposableEffect(engine, owner) { onDispose { engine.stop() } }
}
