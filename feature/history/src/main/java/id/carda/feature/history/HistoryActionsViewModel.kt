package id.carda.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.carda.core.model.MeasurementRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryActionState(val accountId: String? = null, val busy: Boolean = false, val message: String = "")

@HiltViewModel
class HistoryActionsViewModel @Inject constructor(private val measurements: MeasurementRepository) : ViewModel() {
    private val mutable = MutableStateFlow(HistoryActionState())
    val state = mutable.asStateFlow()

    fun deleteAll(accountId: String) {
        if (mutable.value.busy) return
        mutable.value = HistoryActionState(accountId, busy = true)
        viewModelScope.launch {
            mutable.value = try {
                measurements.deleteAll(accountId)
                HistoryActionState(accountId, message = "Riwayat lokal akun ini dihapus.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { HistoryActionState(accountId, message = "Riwayat belum terhapus. Coba lagi.") }
        }
    }
}
