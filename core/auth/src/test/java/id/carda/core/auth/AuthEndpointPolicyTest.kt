package id.carda.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthEndpointPolicyTest {
    @Test fun productionAcceptsHttpsOnly() {
        assertTrue(AuthEndpointPolicy.isAllowed("https://auth.carda.example", false))
        assertFalse(AuthEndpointPolicy.isAllowed("http://auth.carda.example", false))
        assertFalse(AuthEndpointPolicy.isAllowed("http://10.0.2.2:8080", false))
    }

    @Test fun debugAcceptsOnlyLoopbackPlaintext() {
        assertTrue(AuthEndpointPolicy.isAllowed("http://10.0.2.2:8080", true))
        assertTrue(AuthEndpointPolicy.isAllowed("http://localhost:8080", true))
        assertFalse(AuthEndpointPolicy.isAllowed("http://192.168.1.10:8080", true))
    }

    @Test fun rejectsEmbeddedCredentialsAndPaths() {
        assertFalse(AuthEndpointPolicy.isAllowed("https://user:secret@auth.carda.example", false))
        assertFalse(AuthEndpointPolicy.isAllowed("https://auth.carda.example/other", false))
        assertFalse(AuthEndpointPolicy.isAllowed("https://auth.carda.example?q=1", false))
    }
}
