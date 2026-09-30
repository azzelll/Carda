package id.carda.core.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import id.carda.core.model.QualityIssue
import id.carda.core.model.SupportClassification
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** A transient, downsampled RGBA grid. It must never enter a ViewModel, log, or database. */
data class RgbaRoiFrame(
    val timestampNanos: Long,
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
    val torchOn: Boolean,
)

data class CameraCapability(
    val rearCamera: Boolean,
    val torch: Boolean,
    val width: Int?,
    val height: Int?,
    val classification: SupportClassification,
    val reasons: Set<QualityIssue>,
    /** CameraX exposure compensation at onReady; not the sensor shutter time or gain. */
    val exposureDescription: String? = null,
    /** Observed torch state at onReady, not an assurance it stays on for the session. */
    val flashDescription: String? = null,
)

/** Hardware boundary for one foreground capture. Frames never leave the measurement core. */
interface CameraSession : AutoCloseable {
    fun start(
        owner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: (RgbaRoiFrame) -> Unit,
        onReady: (CameraCapability) -> Unit,
        onFailure: (Throwable) -> Unit,
    )

    fun stop()
}

/** Owns CameraX and torch lifecycle; health calculations belong to core:ppg. */
class CameraSessionController(private val context: Context) : CameraSession {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor: Executor = Executor { mainHandler.post(it) }
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val epoch = CameraSessionEpoch()
    // Resource mutations are confined to main; analyzers read only their own camera reference.
    private var binding: CameraBinding? = null
    private var closed = false

    private data class CameraBinding(
        val token: Long,
        val provider: ProcessCameraProvider,
        val camera: Camera,
        val preview: Preview,
        val analysis: ImageAnalysis,
    )

    override fun start(
        owner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: (RgbaRoiFrame) -> Unit,
        onReady: (CameraCapability) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Start camera on main thread" }
        if (closed) {
            onFailure(IllegalStateException("Camera session closed"))
            return
        }
        val token = epoch.begin()
        if (token == null) {
            onFailure(IllegalStateException("Camera session already active"))
            return
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                if (!epoch.isCurrent(token)) return@addListener
                val currentProvider = future.get()
                if (!currentProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    onReady(CameraCapability(false, false, null, null,
                        SupportClassification.NOT_SUPPORTED, setOf(QualityIssue.NO_REAR_CAMERA)))
                    stop(token)
                    return@addListener
                }

                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysisResolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
                val analysis = ImageAnalysis.Builder()
                    // CameraX records the selected size below; a fallback still faces the same SQI gate.
                    .setResolutionSelector(analysisResolution)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val sessionCamera = AtomicReference<Camera?>()
                analysis.setAnalyzer(analysisExecutor) { image ->
                    consumeImageProxy(image, onFailure = { failure ->
                        mainExecutor.execute { fail(token, failure, onFailure) }
                    }) {
                        if (epoch.isCurrent(token)) {
                            val frame = image.toRgbaRoi(sessionCamera.get()?.cameraInfo?.torchState?.value == TorchState.ON)
                            if (frame != null) onFrame(frame)
                        }
                    }
                }

                // Release only this controller's previous binding, never another session's use cases.
                binding?.let(::release)
                val boundCamera = currentProvider.bindToLifecycle(
                    owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                )
                sessionCamera.set(boundCamera)
                binding = CameraBinding(token, currentProvider, boundCamera, preview, analysis)
                if (!boundCamera.cameraInfo.hasFlashUnit()) {
                    onReady(CameraCapability(true, false, analysis.resolutionInfo?.resolution?.width,
                        analysis.resolutionInfo?.resolution?.height, SupportClassification.NOT_SUPPORTED,
                        setOf(QualityIssue.NO_TORCH),
                        exposureDescription = boundCamera.cameraInfo.exposureCompensationDescription(),
                        flashDescription = "torch tidak tersedia"))
                    stop(token)
                    return@addListener
                }
                val torchRequest = boundCamera.cameraControl.enableTorch(true)
                torchRequest.addListener({
                    try {
                        torchRequest.get()
                        if (epoch.isCurrent(token)) {
                            onReady(CameraCapability(true, true, analysis.resolutionInfo?.resolution?.width,
                                analysis.resolutionInfo?.resolution?.height,
                                SupportClassification.RESTRICTED, emptySet(),
                                exposureDescription = boundCamera.cameraInfo.exposureCompensationDescription(),
                                flashDescription = boundCamera.cameraInfo.observedTorchDescription()))
                        }
                    } catch (failure: Throwable) {
                        fail(token, failure, onFailure)
                    }
                }, mainExecutor)
            } catch (failure: Throwable) {
                fail(token, failure, onFailure)
            }
        }, mainExecutor)
    }

    override fun stop() {
        val token = epoch.stopCurrent() ?: return
        releaseOnMain(token)
    }

    private fun stop(token: Long) {
        if (epoch.stop(token)) releaseOnMain(token)
    }

    private fun fail(token: Long, failure: Throwable, onFailure: (Throwable) -> Unit) {
        if (!epoch.stop(token)) return
        releaseOnMain(token)
        onFailure(failure)
    }

    private fun releaseOnMain(token: Long) {
        val action: () -> Unit = {
            // A queued stop from an old session cannot unbind a newer retry.
            binding?.takeIf { it.token == token }?.let(::release)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun release(resources: CameraBinding) {
        resources.analysis.clearAnalyzer()
        runCatching { resources.camera.cameraControl.enableTorch(false) }
        resources.provider.unbind(resources.preview, resources.analysis)
        if (binding === resources) binding = null
    }

    override fun close() {
        closed = true
        stop()
        analysisExecutor.shutdown()
    }
}

private fun ImageProxy.toRgbaRoi(torchOn: Boolean): RgbaRoiFrame? {
    val plane = planes.firstOrNull() ?: return null
    val roi = downsampleCenterRgba(
        plane.buffer.duplicate(), width, height, plane.rowStride, plane.pixelStride,
    ) ?: return null
    return RgbaRoiFrame(imageInfo.timestamp, roi.width, roi.height, roi.pixels, torchOn)
}

/** The analyzer takes ownership of each delivered ImageProxy and releases it once. */
internal inline fun consumeImageProxy(
    image: ImageProxy,
    onFailure: (Throwable) -> Unit,
    process: (ImageProxy) -> Unit,
) {
    try {
        process(image)
    } catch (failure: Throwable) {
        onFailure(failure)
    } finally {
        image.close()
    }
}
