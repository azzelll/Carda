package id.carda.feature.onboarding

import id.carda.core.model.LocalProfile
import id.carda.core.model.Sex
import java.time.LocalDate

/** Local-only form details. No credentials, HTTP serialization or automatic sensitive toString. */
class RegistrationDetails(
    val fullName: String,
    val birthDate: String?,
    val sex: Sex,
    val phone: String,
) {
    init {
        require(fullName.length <= 100 && fullName.none { it.isISOControl() })
        require(birthDate == null || LocalDate.parse(birthDate) <= LocalDate.now())
        require(phone.isEmpty() || (phone.length <= 32 && phone.matches(Regex("[+0-9() .-]+"))))
    }

    fun applyTo(profile: LocalProfile): LocalProfile = profile.copy(
        fullName = fullName, birthDate = birthDate, sex = sex, phone = phone,
    )

    override fun toString(): String = "RegistrationDetails(personalFields=redacted)"
}
