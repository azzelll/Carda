package id.carda.core.camera

import androidx.camera.core.CameraInfo
import androidx.camera.core.TorchState
import java.util.Locale

/** Reads only CameraX-reported metadata. Actual sensor exposure time and gain are not sampled. */
internal fun CameraInfo.exposureCompensationDescription(): String? = runCatching {
    val state = exposureState
    if (!state.isExposureCompensationSupported) return@runCatching null
    formatExposureCompensation(
        index = state.exposureCompensationIndex,
        minimumIndex = state.exposureCompensationRange.lower,
        maximumIndex = state.exposureCompensationRange.upper,
        stepEv = state.exposureCompensationStep.toDouble(),
    )
}.getOrNull()

internal fun CameraInfo.observedTorchDescription(): String? = runCatching {
    formatObservedTorchState(torchState.value)
}.getOrNull()

internal fun formatObservedTorchState(state: Int?): String? =
    when (state) {
        TorchState.ON -> "torch aktif saat kamera siap"
        TorchState.OFF -> "torch mati saat kamera siap"
        else -> null
    }

internal fun formatExposureCompensation(
    index: Int,
    minimumIndex: Int,
    maximumIndex: Int,
    stepEv: Double,
): String? {
    if (minimumIndex > maximumIndex || index !in minimumIndex..maximumIndex ||
        !stepEv.isFinite() || stepEv <= 0.0) return null
    val offsetEv = index * stepEv
    if (!offsetEv.isFinite()) return null
    return String.format(
        Locale.ROOT,
        "kompensasi eksposur %+.2f EV (indeks %d; rentang %d..%d)",
        offsetEv, index, minimumIndex, maximumIndex,
    )
}
