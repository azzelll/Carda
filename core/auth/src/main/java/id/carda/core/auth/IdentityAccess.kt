package id.carda.core.auth

/** Identity use cases only. Feature state does not depend on HTTP, JSON or Keystore. */
interface IdentityAccess {
    suspend fun register(email: String, password: String)
    suspend fun verifyEmail(token: String, newPassword: String)
    suspend fun login(email: String, password: String): AuthSession
    suspend fun requestPasswordReset(email: String)
    suspend fun confirmPasswordReset(token: String, newPassword: String)
    suspend fun logout()
    suspend fun deleteAccount(password: String)
}

data class IdentityEnvironment(val access: IdentityAccess?)
