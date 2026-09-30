package id.carda.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.carda.core.model.HealthCondition
import id.carda.core.model.LocalProfile
import id.carda.core.model.DeviceCompatibilityRecord
import id.carda.core.model.DeviceProfile
import id.carda.core.model.QualityIssue
import id.carda.core.model.Sex
import id.carda.core.model.SupportClassification
import id.carda.core.model.AccountDataGate
import id.carda.core.model.MeasurementRepository
import id.carda.core.model.MeasurementSummary
import id.carda.core.model.StoredMeasurement
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfilePreferencesInstrumentedTest {
    @Test fun erasedAccountRejectsDelayedCompatibilityAndConsentWrites() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val testFile = File(context.noBackupFilesDir, "carda-erasure-test-${UUID.randomUUID()}.preferences_pb")
        val dataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val raw = ProfilePreferencesStore(PreferenceDataStoreFactory.create(scope = dataScope,
            produceFile = { testFile }))
        val gate = AccountDataGate()
        val guarded = GuardedProfileRepository(raw, gate)
        val account = "ec87a9d1-6035-443b-b14a-ad36d879c9d8"
        val emptyMeasurements = object : MeasurementRepository {
            override suspend fun save(accountId: String, summary: MeasurementSummary): Long = error("unused")
            override fun observeHistory(accountId: String) = flowOf(emptyList<StoredMeasurement>())
            override suspend fun find(accountId: String, id: Long): StoredMeasurement? = null
            override suspend fun deleteAll(accountId: String) = 0
        }
        try {
            guarded.save(LocalProfile(account, fullName = "Synthetic fixture"))
            guarded.acceptConsent(account, "consent-v1")
            guarded.setReminderHour(account, 8)
            AccountLocalDataCleaner(emptyMeasurements, raw, gate, {}).clean(account)
            assertTrue(runCatching { guarded.acceptConsent(account, "stale-consent") }.isFailure)
            assertTrue(runCatching { guarded.setReminderHour(account, 18) }.isFailure)
            val device = DeviceCompatibilityRecord(1_000L, DeviceProfile("Fixture", "Fixture", 36,
                "arm64-v8a", "test", "fixture", true, false, null, null, null, null, null,
                emptySet(), null, SupportClassification.NOT_SUPPORTED))
            assertTrue(runCatching { guarded.saveDeviceCompatibility(account, device) }.isFailure)
            assertEquals("", raw.observe(account).first().fullName)
            assertNull(raw.observeConsentVersion(account).first())
            assertNull(raw.observeReminderHour(account).first())
            assertNull(raw.observeDeviceCompatibility(account).first())
        } finally {
            dataScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            testFile.delete()
        }
    }

    @Test fun profileAndConsentStayAccountScopedAndCanBeRemoved() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val testFile = File(context.noBackupFilesDir, "carda-profile-test-${UUID.randomUUID()}.preferences_pb")
        val store = ProfilePreferencesStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { testFile },
        ))
        val accountA = "ec87a9d1-6035-443b-b14a-ad36d879c9d8"
        val accountB = "43718466-854c-4c97-b2c8-50dfb369fa2a"
        val profile = LocalProfile(accountA, "Contoh", "1990-01-01", Sex.FEMALE,
            "081200000000", 170.0, 68.0, setOf(HealthCondition.HYPERTENSION), "")
        store.save(profile)
        store.acceptConsent(accountA, "consent-v1")
        store.setReminderHour(accountA, 8)
        val device = DeviceCompatibilityRecord(1_000L, DeviceProfile(
            "Example", "NoTorch", 36, "arm64-v8a", "0.1.0", "ppg-0.1",
            true, false, 1280, 720, null, null, "torch unavailable",
            setOf(QualityIssue.NO_TORCH), null, SupportClassification.NOT_SUPPORTED,
        ))
        store.saveDeviceCompatibility(accountA, device)
        assertEquals(profile, store.observe(accountA).first())
        assertEquals("", store.observe(accountB).first().fullName)
        assertEquals("consent-v1", store.observeConsentVersion(accountA).first())
        assertNull(store.observeConsentVersion(accountB).first())
        assertEquals(8, store.observeReminderHour(accountA).first())
        assertNull(store.observeReminderHour(accountB).first())
        assertEquals(device, store.observeDeviceCompatibility(accountA).first())
        assertNull(store.observeDeviceCompatibility(accountB).first())
        store.delete(accountA)
        store.revokeConsent(accountA)
        store.setReminderHour(accountA, null)
        store.deleteDeviceCompatibility(accountA)
        assertTrue(store.observe(accountA).first().conditions.isEmpty())
        assertNull(store.observeConsentVersion(accountA).first())
        assertNull(store.observeReminderHour(accountA).first())
        assertNull(store.observeDeviceCompatibility(accountA).first())
    }
}
