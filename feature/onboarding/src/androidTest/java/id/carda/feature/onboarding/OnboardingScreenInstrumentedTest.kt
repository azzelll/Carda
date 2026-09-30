package id.carda.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loginFormSubmitsEnteredValuesAndWorkingStateDisablesActions() {
        var submitted: AccountAction? = null
        var submittedEmail = ""
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OnboardingScreen(OnboardingState.Ready()) { action, email, _, _, _ ->
                    submitted = action
                    submittedEmail = email
                }
            }
        }
        compose.onNodeWithText("Email").performTextInput("fixture@example.invalid")
        compose.onNodeWithText("Kata sandi", substring = false).performTextInput("fixture-password")
        compose.onNodeWithText("Masuk").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(AccountAction.LOGIN, submitted)
            assertEquals("fixture@example.invalid", submittedEmail)
        }
    }

    @Test fun workingRequestCannotBeSubmittedTwice() {
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OnboardingScreen(OnboardingState.Working(AccountAction.LOGIN)) { _, _, _, _, _ ->
                    error("Disabled action must not submit")
                }
            }
        }
        compose.onNodeWithText("Masuk").assertIsNotEnabled()
        compose.onNodeWithText("Daftar").assertIsNotEnabled()
        compose.onNodeWithText("Memproses permintaan akun…").performScrollTo().assertIsDisplayed()
    }

    @Test fun registrationCollectsProposalFieldsAndRejectsInvalidDateBeforeSubmission() {
        var submitted: RegistrationDetails? = null
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OnboardingScreen(OnboardingState.Ready()) { action, _, _, _, details ->
                    assertEquals(AccountAction.REGISTER, action)
                    submitted = details
                }
            }
        }
        compose.onNodeWithText("Daftar", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Nama untuk profil lokal").performScrollTo().performTextInput("Synthetic profile")
        compose.onNodeWithText("Tanggal lahir (YYYY-MM-DD)").performScrollTo().performTextInput("2030-01-01")
        compose.onNodeWithText("Nomor telepon untuk profil lokal").performScrollTo().performTextInput("+620000000000")
        compose.onNodeWithText("Kirim pendaftaran").performScrollTo().performClick()
        compose.onNodeWithText("Periksa tanggal lahir dan nomor telepon.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, submitted) }
        compose.onNodeWithText("Tanggal lahir (YYYY-MM-DD)").performScrollTo().performTextReplacement("1990-01-01")
        compose.onNodeWithText("Kirim pendaftaran").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Synthetic profile", submitted!!.fullName)
            assertEquals("1990-01-01", submitted!!.birthDate)
            assertEquals("+620000000000", submitted!!.phone)
        }
    }

    @Test fun educationDistinguishesTechnicalQualityAndExperimentalMetrics() {
        compose.setContent { EducationScreen(onBack = {}) }
        compose.onNodeWithText("Kualitas dan denyut jantung").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("skor teknis kualitas sinyal", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PRV, RMSSD dan SDNN").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Interval R–R dan laju napas berbeda").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Akun, riwayat dan privasi").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Kembali", substring = false).performScrollTo().assertIsDisplayed()
    }
}
