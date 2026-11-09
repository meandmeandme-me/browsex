package com.browsex.app

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Provider-neutral OIDC Authorization Code + PKCE primitives.
 *
 * This does not launch a browser, exchange a code, validate an ID token, or persist
 * credentials. Provider adapters must still validate issuer, audience, signature,
 * nonce, redirect registration and token response before creating an account session.
 */
internal data class OidcPkceRequest(
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String,
    val nonce: String,
    val codeChallengeMethod: String = "S256"
)

internal data class OidcAuthorizationCodeCallback(
    val authorizationCode: String,
    val state: String
)

internal data class OidcAuthorizationRequestConfig(
    val authorizationEndpoint: String,
    val clientId: String,
    val redirectUri: String,
    val scopes: Set<String>
)

/**
 * Constructs an Authorization Code request without launching a provider or storing secrets.
 * The caller still needs to pin issuer metadata and register the exact redirect URI.
 */
internal object OidcAuthorizationRequestBuilder {
    fun build(
        config: OidcAuthorizationRequestConfig,
        pkce: OidcPkceRequest
    ): String? {
        return runCatching {
        val endpoint = URI(config.authorizationEndpoint)
        if (!endpoint.scheme.equals("https", true) ||
            endpoint.host.isNullOrBlank() ||
            endpoint.rawUserInfo != null ||
            endpoint.rawQuery != null ||
            endpoint.rawFragment != null
        ) return null
        if (config.clientId.isBlank() || config.clientId.length > 512) return null
        if (!isRegisteredRedirect(config.redirectUri)) return null
        if (config.scopes.isEmpty() || config.scopes.any { it.isBlank() || it.any(Char::isWhitespace) }) return null
        if (!config.scopes.contains("openid")) return null
        if (pkce.codeChallengeMethod != "S256" ||
            !isValidVerifier(pkce.codeVerifier) ||
            !isValidOpaqueValue(pkce.state) ||
            !isValidOpaqueValue(pkce.nonce) ||
            !isChallengeForVerifier(pkce.codeVerifier, pkce.codeChallenge)
        ) return null

        val parameters = linkedMapOf(
            "response_type" to "code",
            "client_id" to config.clientId,
            "redirect_uri" to config.redirectUri,
            "scope" to config.scopes.sorted().joinToString(" "),
            "state" to pkce.state,
            "nonce" to pkce.nonce,
            "code_challenge" to pkce.codeChallenge,
            "code_challenge_method" to "S256"
        )
        val query = parameters.entries.joinToString("&") { (key, value) ->
            encodeQuery(key) + "=" + encodeQuery(value)
        }
        val base = endpoint.toASCIIString().substringBefore('#')
        URI("$base?$query").toASCIIString()
        }.getOrNull()
    }

    private fun isRegisteredRedirect(value: String): Boolean {
        return runCatching {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase() ?: return false
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return false
        when (scheme) {
            "https" -> !uri.host.isNullOrBlank()
            // Native-app redirects may use a private URI scheme, but not browser or file destinations.
            "http", "file", "content", "javascript", "intent" -> false
            else -> scheme.matches(Regex("^[a-z][a-z0-9+.-]{2,63}$")) &&
                uri.rawAuthority == null &&
                !uri.rawPath.isNullOrBlank() &&
                uri.rawPath.startsWith("/")
        }
        }.getOrDefault(false)
    }

    private fun isValidVerifier(value: String): Boolean =
        value.length in 43..128 &&
            value.all {
                it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-._~"
            }

    private fun isValidOpaqueValue(value: String): Boolean =
        value.length in 16..512 && value.all { it.code in 0x21..0x7E }

    private fun isChallengeForVerifier(verifier: String, challenge: String): Boolean {
        if (challenge.length != 43 ||
            challenge.any { !(it.isLetterOrDigit() || it == '-' || it == '_') }
        ) return false
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        )
        return MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.US_ASCII),
            challenge.toByteArray(StandardCharsets.US_ASCII)
        )
    }

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%7E", "~")
}

internal object OidcPkce {
    private const val RANDOM_BYTES = 32
    private const val CHALLENGE_METHOD = "S256"

    fun create(random: SecureRandom = SecureRandom()): OidcPkceRequest {
        val verifier = randomUrlToken(random)
        val state = randomUrlToken(random)
        val nonce = randomUrlToken(random)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        )
        check(verifier.length in 43..128)
        return OidcPkceRequest(
            codeVerifier = verifier,
            codeChallenge = challenge,
            state = state,
            nonce = nonce
        )
    }

    /**
     * Accepts only an authorization-code callback for the exact registered redirect.
     * Implicit-flow tokens, fragments, duplicate parameters, errors and state mismatch
     * are rejected. Token/ID-token validation must happen after the code exchange.
     */
    fun parseAuthorizationCodeCallback(
        expectedRedirectUri: String,
        callbackUri: String,
        expectedState: String
    ): OidcAuthorizationCodeCallback? {
        if (expectedState.isBlank()) return null
        return runCatching {
            val expected = URI(expectedRedirectUri)
            val actual = URI(callbackUri)
            if (!isSafeRedirectBase(expected) || !sameRedirectBase(expected, actual)) return null
            if (actual.rawFragment != null || actual.rawUserInfo != null) return null

            val parameters = parseQuery(actual.rawQuery ?: return null) ?: return null
            if (parameters.containsKey("error") ||
                parameters.containsKey("access_token") ||
                parameters.containsKey("id_token") ||
                parameters.containsKey("token_type")
            ) return null

            val returnedState = parameters["state"] ?: return null
            val code = parameters["code"]?.takeIf {
                it.isNotBlank() && it.length <= 8192 && it.all { char -> char.code in 0x21..0x7E }
            } ?: return null
            if (!constantTimeEquals(returnedState, expectedState)) return null
            OidcAuthorizationCodeCallback(code, returnedState)
        }.getOrNull()
    }

    private fun randomUrlToken(random: SecureRandom): String {
        val bytes = ByteArray(RANDOM_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun isSafeRedirectBase(uri: URI): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return false
        return when (scheme) {
            "https" -> !uri.host.isNullOrBlank() && uri.port != 0 && uri.port <= 65535
            "http", "file", "content", "javascript", "intent" -> false
            else -> scheme.matches(Regex("^[a-z][a-z0-9+.-]{2,63}$")) &&
                uri.rawAuthority == null &&
                !uri.rawPath.isNullOrBlank() &&
                uri.rawPath.startsWith("/")
        }
    }

    private fun sameRedirectBase(expected: URI, actual: URI): Boolean =
        expected.scheme.equals(actual.scheme, ignoreCase = true) &&
            expected.rawAuthority == actual.rawAuthority &&
            expected.rawPath == actual.rawPath

    private fun parseQuery(raw: String): Map<String, String>? {
        if (raw.length > 16_384) return null
        val values = linkedMapOf<String, String>()
        for (part in raw.split('&')) {
            if (part.isEmpty()) continue
            val separator = part.indexOf('=')
            if (separator <= 0) return null
            val key = decodeQueryComponent(part.substring(0, separator)) ?: return null
            val value = decodeQueryComponent(part.substring(separator + 1)) ?: return null
            if (key.isBlank() || values.containsKey(key)) return null
            values[key] = value
        }
        return values
    }

    private fun decodeQueryComponent(raw: String): String? = runCatching {
        // OAuth values may contain '+'. Treat it literally rather than as form-space.
        URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrNull()

    private fun constantTimeEquals(first: String, second: String): Boolean =
        MessageDigest.isEqual(
            first.toByteArray(StandardCharsets.UTF_8),
            second.toByteArray(StandardCharsets.UTF_8)
        )
}
