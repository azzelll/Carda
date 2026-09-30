package id.carda.feature.measurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.carda.core.model.DeviceCompatibilityRecord
import id.carda.core.model.DeviceProfile
import id.carda.core.model.LocalProfileRepository
import id.carda.core.model.MeasurementRepository
import id.carda.core.model.MeasurementSummary
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RecordingState {
    data object Idle : RecordingState
    data object Saving : RecordingState
    data object Saved : RecordingState
    data object Failed : RecordingState
}

/** Feature owns persistence and retries. The app connects navigation and domain-facing contracts. */
@HiltViewModel
class MeasurementRecordingViewModel @Inject constructor(
    private val measurements: MeasurementRepository,
    private val profiles: LocalProfileRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state = mutable.asStateFlow()
    private var currentKey: Pair<String, Long>? = null

    fun record(accountId: String?, summary: MeasurementSummary, retry: Boolean = false) {
        if (accountId == null) return // Clearly labelled engineering capture is never persisted.
        val key = accountId to summary.measuredAtEpochMillis
        if (currentKey == key && !(retry && mutable.value == RecordingState.Failed)) return
        if (mutable.value == RecordingState.Saving) return
        currentKey = key
        mutable.value = RecordingState.Saving
        viewModelScope.launch {
            mutable.value = try {
                measurements.save(accountId, summary)
                RecordingState.Saved
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { RecordingState.Failed }
        }
    }

    fun recordCompatibility(accountId: String?, device: DeviceProfile) {
        if (accountId == null) return
        viewModelScope.launch {
            try {
                profiles.saveDeviceCompatibility(accountId,
                    DeviceCompatibilityRecord(System.currentTimeMillis(), device))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* A compatibility write must not invalidate an accepted metric. */ }
        }
    }
}
