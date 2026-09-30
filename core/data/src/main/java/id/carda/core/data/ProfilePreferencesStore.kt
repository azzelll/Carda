package id.carda.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import id.carda.core.model.HealthCondition
import id.carda.core.model.DeviceCompatibilityRecord
import id.carda.core.model.DeviceProfile
import id.carda.core.model.LocalProfile
import id.carda.core.model.LocalProfileRepository
import id.carda.core.model.QualityIssue
import id.carda.core.model.Sex
import id.carda.core.model.SupportClassification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Account-scoped local profile and consent. File lives in noBackupFilesDir. */
class ProfilePreferencesStore(private val store: DataStore<Preferences>) : LocalProfileRepository {
    override fun observe(accountId: String): Flow<LocalProfile> {
        val key = profileKey(accountId)
        return store.data.map { preferences ->
            preferences[key]?.let { decodeProfile(accountId, it) } ?: LocalProfile(accountId)
        }
    }

    override suspend fun save(profile: LocalProfile) {
        val key = profileKey(profile.accountId)
        store.edit { it[key] = encodeProfile(profile) }
    }

    override suspend fun delete(accountId: String) {
        val key = profileKey(accountId)
        store.edit { it.remove(key) }
    }

    override fun observeConsentVersion(accountId: String): Flow<String?> {
        val key = consentKey(accountId)
        return store.data.map { it[key] }
    }

    override suspend fun acceptConsent(accountId: String, version: String) {
        require(version.isNotBlank())
        val key = consentKey(accountId)
        store.edit { it[key] = version }
    }

    override suspend fun revokeConsent(accountId: String) {
        val key = consentKey(accountId)
        store.edit { it.remove(key) }
    }

    override fun observeReminderHour(accountId: String): Flow<Int?> {
        val key = reminderKey(accountId)
        return store.data.map { it[key]?.toIntOrNull()?.takeIf { hour -> hour in 0..23 } }
    }

    override suspend fun setReminderHour(accountId: String, hour: Int?) {
        require(hour == null || hour in 0..23)
        val key = reminderKey(accountId)
        store.edit { preferences ->
            if (hour == null) preferences.remove(key) else preferences[key] = hour.toString()
        }
    }

    override fun observeDeviceCompatibility(accountId: String): Flow<DeviceCompatibilityRecord?> {
        val key = deviceKey(accountId)
        return store.data.map { preferences ->
            preferences[key]?.let { runCatching { decodeDeviceCompatibility(it) }.getOrNull() }
        }
    }

    override suspend fun saveDeviceCompatibility(accountId: String, record: DeviceCompatibilityRecord) {
        val key = deviceKey(accountId)
        store.edit { it[key] = encodeDeviceCompatibility(record) }
    }

    override suspend fun deleteDeviceCompatibility(accountId: String) {
        val key = deviceKey(accountId)
        store.edit { it.remove(key) }
    }

    private fun profileKey(accountId: String) = stringPreferencesKey("profile_${validAccount(accountId)}")
    private fun consentKey(accountId: String) = stringPreferencesKey("consent_${validAccount(accountId)}")
    private fun reminderKey(accountId: String) = stringPreferencesKey("reminder_${validAccount(accountId)}")
    private fun deviceKey(accountId: String) = stringPreferencesKey("device_${validAccount(accountId)}")

    companion object {
        @Volatile private var singleton: ProfilePreferencesStore? = null

        fun open(context: Context): ProfilePreferencesStore = singleton ?: synchronized(this) {
            singleton ?: ProfilePreferencesStore(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { File(context.applicationContext.noBackupFilesDir, "carda_profile.preferences_pb") },
            )).also { singleton = it }
        }
    }
}

private fun validAccount(accountId: String): String {
    require(UUID.fromString(accountId).toString() == accountId.lowercase())
    return accountId.lowercase()
}

private fun encodeProfile(profile: LocalProfile): String = JSONObject()
    .put("fullName", profile.fullName)
    .put("birthDate", profile.birthDate)
    .put("sex", profile.sex.name)
    .put("phone", profile.phone)
    .put("heightCm", profile.heightCm)
    .put("weightKg", profile.weightKg)
    .put("conditions", JSONArray(profile.conditions.map { it.name }))
    .put("medicines", profile.medicines)
    .toString()

private fun decodeProfile(accountId: String, encoded: String): LocalProfile {
    val json = JSONObject(encoded)
    val conditions = json.optJSONArray("conditions") ?: JSONArray()
    return LocalProfile(
        accountId = accountId,
        fullName = json.optString("fullName"),
        birthDate = if (json.isNull("birthDate")) null else json.getString("birthDate"),
        sex = Sex.valueOf(json.optString("sex", Sex.UNSPECIFIED.name)),
        phone = json.optString("phone"),
        heightCm = if (json.isNull("heightCm")) null else json.getDouble("heightCm"),
        weightKg = if (json.isNull("weightKg")) null else json.getDouble("weightKg"),
        conditions = (0 until conditions.length()).map {
            HealthCondition.valueOf(conditions.getString(it))
        }.toSet(),
        medicines = json.optString("medicines"),
    )
}

private fun encodeDeviceCompatibility(record: DeviceCompatibilityRecord): String {
    val device = record.profile
    return JSONObject()
        .put("observedAt", record.observedAtEpochMillis)
        .put("manufacturer", device.manufacturer)
        .put("model", device.model)
        .put("api", device.androidApiLevel)
        .put("abi", device.abi)
        .put("appVersion", device.appVersion)
        .put("pipelineVersion", device.pipelineVersion)
        .put("rearCamera", device.hasRearCamera)
        .put("torch", device.hasTorch)
        .put("width", device.analysisWidth)
        .put("height", device.analysisHeight)
        .put("fps", device.observedFramesPerSecond)
        .put("exposure", device.exposureDescription)
        .put("flash", device.flashDescription)
        .put("reasons", JSONArray(device.rejectionReasons.map { it.name }))
        .put("sqi", device.lastQualityScore)
        .put("support", device.supportClassification.name)
        .toString()
}

private fun decodeDeviceCompatibility(encoded: String): DeviceCompatibilityRecord {
    val json = JSONObject(encoded)
    val reasons = json.optJSONArray("reasons") ?: JSONArray()
    val device = DeviceProfile(
        manufacturer = json.getString("manufacturer"),
        model = json.getString("model"),
        androidApiLevel = json.getInt("api"),
        abi = json.getString("abi"),
        appVersion = json.getString("appVersion"),
        pipelineVersion = json.getString("pipelineVersion"),
        hasRearCamera = json.getBoolean("rearCamera"),
        hasTorch = json.getBoolean("torch"),
        analysisWidth = json.optNullableInt("width"),
        analysisHeight = json.optNullableInt("height"),
        observedFramesPerSecond = json.optNullableDouble("fps"),
        exposureDescription = json.optNullableString("exposure"),
        flashDescription = json.optNullableString("flash"),
        rejectionReasons = (0 until reasons.length()).map {
            QualityIssue.valueOf(reasons.getString(it))
        }.toSet(),
        lastQualityScore = json.optNullableDouble("sqi"),
        supportClassification = SupportClassification.valueOf(json.getString("support")),
    )
    return DeviceCompatibilityRecord(json.getLong("observedAt"), device)
}

private fun JSONObject.optNullableInt(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
private fun JSONObject.optNullableDouble(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
private fun JSONObject.optNullableString(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
