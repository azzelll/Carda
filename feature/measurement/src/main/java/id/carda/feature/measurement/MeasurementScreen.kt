package id.carda.feature.measurement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.carda.core.measurement.CaptureState
import id.carda.core.measurement.MeasurementEngine
import id.carda.core.measurement.MeasurementPreview
import id.carda.core.model.MeasurementState

/** Stateless minimal UI for physical capture and quality feedback. */
@Composable
fun MeasurementScreen(
    engine: MeasurementEngine,
    state: CaptureState,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmCancel by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Pengukuran Carda", style = MaterialTheme.typography.headlineMedium)
        Text("Letakkan ujung jari di kamera belakang dan lampu kilat. Jangan menekan terlalu kuat.")
        if (!state.sessionStopped && state.state !in setOf(
                MeasurementState.COMPLETE,
                MeasurementState.UNSUPPORTED_DEVICE,
                MeasurementState.ERROR,
            )
        ) {
            MeasurementPreview(engine, Modifier.fillMaxWidth().height(180.dp))
        }
        Text("Grafik PPG kamera sementara", style = MaterialTheme.typography.titleMedium)
        if (state.tracePoints.size >= 2) {
            Canvas(Modifier.fillMaxWidth().height(100.dp).semantics {
                contentDescription = "Grafik PPG langsung dari kamera; hasil numerik menunggu kualitas sinyal"
            }) {
                val points = state.tracePoints
                val path = Path()
                points.forEachIndexed { index, value ->
                    val x = index.toFloat() / (points.size - 1) * size.width
                    val y = (1f - value) * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Color.Red, style = Stroke(width = 3.dp.toPx()))
            }
        } else Text("Grafik muncul setelah kamera membaca sinyal.")
        LinearProgressIndicator(
            progress = { state.progressSeconds.toFloat() / state.targetSeconds },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("${state.progressSeconds} / ${state.targetSeconds} detik")
        val actionableStatus = state.state in setOf(
            MeasurementState.TOO_DARK, MeasurementState.MOTION_DETECTED,
            MeasurementState.POOR_CONTACT, MeasurementState.RETRY,
            MeasurementState.UNSUPPORTED_DEVICE, MeasurementState.ERROR,
        )
        Text(state.message,
            modifier = if (actionableStatus) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
            style = MaterialTheme.typography.bodyLarge)
        state.nominalDevice?.let { nominal ->
            Text("Syarat perangkat nominal: API 26 ${nominal.api26.mark()}, ARM64 ${nominal.arm64.mark()}, 4 inti ${nominal.fourProcessors.mark()}, RAM 3 GB ${nominal.threeGbRam.mark()}, ruang 250 MB ${nominal.storage250Mb.mark()}, analisis 720p ${nominal.analysis720p.mark()}, cadence 30 fps ${nominal.observed30Fps.mark()}. Kualitas sinyal aktual tetap diperiksa terpisah.")
        }
        state.quality?.let { quality ->
            Text("Kualitas sinyal: ${(quality.score * 100).toInt()}% (skor teknis, bukan akurasi kesehatan)")
        }
        if (state.state == MeasurementState.MEASURING) {
            state.heartRateBpm?.let { bpm ->
                Text("Denyut sementara: ${bpm.toInt()} kali/menit. Menunggu sesi lengkap.")
            }
        }
        if (state.sessionStopped && state.state != MeasurementState.COMPLETE) {
            Button(onClick = onRetry) { Text("Coba sesi baru") }
        }
        if (confirmCancel) {
            Text("Batalkan sesi dan matikan kamera? Hasil yang belum selesai tidak disimpan.")
            Button(onClick = onDone) { Text("Ya, batalkan sesi") }
            Button(onClick = { confirmCancel = false }) { Text("Lanjutkan pengukuran") }
        } else {
            Button(onClick = {
                if (state.state == MeasurementState.COMPLETE ||
                    state.state == MeasurementState.UNSUPPORTED_DEVICE ||
                    state.state == MeasurementState.ERROR
                ) onDone() else confirmCancel = true
            }) { Text(when (state.state) {
                MeasurementState.COMPLETE -> "Selesai"
                MeasurementState.UNSUPPORTED_DEVICE, MeasurementState.ERROR -> "Kembali"
                else -> "Batalkan pengukuran"
            }) }
        }
    }
}

private fun Boolean?.mark(): String = when (this) {
    true -> "sesuai"
    false -> "di bawah target"
    null -> "belum terukur"
}
