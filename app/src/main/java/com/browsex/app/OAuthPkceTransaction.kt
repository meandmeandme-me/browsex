package com.browsex.app

import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * One-use, short-lived OAuth 2.0 Authorization Code + PKCE transaction.
 *
 * This is deliberately provider- and UI-neutral. It creates the PKCE material
 * and validates the exact app redirect before an authorization code can be
 * handed to a provider adapter. It does not exchange codes, store tokens, or
 * make sign-in available until provider client configuration and adapters exist.
 */
internal data class OAuthPkceTransaction private constructor(
    val provider: IdentityProvider,
    val redirectUri: String,
    val state: String,
    val codeVerifier: String,
    val codeChallenge: String,
    val createdAtEpochMillis: Long
) {
    fun validateCallback(
        callbackUri: String,
        nowEpochMillis: Long,
        maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS
    ): String? {
        if (maxAgeMillis <= 0L || nowEpochMillis < createdAtEpochMillis ||
            nowEpochMillis - createdAtEpochMillis > maxAgeMillis
        ) return null

        val expected = parseRedirect(redirectUri) ?: return null
        val actual = parseRedirect(callbackUri) ?: return null
        if (!sameRedirectTarget(expected, actual)) return null

        val params = parseQuery(actual.rawQuery ?: return null) ?: return null
        val returnedState = params["state"] ?: return null
        if (!constantTimeEquals(state, returnedState)) return null
        if (params.containsKey("error")) return null
        return params["code"]?.takeIf { it.isNotBlank() && it.length <= MAX_CODE_LENGTH }
    }

    companion object {
        const val DEFAULT_MAX_AGE_MILLIS = 10 * 60 * 1000L
        private const val MAX_CODE_LENGTH = 4096
        private val random = SecureRandom()

        fun create(
            provider: IdentityProvider,
            redirectUri: String,
            nowEpochMillis: Long
        ): OAuthPkceTransaction {
            require(nowEpochMillis >= 0L)
            require(parseRedirect(redirectUri) != null) { "OAuth redirect URI must be a valid app callback URI" }

            val verifier = randomUrlSafe(32)
            val state = randomUrlSafe(32)
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            )
            return OAuthPkceTransaction(
                provider = provider,
                redirectUri = redirectUri,
                state = state,
                codeVerifier = verifier,
                codeChallenge = challenge,
                createdAtEpochMillis = nowEpochMillis
            )
        }

        private fun randomUrlSafe(byteCount: Int): String {
            val bytes = ByteArray(byteCount)
            random.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        private fun parseRedirect(raw: String): URI? {
            return try {
                val uri = URI(raw)
                val scheme = uri.scheme ?: return null
                val isHttps = scheme.equals("https", ignoreCase = true)
                val isAppScheme = !scheme.equals("http", ignoreCase = true) &&
                    scheme.matches(Regex("[A-Za-z][A-Za-z0-9+.-]{2,63}"))
                if (!isHttps && !isAppScheme) return null
                if ((isHttps && uri.host.isNullOrBlank()) || uri.rawUserInfo != null ||
                    uri.rawFragment != null || uri.rawQuery != null || uri.rawPath.isNullOrBlank()
                ) return null
                uri
            } catch (_: Exception) { null }
        }

        private fun sameRedirectTarget(expected: URI, actual: URI): Boolean =
            expected.scheme.equals(actual.scheme, ignoreCase = true) &&
                expected.rawAuthority.equals(actual.rawAuthority, ignoreCase = true) &&
                expected.rawPath == actual.rawPath

        private fun parseQuery(raw: String): Map<String, String>? {
            val values = linkedMapOf<String, String>()
            for (part in raw.split('&')) {
                if (part.isBlank()) continue
                val pieces = part.split('=', limit = 2)
                val key = decodeQueryPart(pieces[0]) ?: return null
                val value = decodeQueryPart(pieces.getOrElse(1) { "" }) ?: return null
                if (key.isBlank() || values.put(key, value) != null) return null
            }
            return values
        }

        private fun decodeQueryPart(value: String): String? = try {
            java.net.URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (_: Exception) { null }

        private fun constantTimeEquals(expected: String, actual: String): Boolean =
            MessageDigest.isEqual(
                expected.toByteArray(Charsets.UTF_8),
                actual.toByteArray(Charsets.UTF_8)
            )
    }
}
