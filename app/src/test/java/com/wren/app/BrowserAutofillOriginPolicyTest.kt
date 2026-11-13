package com.wren.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAutofillOriginPolicyTest {
    @Test
    fun passwordFieldMustHaveCanonicalHttpsOrigin() {
        assertEquals(
            "https://example.com",
            BrowserAutofillOriginPolicy.resolve("example.com", null)?.passwordOrigin
        )
        assertNull(BrowserAutofillOriginPolicy.resolve("http://example.com", "example.com"))
        assertNull(BrowserAutofillOriginPolicy.resolve("file:///private", null))
        assertNull(BrowserAutofillOriginPolicy.resolve(null, "example.com"))
    }

    @Test
    fun usernameIsOnlyIncludedWhenItSharesThePasswordOrigin() {
        val exact = BrowserAutofillOriginPolicy.resolve(
            passwordDomain = "example.com",
            usernameDomain = "https://example.com:443/"
        )
        assertTrue(exact?.usernameMatchesPasswordOrigin == true)

        val subdomain = BrowserAutofillOriginPolicy.resolve(
            passwordDomain = "login.example.com",
            usernameDomain = "example.com"
        )
        assertFalse(subdomain?.usernameMatchesPasswordOrigin == true)

        val differentFrame = BrowserAutofillOriginPolicy.resolve(
            passwordDomain = "checkout.example.com",
            usernameDomain = "identity.example.net"
        )
        assertFalse(differentFrame?.usernameMatchesPasswordOrigin == true)
        assertEquals("https://checkout.example.com", differentFrame?.passwordOrigin)
    }

    @Test
    fun portsArePartOfTheOriginAndDefaultHttpsPortIsNormalized() {
        assertTrue(
            BrowserAutofillOriginPolicy.resolve(
                passwordDomain = "example.com:443",
                usernameDomain = "example.com"
            )?.usernameMatchesPasswordOrigin == true
        )
        assertFalse(
            BrowserAutofillOriginPolicy.resolve(
                passwordDomain = "example.com:8443",
                usernameDomain = "example.com"
            )?.usernameMatchesPasswordOrigin == true
        )
    }
}
