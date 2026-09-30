package id.carda.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

class RegisterRequest(
    @field:Email @field:NotBlank @field:Size(max = 254) val email: String,
    @field:Size(min = 12, max = 128) val password: String,
)

class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)
class EmailRequest(@field:Email @field:NotBlank @field:Size(max = 254) val email: String)

class VerifyEmailRequest(
    @field:NotBlank @field:Size(max = 128) val token: String,
    @field:NotBlank @field:Size(min = 12, max = 128) val newPassword: String,
)
class RefreshRequest(@field:NotBlank @field:Size(max = 128) val refreshToken: String)
class ResetConfirmRequest(
    @field:NotBlank @field:Size(max = 128) val token: String,
    @field:Size(min = 12, max = 128) val newPassword: String,
)
class DeleteAccountRequest(@field:NotBlank val password: String)

data class AcceptedResponse(val status: String = "accepted")
data class SessionResponse(
    val accountId: UUID,
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

data class AccountRecord(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val verifiedAt: Instant?,
)

data class IssuedToken(val plaintext: String, val hash: String)
data class SessionTokens(val access: IssuedToken, val refresh: IssuedToken)
