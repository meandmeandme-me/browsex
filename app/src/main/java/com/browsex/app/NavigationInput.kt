package com.browsex.app

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Pure URL/search input policy. Only web URLs and the app's own home page may be
 * navigated directly; arbitrary schemes are treated as search text.
 */
internal object NavigationInput {
    private const val GOOGLE = "https://www.google.com/search?q="
    private const val DUCK_DUCK_GO = "https://html.duckduckgo.com/html/?q="
    private const val BING = "https://www.bing.com/search?q="

    fun normalize(raw: String, searchEngine: String, homeUrl: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return homeUrl
        if (value == homeUrl || value == "about:blank") return value

        val explicitScheme = SCHEME.matchAt(value, 0)
        if (explicitScheme != null) {
            val scheme = explicitScheme.value.removeSuffix("://").lowercase()
            if (scheme == "http" || scheme == "https") {
                return value.takeIf(::isValidWebUrl) ?: search(value, searchEngine)
            }
            return search(value, searchEngine)
        }

        if (looksLikeHost(value)) {
            val candidate = "https://$value"
            if (isValidWebUrl(candidate)) return candidate
        }
        return search(value, searchEngine)
    }

    fun externalWebUrl(raw: String): String? = raw.trim().takeIf(::isValidWebUrl)

    fun isValidWebUrl(value: String): Boolean = try {
        val uri = URI(value)
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    } catch (_: Exception) {
        false
    }

    private fun looksLikeHost(value: String): Boolean =
        !value.any(Char::isWhitespace) &&
            (value.equals("localhost", true) || value.contains('.') || value.startsWith("192.168."))

    private fun search(value: String, engine: String): String {
        val base = when (engine) {
            "DuckDuckGo" -> DUCK_DUCK_GO
            "Bing" -> BING
            else -> GOOGLE
        }
        val encoded = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return base + encoded
    }

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
}
