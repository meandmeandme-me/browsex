package com.browsex.app

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class OAuthPkceTransactionTest {
    private val redirect = "com.browsex.app:/oauth2redirect"

    @Test fun createsRfc7636S256Challenge() {
        val transaction = OAuthPkceTransaction.create(IdentityProvider.GOOGLE, redirect, 1000L)
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(transaction.codeVerifier.toByteArray(Charsets.US_ASCII))
        )
        assertEquals(expected, transaction.codeChallenge)
        assertTrue(transaction.codeVerifier.length in 43..128)
        assertTrue(transaction.state.length >= 32)
    }

    @Test fun acceptsMatchingFreshCallbackAndReturnsOnlyCode() {
        val transaction = OAuthPkceTransaction.create(IdentityProvider.MICROSOFT, redirect, 1000L)
        val callback = "$redirect?code=auth-code&state=${transaction.state}"
        assertEquals("auth-code", transaction.validateCallback(callback, 2000L))
        assertNull("A callback must not be accepted twice", transaction.validateCallback(callback, 2000L))
    }

    @Test fun invalidCallbackDoesNotConsumeTransactionButValidCallbackDoes() {
        val transaction = OAuthPkceTransaction.create(IdentityProvider.GOOGLE, redirect, 1000L)
        assertNull(transaction.validateCallback("$redirect?code=bad&state=wrong", 2000L))
        val valid = "$redirect?code=good&state=${transaction.state}"
        assertEquals("good", transaction.validateCallback(valid, 2000L))
        assertNull(transaction.validateCallback(valid, 2000L))
    }

    @Test fun rejectsWrongStateRedirectAndExpiredTransaction() {
        val transaction = OAuthPkceTransaction.create(IdentityProvider.APPLE, redirect, 1000L)
        assertNull(transaction.validateCallback("$redirect?code=x&state=wrong", 2000L))
        assertNull(transaction.validateCallback("com.attacker.app:/oauth2redirect?code=x&state=${transaction.state}", 2000L))
        assertNull(transaction.validateCallback("$redirect?code=x&state=${transaction.state}", 1000L + OAuthPkceTransaction.DEFAULT_MAX_AGE_MILLIS + 1))
    }

    @Test fun rejectsProviderErrorsDuplicateParametersAndFragments() {
        val transaction = OAuthPkceTransaction.create(IdentityProvider.GOOGLE, redirect, 1000L)
        assertNull(transaction.validateCallback("$redirect?error=access_denied&state=${transaction.state}", 2000L))
        assertNull(transaction.validateCallback("$redirect?code=a&code=b&state=${transaction.state}", 2000L))
        assertNull(transaction.validateCallback("$redirect?code=a&state=${transaction.state}#fragment", 2000L))
    }

    @Test fun rejectsInsecureOrMalformedRedirectConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            OAuthPkceTransaction.create(IdentityProvider.GOOGLE, "http://example.com/callback", 1000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OAuthPkceTransaction.create(IdentityProvider.GOOGLE, "com.browsex.app:/callback#fragment", 1000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OAuthPkceTransaction.create(IdentityProvider.GOOGLE, "javascript:/callback", 1000L)
        }
    }
}
