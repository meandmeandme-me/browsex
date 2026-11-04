package com.browsex.app

import org.junit.Assert.*
import org.junit.Test

class CredentialOriginPolicyTest {
    @Test fun acceptsAndCanonicalizesOnlyHttpsOrigins() {
        assertEquals("https://example.com", CredentialOriginPolicy.canonicalHttpsOrigin("HTTPS://Example.COM/"))
        assertEquals("https://example.com:8443", CredentialOriginPolicy.canonicalHttpsOrigin("https://Example.com:8443"))
    }

    @Test fun rejectsInsecureOrAmbiguousOrigins() {
        listOf(
            "http://example.com",
            "file:///etc/passwd",
            "https://user@example.com",
            "https://example.com/path",
            "https://example.com?next=other",
            "https://example.com#fragment",
            "https://example.com:",
            "https://example.com:65536"
        ).forEach { assertNull(it, CredentialOriginPolicy.canonicalHttpsOrigin(it)) }
    }

    @Test fun credentialsNeverMatchSubdomainsOrDifferentPorts() {
        assertTrue(CredentialOriginPolicy.matchesExactOrigin("https://example.com", "example.com"))
        assertFalse(CredentialOriginPolicy.matchesExactOrigin("https://example.com", "login.example.com"))
        assertFalse(CredentialOriginPolicy.matchesExactOrigin("https://example.com:8443", "example.com"))
    }
}
