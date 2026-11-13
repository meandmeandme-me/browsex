package com.wren.app

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

    @Test fun storedCredentialRowsMustBeCompleteBoundedAndSameOrigin() {
        assertTrue(CredentialOriginPolicy.isValidStoredCredential(
            "credential-1", "https://example.com", "https://example.com",
            "alice", "secret", ""
        ))
        assertFalse(CredentialOriginPolicy.isValidStoredCredential(
            "", "https://example.com", "", "alice", "secret", ""
        ))
        assertFalse(CredentialOriginPolicy.isValidStoredCredential(
            "credential-1", "http://example.com", "", "alice", "secret", ""
        ))
        assertFalse(CredentialOriginPolicy.isValidStoredCredential(
            "credential-1", "https://example.com", "https://attacker.example",
            "alice", "secret", ""
        ))
        assertFalse(CredentialOriginPolicy.isValidStoredCredential(
            "credential-1", "https://example.com", "", "alice", "", ""
        ))
        assertFalse(CredentialOriginPolicy.isValidStoredCredential(
            "credential-1", "https://example.com", "", "a".repeat(513), "secret", ""
        ))
        assertFalse(CredentialOriginPolicy.isValidStoredCredential(
            "credential-1", "https://example.com", "", "alice", "s".repeat(4097), ""
        ))
    }
}
