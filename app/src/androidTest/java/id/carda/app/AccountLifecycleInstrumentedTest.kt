package id.carda.app

import android.content.Context
import android.Manifest
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import id.carda.core.auth.*
import id.carda.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import javax.inject.Singleton

/** Actual Activity recreation with fake delayed identity; no cloud or physiological evidence. */
@HiltAndroidTest
@UninstallModules(AppDependencies::class)
class AccountLifecycleInstrumentedTest {
    private val sessions = FakeSessions()
    private val identity = DelayedIdentity()
    private val localCleaner = DelayedCleaner()
    @BindValue @JvmField val storage: SessionStorage = sessions
    @BindValue @JvmField val environment = IdentityEnvironment(identity)
    @BindValue @JvmField val cleaner: LocalAccountCleaner = localCleaner
    @Inject lateinit var pending: PendingDeletionStore
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recreatedActivityWaitsForExistingLogoutAndNeverStartsCaptureDuringTeardown() {
        compose.onNodeWithText("Keluar", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Mulai pengukuran").performScrollTo().assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Keluar", substring = false).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Mulai pengukuran").performScrollTo().assertIsNotEnabled()
        identity.release.complete(Unit)
        compose.waitUntil(5_000) { sessions.value == null }
        compose.onNodeWithText("Masuk", substring = false).performScrollTo().assertIsEnabled()
        assertEquals(1, identity.calls)
    }

    @Test fun healthScreensAreExcludedFromSystemScreenshotsAndRecents() {
        compose.activityRule.scenario.onActivity { activity ->
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }

    @Test fun recreatedActivityCompletesExistingDeletionAndAllowsLoginAgain() {
        compose.onNodeWithText("Hapus akun daring").performScrollTo().performClick()
        compose.onNodeWithText("Kata sandi untuk konfirmasi").performScrollTo().performTextInput("fixture-password")
        compose.onNodeWithText("Ya, hapus akun dan data lokal").performScrollTo().performClick()
        compose.onNodeWithText("Mulai pengukuran").performScrollTo().assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Keluar", substring = false).performScrollTo().assertIsNotEnabled()
        identity.release.complete(Unit)
        compose.waitUntil(5_000) { sessions.value == null }
        compose.onNodeWithText("Akun daring dan data lokal akun ini dihapus.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Masuk", substring = false).performScrollTo().assertIsEnabled()
        assertEquals(1, identity.calls)
    }

    @Test fun freshViewModelResumesPendingCleanupBeforeShowingLogin() {
        hilt.inject()
        sessions.value = null
        pending.mark("ec87a9d1-6035-443b-b14a-ad36d879c9d8")
        localCleaner.release = CompletableDeferred()
        // Construct a fresh state owner against the existing marker. This is not actual process death.
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        compose.activityRule.scenario.recreate()
        compose.onAllNodesWithText("Masuk", substring = false).assertCountEquals(0)
        compose.onNodeWithText("Ulangi pembersihan data lokal").performScrollTo().assertIsNotEnabled()
        localCleaner.release!!.complete(Unit)
        compose.onNodeWithText("Masuk", substring = false).performScrollTo().assertIsEnabled()
        assertEquals(1, localCleaner.calls)
        assertEquals(0, identity.calls)
    }

    @Test fun nonCaptureNavigationAndDurationSurviveSameAccountRecreation() {
        compose.onNodeWithText("60 detik", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Edit profil lokal").performScrollTo().performClick()
        compose.onNodeWithText("Profil lokal", substring = false).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Profil lokal", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Kembali", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("✓ 60 detik", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lihat riwayat lokal").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Kembali", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Mulai pengukuran").performScrollTo().assertIsEnabled()
    }

    @Test fun activeCaptureDoesNotRestoreAfterActivityRecreation() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            "id.carda.app", Manifest.permission.CAMERA)
        compose.onNodeWithText("Mulai pengukuran").performScrollTo().performClick()
        compose.onNodeWithText("Pengukuran Carda", substring = false).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onAllNodesWithText("Pengukuran Carda", substring = false).assertCountEquals(0)
        compose.onNodeWithText("Mulai pengukuran").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Profil lokal belum diisi.", substring = true).performScrollTo().assertIsDisplayed()
    }

    private class FakeSessions : SessionStorage {
        @Volatile var value: AuthSession? = AuthSession("ec87a9d1-6035-443b-b14a-ad36d879c9d8",
            "fake-access", "fake-refresh", 1L)
        override fun read() = value
        override fun clear() { value = null }
    }
    private class DelayedIdentity : IdentityAccess {
        val release = CompletableDeferred<Unit>()
        @Volatile var calls = 0
        private suspend fun awaitResponse() { calls++; release.await() }
        override suspend fun logout() = awaitResponse()
        override suspend fun deleteAccount(password: String) = awaitResponse()
        override suspend fun register(email: String, password: String) = Unit
        override suspend fun verifyEmail(token: String, newPassword: String) = Unit
        override suspend fun login(email: String, password: String) = error("unused")
        override suspend fun requestPasswordReset(email: String) = Unit
        override suspend fun confirmPasswordReset(token: String, newPassword: String) = Unit
    }
    private class DelayedCleaner : LocalAccountCleaner {
        var release: CompletableDeferred<Unit>? = null
        @Volatile var calls = 0
        override suspend fun clean(accountId: String) { calls++; release?.await() }
    }

    @Module @InstallIn(SingletonComponent::class)
    internal object LocalFakes {
        @Provides fun reminders(@ApplicationContext context: Context) = ReminderScheduler(context)
        @Provides @Singleton fun pending(): PendingDeletionStore = object : PendingDeletionStore {
            private var value: String? = null
            override fun read() = value
            override fun mark(accountId: String) { value = accountId }
            override fun clear() { value = null }
        }
        @Provides fun deletion(pending: PendingDeletionStore) = AccountDeletionCoordinator(pending)
        @Provides fun measurements(): MeasurementRepository = object : MeasurementRepository {
            override suspend fun save(accountId: String, summary: MeasurementSummary): Long = error("unused")
            override fun observeHistory(accountId: String) = flowOf(emptyList<StoredMeasurement>())
            override suspend fun find(accountId: String, id: Long): StoredMeasurement? = null
            override suspend fun deleteAll(accountId: String) = 0
        }
        @Provides fun profiles(): LocalProfileRepository = object : LocalProfileRepository {
            override fun observe(accountId: String) = flowOf(LocalProfile(accountId))
            override suspend fun save(profile: LocalProfile) = Unit
            override suspend fun delete(accountId: String) = Unit
            override fun observeConsentVersion(accountId: String): Flow<String?> = flowOf("consent-v1")
            override suspend fun acceptConsent(accountId: String, version: String) = Unit
            override suspend fun revokeConsent(accountId: String) = Unit
            override fun observeReminderHour(accountId: String): Flow<Int?> = flowOf(null)
            override suspend fun setReminderHour(accountId: String, hour: Int?) = Unit
            override fun observeDeviceCompatibility(accountId: String): Flow<DeviceCompatibilityRecord?> = flowOf(null)
            override suspend fun saveDeviceCompatibility(accountId: String, record: DeviceCompatibilityRecord) = Unit
            override suspend fun deleteDeviceCompatibility(accountId: String) = Unit
        }
    }
}
