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

    /**
     * Accepts Unicode domain names while rejecting credentials, malformed authorities and
     * invalid ports. The original URL is retained; Gecko handles IDN navigation/display.
     */
    fun isValidWebUrl(value: String): Boolean {
        return try {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        if (uri.rawUserInfo != null) return false
        val authority = uri.rawAuthority ?: return false
        if (authority.isBlank() || authority.any { it.isWhitespace() || it == '%' || it == '\\' }) return false

        val host = if (authority.startsWith("[")) {
            val closing = authority.indexOf(']')
            if (closing <= 1) return false
            val suffix = authority.substring(closing + 1)
            if (suffix.isNotEmpty()) {
                if (!suffix.startsWith(":")) return false
                val port = suffix.substring(1).toIntOrNull() ?: return false
                if (port !in 1..65535) return false
            }
            authority.substring(0, closing + 1)
        } else {
            val colon = authority.lastIndexOf(':')
            if (colon >= 0) {
                if (authority.indexOf(':') != colon) return false
                val port = authority.substring(colon + 1).toIntOrNull() ?: return false
                if (port !in 1..65535) return false
                authority.substring(0, colon)
            } else authority
        }
        if (host.isBlank()) return false
        if (host.startsWith("[") && host.endsWith("]")) {
            host.length > 2 && host.substring(1, host.length - 1).contains(':')
        } else {
            val ascii = java.net.IDN.toASCII(host, java.net.IDN.USE_STD3_ASCII_RULES)
            val labels = ascii.trimEnd('.').split('.')
            ascii.length <= 254 && labels.isNotEmpty() &&
                labels.all { it.isNotBlank() && it.length <= 63 }
        }
        } catch (_: Exception) {
            false
        }
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
