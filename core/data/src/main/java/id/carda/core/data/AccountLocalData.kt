package id.carda.core.data

import id.carda.core.model.AccountDataGate
import id.carda.core.model.DeviceCompatibilityRecord
import id.carda.core.model.LocalAccountCleaner
import id.carda.core.model.LocalProfile
import id.carda.core.model.LocalProfileRepository
import id.carda.core.model.MeasurementRepository
import id.carda.core.model.MeasurementSummary

class GuardedMeasurementRepository(
    private val delegate: MeasurementRepository,
    private val gate: AccountDataGate,
) : MeasurementRepository by delegate {
    override suspend fun save(accountId: String, summary: MeasurementSummary): Long =
        gate.write(accountId) { delegate.save(accountId, summary) }
    override suspend fun deleteAll(accountId: String): Int =
        gate.write(accountId) { delegate.deleteAll(accountId) }
}

class GuardedProfileRepository(
    private val delegate: LocalProfileRepository,
    private val gate: AccountDataGate,
) : LocalProfileRepository by delegate {
    override suspend fun save(profile: LocalProfile) = gate.write(profile.accountId) { delegate.save(profile) }
    override suspend fun delete(accountId: String) = gate.write(accountId) { delegate.delete(accountId) }
    override suspend fun acceptConsent(accountId: String, version: String) =
        gate.write(accountId) { delegate.acceptConsent(accountId, version) }
    override suspend fun revokeConsent(accountId: String) = gate.write(accountId) { delegate.revokeConsent(accountId) }
    override suspend fun setReminderHour(accountId: String, hour: Int?) =
        gate.write(accountId) { delegate.setReminderHour(accountId, hour) }
    override suspend fun saveDeviceCompatibility(accountId: String, record: DeviceCompatibilityRecord) =
        gate.write(accountId) { delegate.saveDeviceCompatibility(accountId, record) }
    override suspend fun deleteDeviceCompatibility(accountId: String) =
        gate.write(accountId) { delegate.deleteDeviceCompatibility(accountId) }
}

/** Uses raw repositories inside the erasure lock. Guarded wrappers are for ordinary feature writes. */
class AccountLocalDataCleaner(
    private val measurements: MeasurementRepository,
    private val profiles: LocalProfileRepository,
    private val gate: AccountDataGate,
    private val cancelReminders: () -> Unit,
) : LocalAccountCleaner {
    override suspend fun clean(accountId: String) = gate.erase(accountId) {
        measurements.deleteAll(accountId)
        profiles.delete(accountId)
        profiles.revokeConsent(accountId)
        profiles.setReminderHour(accountId, null)
        profiles.deleteDeviceCompatibility(accountId)
        cancelReminders()
    }
}
