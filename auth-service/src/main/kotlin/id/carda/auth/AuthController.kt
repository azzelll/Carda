package id.carda.auth

import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val service: AuthService) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun register(@Valid @RequestBody body: RegisterRequest, request: HttpServletRequest): AcceptedResponse =
        service.register(body, request.remoteAddr)

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun verifyEmail(@Valid @RequestBody body: VerifyEmailRequest, request: HttpServletRequest) =
        service.verifyEmail(body.token, body.newPassword, request.remoteAddr)

    @PostMapping("/login")
    fun login(@Valid @RequestBody body: LoginRequest, request: HttpServletRequest): SessionResponse =
        service.login(body, request.remoteAddr)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody body: RefreshRequest, request: HttpServletRequest): SessionResponse =
        service.refresh(body.refreshToken, request.remoteAddr)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(auth: Authentication, request: HttpServletRequest) =
        service.logout(UUID.fromString(auth.name), bearer(request))

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun resetRequest(@Valid @RequestBody body: EmailRequest, request: HttpServletRequest): AcceptedResponse =
        service.requestPasswordReset(body.email, request.remoteAddr)

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resetConfirm(@Valid @RequestBody body: ResetConfirmRequest, request: HttpServletRequest) =
        service.confirmPasswordReset(body.token, body.newPassword, request.remoteAddr)

    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAccount(auth: Authentication, @Valid @RequestBody body: DeleteAccountRequest, request: HttpServletRequest) =
        service.deleteAccount(UUID.fromString(auth.name), body.password, request.remoteAddr)

    private fun bearer(request: HttpServletRequest): String =
        request.getHeader("Authorization").removePrefix("Bearer ")
}
