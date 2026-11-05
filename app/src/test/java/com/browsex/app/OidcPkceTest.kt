package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OidcPkceTest {
    @Test
    fun createsIndependentVerifierChallengeStateAndNonce() {
        val request = OidcPkce.create()

        assertTrue(request.codeVerifier.length in 43..128)
        assertTrue(request.codeVerifier.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertTrue(request.codeChallenge.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        assertEquals("S256", request.codeChallengeMethod)
        assertTrue(request.state.length >= 40)
        assertTrue(request.nonce.length >= 40)
        assertNotEquals(request.codeVerifier, request.codeChallenge)
        assertNotEquals(request.state, request.nonce)
        assertNotEquals(request.state, request.codeVerifier)
    }

    @Test
    fun acceptsOnlyExactRegisteredRedirectAndExpectedState() {
        val request = OidcPkce.create()
        val callback = "com.browsex.app:/oauth2redirect?code=opaque-code&state=${request.state}"

        val parsed = OidcPkce.parseAuthorizationCodeCallback(
            expectedRedirectUri = "com.browsex.app:/oauth2redirect",
            callbackUri = callback,
            expectedState = request.state
        )

        assertEquals("opaque-code", parsed?.authorizationCode)
        assertEquals(request.state, parsed?.state)
    }

    @Test
    fun acceptsHttpsRedirectButRejectsWrongHostPathAndScheme() {
        val state = "safe-state"
        val expected = "https://auth.example.test/callback"
        val valid = OidcPkce.parseAuthorizationCodeCallback(
            expected, "https://auth.example.test/callback?code=abc&state=$state", state
        )
        assertEquals("abc", valid?.authorizationCode)

        assertNull(OidcPkce.parseAuthorizationCodeCallback(
            expected, "https://evil.example.test/callback?code=abc&state=$state", state
        ))
        assertNull(OidcPkce.parseAuthorizationCodeCallback(
            expected, "https://auth.example.test/other?code=abc&state=$state", state
        ))
        assertNull(OidcPkce.parseAuthorizationCodeCallback(
            expected, "com.browsex.app:/callback?code=abc&state=$state", state
        ))
    }

    @Test
    fun rejectsStateMismatchDuplicateParametersErrorsAndImplicitTokens() {
        val expected = "https://auth.example.test/callback"
        val state = "expected-state"
        val invalid = listOf(
            "https://auth.example.test/callback?code=abc&state=wrong",
            "https://auth.example.test/callback?code=abc&state=$state&state=$state",
            "https://auth.example.test/callback?code=abc&state=$state&error=access_denied",
            "https://auth.example.test/callback?access_token=secret&state=$state",
            "https://auth.example.test/callback?code=abc&state=$state#id_token=secret",
            "https://auth.example.test/callback?code=abc"
        )

        invalid.forEach { callback ->
            assertNull(OidcPkce.parseAuthorizationCodeCallback(expected, callback, state))
        }
    }

    @Test
    fun rejectsUnsafeOrAmbiguousRedirectInputs() {
        val state = "safe"
        val invalid = listOf(
            "http://auth.example.test/callback?code=abc&state=$state",
            "https://user@auth.example.test/callback?code=abc&state=$state",
            "https://auth.example.test/callback?code=abc&code=second&state=$state",
            "https://auth.example.test/callback?code=&state=$state",
            "https://auth.example.test/callback?code=abc%ZZ&state=$state"
        )

        invalid.forEach { callback ->
            assertNull(OidcPkce.parseAuthorizationCodeCallback(
                "https://auth.example.test/callback", callback, state
            ))
        }
        assertFalse(
            OidcPkce.parseAuthorizationCodeCallback(
                "https://auth.example.test/callback?fixed=1",
                "https://auth.example.test/callback?fixed=1&code=abc&state=$state",
                state
            ) != null
        )
    }

    @Test
    fun authorizationRequestUsesCodeFlowExactRedirectAndS256() {
        val pkce = OidcPkce.create()
        val config = OidcAuthorizationRequestConfig(
            authorizationEndpoint = "https://login.example.test/authorize",
            clientId = "browsex-client",
            redirectUri = "com.browsex.app:/oauth2redirect",
            scopes = setOf("profile", "openid", "email")
        )

        val request = OidcAuthorizationRequestBuilder.build(config, pkce)

        assertNotNull(request)
        assertTrue(request!!.startsWith("https://login.example.test/authorize?"))
        assertTrue(request.contains("response_type=code"))
        assertTrue(request.contains("code_challenge_method=S256"))
        assertTrue(request.contains("client_id=browsex-client"))
        assertTrue(request.contains("redirect_uri=com.browsex.app%3A%2Foauth2redirect"))
        assertTrue(request.contains("state=" + pkce.state))
        assertTrue(request.contains("nonce=" + pkce.nonce))
        assertTrue(request.contains("scope=email%20openid%20profile"))
        assertFalse(request.contains("code_verifier"))
    }

    @Test
    fun authorizationRequestRejectsInsecureEndpointsAndMissingOpenIdScope() {
        val pkce = OidcPkce.create()
        val base = OidcAuthorizationRequestConfig(
            authorizationEndpoint = "http://login.example.test/authorize",
            clientId = "client",
            redirectUri = "com.browsex.app:/oauth2redirect",
            scopes = setOf("openid", "profile")
        )
        assertNull(OidcAuthorizationRequestBuilder.build(base, pkce))
        assertNull(OidcAuthorizationRequestBuilder.build(
            base.copy(
                authorizationEndpoint = "https://login.example.test/authorize",
                scopes = setOf("profile", "email")
            ),
            pkce
        ))
        assertNull(OidcAuthorizationRequestBuilder.build(
            base.copy(
                authorizationEndpoint = "https://user@login.example.test/authorize"
            ),
            pkce
        ))
    }

    @Test
    fun authorizationRequestRejectsRedirectUriWithUntrustedQueryOrFragment() {
        val pkce = OidcPkce.create()
        val config = OidcAuthorizationRequestConfig(
            authorizationEndpoint = "https://login.example.test/authorize",
            clientId = "client",
            redirectUri = "https://app.example.test/callback?next=https://evil.test",
            scopes = setOf("openid")
        )
        assertNull(OidcAuthorizationRequestBuilder.build(config, pkce))
        assertNull(OidcAuthorizationRequestBuilder.build(
            config.copy(redirectUri = "https://app.example.test/callback#token"),
            pkce
        ))
    }
}
