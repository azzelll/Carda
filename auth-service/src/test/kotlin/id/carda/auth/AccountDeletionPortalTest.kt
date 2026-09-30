package id.carda.auth

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/** Real MVC/security/static-resource stack with fake identity service, not a deployed integration. */
@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class, AccessFilter::class)
class AccountDeletionPortalTest {
    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: AuthService
    @MockitoBean lateinit var repository: IdentityRepository
    @MockitoBean lateinit var tokens: TokenFactory

    @Test fun portalAssetsArePublicWithRestrictiveHeaders() {
        listOf("index.html", "portal.js", "portal.css").forEach { name ->
            mvc.perform(get("/account-delete/$name"))
                .andExpect(status().isOk)
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("connect-src 'self'")))
        }
    }

    @Test fun publicPortalDoesNotMakeDeletionApiPublic() {
        mvc.perform(delete("/api/v1/auth/account").contentType("application/json")
            .content("{\"password\":\"synthetic-fixture-password\"}"))
            .andExpect(status().isUnauthorized)
        mvc.perform(get("/account-delete/secret"))
            .andExpect(status().isUnauthorized)
    }

    @Test fun duplicateAuthorizationHeadersCannotAuthenticateDeletion() {
        val token = TokenFactory().issue().plaintext
        `when`(tokens.hash(token)).thenReturn("synthetic-hash")
        `when`(repository.accountForAccess(anyString(), any(Instant::class.java) ?: Instant.EPOCH))
            .thenReturn(UUID.randomUUID())

        mvc.perform(delete("/api/v1/auth/account")
            .header("Authorization", "Bearer $token", "Bearer another-token")
            .contentType("application/json")
            .content("{\"password\":\"synthetic-fixture-password\"}"))
            .andExpect(status().isUnauthorized)
    }

    @Test fun authenticatedDeletionPassesRequestAddressToService() {
        val id = UUID.randomUUID()
        val token = TokenFactory().issue().plaintext
        `when`(tokens.hash(token)).thenReturn("synthetic-hash")
        `when`(repository.accountForAccess(anyString(), any(Instant::class.java) ?: Instant.EPOCH))
            .thenReturn(id)

        mvc.perform(delete("/api/v1/auth/account")
            .with { request -> request.remoteAddr = "192.0.2.10"; request }
            .header("Authorization", "Bearer $token")
            .contentType("application/json")
            .content("{\"password\":\"synthetic-fixture-password\"}"))
            .andExpect(status().isNoContent)
        verify(service).deleteAccount(id, "synthetic-fixture-password", "192.0.2.10")
    }

    @Test fun verificationRequiresRecipientChosenPassword() {
        mvc.perform(post("/api/v1/auth/verify-email")
            .contentType("application/json")
            .content("{\"token\":\"synthetic-verification-token\"}"))
            .andExpect(status().isBadRequest)
        mvc.perform(post("/api/v1/auth/verify-email")
            .contentType("application/json")
            .content("{\"token\":\"synthetic-verification-token\",\"newPassword\":\"short\"}"))
            .andExpect(status().isBadRequest)
    }
}
