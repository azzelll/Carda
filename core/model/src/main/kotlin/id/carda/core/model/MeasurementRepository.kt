package id.carda.core.model

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class StoredMeasurement(val id: Long, val summary: MeasurementSummary)

/** Account-scoped local history. The implementation never receives camera frames or PPG samples. */
interface MeasurementRepository {
    suspend fun save(accountId: String, summary: MeasurementSummary): Long
    fun observeHistory(accountId: String): Flow<List<StoredMeasurement>>
    suspend fun find(accountId: String, id: Long): StoredMeasurement?
    suspend fun deleteAll(accountId: String): Int
}

enum class Sex { FEMALE, MALE, OTHER, UNSPECIFIED }

enum class HealthCondition {
    HYPERTENSION, ARRHYTHMIA, TYPE_1_DIABETES, TYPE_2_DIABETES,
    CORONARY_DISEASE, HEART_SURGERY, HEART_FAILURE,
}

data class LocalProfile(
    val accountId: String,
    val fullName: String = "",
    val birthDate: String? = null,
    val sex: Sex = Sex.UNSPECIFIED,
    val phone: String = "",
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val conditions: Set<HealthCondition> = emptySet(),
    val medicines: String = "",
) {
    init {
        require(accountId.isNotBlank())
        require(birthDate == null || LocalDate.parse(birthDate) <= LocalDate.now())
        require(heightCm == null || (heightCm.isFinite() && heightCm in 50.0..300.0))
        require(weightKg == null || (weightKg.isFinite() && weightKg in 2.0..500.0))
    }

    val bmi: Double?
        get() = if (heightCm == null || weightKg == null) null
        else weightKg / ((heightCm / 100.0) * (heightCm / 100.0))
}

interface LocalProfileRepository {
    fun observe(accountId: String): Flow<LocalProfile>
    suspend fun save(profile: LocalProfile)
    suspend fun delete(accountId: String)
    fun observeConsentVersion(accountId: String): Flow<String?>
    suspend fun acceptConsent(accountId: String, version: String)
    suspend fun revokeConsent(accountId: String)
    fun observeReminderHour(accountId: String): Flow<Int?>
    suspend fun setReminderHour(accountId: String, hour: Int?)
    fun observeDeviceCompatibility(accountId: String): Flow<DeviceCompatibilityRecord?>
    suspend fun saveDeviceCompatibility(accountId: String, record: DeviceCompatibilityRecord)
    suspend fun deleteDeviceCompatibility(accountId: String)
}

data class DeviceCompatibilityRecord(
    val observedAtEpochMillis: Long,
    val profile: DeviceProfile,
) {
    init { require(observedAtEpochMillis > 0) }
}
