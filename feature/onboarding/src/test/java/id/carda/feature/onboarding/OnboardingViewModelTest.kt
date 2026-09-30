package id.carda.feature.onboarding

import id.carda.core.auth.AuthApiException
import id.carda.core.auth.AuthSession
import id.carda.core.auth.IdentityAccess
import id.carda.core.auth.IdentityEnvironment
import id.carda.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun unavailableServerAndWeakPasswordDoNotCallIdentity() = runTest(dispatcher) {
        assertEquals(OnboardingState.Unconfigured, OnboardingViewModel(IdentityEnvironment(null), FakeProfiles()).state.value)
        val fake = FakeIdentity()
        val vm = OnboardingViewModel(IdentityEnvironment(fake), FakeProfiles())
        vm.submit(AccountAction.REGISTER, "test@example.invalid", "short")
        assertEquals(0, fake.calls)
        assertTrue((vm.state.value as OnboardingState.Ready).message.contains("12–128"))
    }

    @Test fun duplicateSubmissionDuringLoginCallsIdentityOnce() = runTest(dispatcher) {
        val fake = FakeIdentity()
        fake.waitForLogin = CompletableDeferred()
        val vm = OnboardingViewModel(IdentityEnvironment(fake), FakeProfiles())
        vm.submit(AccountAction.LOGIN, "test@example.invalid", "test-password")
        vm.submit(AccountAction.LOGIN, "test@example.invalid", "test-password")
        testScheduler.runCurrent()
        assertEquals(1, fake.calls)
        assertTrue(vm.state.value is OnboardingState.Working)
        fake.waitForLogin!!.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(OnboardingState.SignedIn, vm.state.value)
        vm.acknowledgeSignIn()
        assertTrue(vm.state.value is OnboardingState.Ready)
    }

    @Test fun failureKeepsRetryStateWithoutCredentialOrCode() = runTest(dispatcher) {
        val fake = FakeIdentity()
        fake.failure = AuthApiException(429, "rate_limited")
        val vm = OnboardingViewModel(IdentityEnvironment(fake), FakeProfiles())
        vm.submit(AccountAction.VERIFY_EMAIL, password = "recipient-password", token = "secret-code")
        testScheduler.runCurrent()
        val state = vm.state.value as OnboardingState.Ready
        assertTrue(state.message.contains("Terlalu banyak"))
        assertEquals(1, state.clearSecretsRevision)
        assertTrue(!state.toString().contains("secret-code"))
        assertTrue(!state.toString().contains("recipient-password"))
    }

    @Test fun verificationRequiresRecipientChosenPasswordAndForwardsItWithCode() = runTest(dispatcher) {
        val fake = FakeIdentity()
        val vm = OnboardingViewModel(IdentityEnvironment(fake), FakeProfiles())
        vm.submit(AccountAction.VERIFY_EMAIL, password = "short", token = "fixture-code")
        testScheduler.runCurrent()
        assertEquals(0, fake.calls)
        assertTrue((vm.state.value as OnboardingState.Ready).message.contains("12–128"))

        vm.submit(AccountAction.VERIFY_EMAIL, password = "owner-chosen-password", token = "fixture-code")
        testScheduler.runCurrent()
        assertEquals(1, fake.calls)
        assertEquals("fixture-code", fake.verifiedToken)
        assertEquals("owner-chosen-password", fake.verifiedPassword)
        val ready = vm.state.value as OnboardingState.Ready
        assertTrue(ready.message.contains("kata sandi"))
        assertFalse(ready.toString().contains("fixture-code"))
        assertFalse(ready.toString().contains("owner-chosen-password"))
    }

    @Test fun registrationDetailsStayLocalUntilMatchingLoginAndPreserveOtherProfileFields() = runTest(dispatcher) {
        val fake = FakeIdentity()
        val profiles = FakeProfiles()
        val vm = OnboardingViewModel(IdentityEnvironment(fake), profiles)
        val details = RegistrationDetails("Synthetic person", "1990-01-01", Sex.FEMALE, "+620000000000")
        vm.submit(AccountAction.REGISTER, "fixture@example.invalid", "fixture-password", details = details)
        testScheduler.runCurrent()
        assertEquals(null, profiles.saved)
        vm.submit(AccountAction.LOGIN, "FIXTURE@example.invalid", "fixture-password")
        testScheduler.runCurrent()
        assertEquals(OnboardingState.SignedIn, vm.state.value)
        assertEquals("Synthetic person", profiles.saved!!.fullName)
        assertEquals("1990-01-01", profiles.saved!!.birthDate)
        assertEquals(170.0, profiles.saved!!.heightCm!!, 0.0)
        assertEquals("Synthetic medicine", profiles.saved!!.medicines)
        assertFalse(vm.state.value.toString().contains("Synthetic person"))
        assertFalse(details.toString().contains("1990"))
    }

    @Test fun anotherAccountLoginNeverReceivesRegistrationProfile() = runTest(dispatcher) {
        val profiles = FakeProfiles()
        val vm = OnboardingViewModel(IdentityEnvironment(FakeIdentity()), profiles)
        vm.submit(AccountAction.REGISTER, "first@example.invalid", "fixture-password",
            details = RegistrationDetails("First", null, Sex.UNSPECIFIED, ""))
        testScheduler.runCurrent()
        vm.submit(AccountAction.LOGIN, "other@example.invalid", "fixture-password")
        testScheduler.runCurrent()
        assertEquals(OnboardingState.SignedIn, vm.state.value)
        assertEquals(null, profiles.saved)
    }

    @Test fun profileSaveFailureRetriesLocallyWithoutRepeatingLogin() = runTest(dispatcher) {
        val fake = FakeIdentity()
        val profiles = FakeProfiles().apply { failSave = true }
        val vm = OnboardingViewModel(IdentityEnvironment(fake), profiles)
        vm.submit(AccountAction.REGISTER, "fixture@example.invalid", "fixture-password",
            details = RegistrationDetails("Fixture", null, Sex.UNSPECIFIED, ""))
        testScheduler.runCurrent()
        vm.submit(AccountAction.LOGIN, "fixture@example.invalid", "fixture-password")
        testScheduler.runCurrent()
        assertEquals(OnboardingState.LocalProfilePending, vm.state.value)
        val calls = fake.calls
        profiles.failSave = false
        vm.retryLocalProfile()
        vm.retryLocalProfile()
        testScheduler.runCurrent()
        assertEquals(OnboardingState.SignedIn, vm.state.value)
        assertEquals(calls, fake.calls)
        assertEquals(2, profiles.saveCalls)
    }

    private class FakeProfiles : LocalProfileRepository {
        var saved: LocalProfile? = null
        var failSave = false
        var saveCalls = 0
        override fun observe(accountId: String) = flowOf(LocalProfile(accountId, heightCm = 170.0,
            weightKg = 65.0, medicines = "Synthetic medicine"))
        override suspend fun save(profile: LocalProfile) { saveCalls++; if (failSave) error("fixture storage failure"); saved = profile }
        override suspend fun delete(accountId: String) = Unit
        override fun observeConsentVersion(accountId: String): Flow<String?> = flowOf(null)
        override suspend fun acceptConsent(accountId: String, version: String) = Unit
        override suspend fun revokeConsent(accountId: String) = Unit
        override fun observeReminderHour(accountId: String): Flow<Int?> = flowOf(null)
        override suspend fun setReminderHour(accountId: String, hour: Int?) = Unit
        override fun observeDeviceCompatibility(accountId: String): Flow<DeviceCompatibilityRecord?> = flowOf(null)
        override suspend fun saveDeviceCompatibility(accountId: String, record: DeviceCompatibilityRecord) = Unit
        override suspend fun deleteDeviceCompatibility(accountId: String) = Unit
    }

    private class FakeIdentity : IdentityAccess {
        var calls = 0
        var failure: Exception? = null
        var waitForLogin: CompletableDeferred<Unit>? = null
        var verifiedToken: String? = null
        var verifiedPassword: String? = null
        private fun invoked() { calls++; failure?.let { throw it } }
        override suspend fun register(email: String, password: String) = invoked()
        override suspend fun verifyEmail(token: String, newPassword: String) {
            verifiedToken = token
            verifiedPassword = newPassword
            invoked()
        }
        override suspend fun login(email: String, password: String): AuthSession {
            invoked()
            waitForLogin?.await()
            return AuthSession("ec87a9d1-6035-443b-b14a-ad36d879c9d8", "fake-access", "fake-refresh", 1L)
        }
        override suspend fun requestPasswordReset(email: String) = invoked()
        override suspend fun confirmPasswordReset(token: String, newPassword: String) = invoked()
        override suspend fun logout() = invoked()
        override suspend fun deleteAccount(password: String) = invoked()
    }
}
