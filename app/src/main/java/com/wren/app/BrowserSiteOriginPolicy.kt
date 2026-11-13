package com.wren.app

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale

/**
 * Produces a canonical web origin for site permission decisions.
 *
 * Paths, queries and fragments are intentionally discarded. Credentials are rejected
 * rather than copied into permission keys or user-facing origin labels. Unicode DNS
 * names are converted to their ASCII IDNA form so permissions remain origin-stable.
 */
internal object BrowserSiteOriginPolicy {
    fun canonicalOrigin(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.isOpaque || uri.rawUserInfo != null) return null

        val authority = uri.rawAuthority?.takeIf { it.isNotBlank() } ?: return null
        if ('@' in authority) return null

        val rawHost: String
        val rawPort: String?
        if (authority.startsWith("[")) {
            val closingBracket = authority.indexOf(']')
            if (closingBracket <= 1) return null
            rawHost = authority.substring(0, closingBracket + 1)
            val suffix = authority.substring(closingBracket + 1)
            rawPort = when {
                suffix.isEmpty() -> null
                suffix.startsWith(":") && suffix.length > 1 -> suffix.substring(1)
                else -> return null
            }
            // Validate the literal as an actual IPv6 address; character filtering alone accepts
            // malformed values such as "::::". The colon requirement keeps this on the
            // numeric-literal parser path rather than allowing hostname resolution.
            val ipv6Literal = rawHost.substring(1, rawHost.length - 1)
            if (':' !in ipv6Literal || !ipv6Literal.matches(Regex("[0-9A-Fa-f:.]+"))) return null
            val parsedAddress = runCatching { InetAddress.getByName(ipv6Literal) }.getOrNull()
            if (parsedAddress !is Inet6Address) return null
        } else {
            val colon = authority.lastIndexOf(':')
            if (colon >= 0 && authority.indexOf(':') != colon) return null
            rawHost = if (colon >= 0) authority.substring(0, colon) else authority
            rawPort = if (colon >= 0) authority.substring(colon + 1) else null
        }
        if (rawHost.isBlank()) return null

        val host = if (rawHost.startsWith("[")) {
            rawHost.lowercase(Locale.ROOT)
        } else {
            runCatching { IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT) }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        }

        val port = rawPort?.toIntOrNull()
        if (rawPort != null && (port == null || port !in 1..65535)) return null
        val normalizedPort = when {
            port == null -> null
            scheme == "http" && port == 80 -> null
            scheme == "https" && port == 443 -> null
            else -> port
        }

        return buildString {
            append(scheme)
            append("://")
            append(host)
            normalizedPort?.let {
                append(':')
                append(it)
            }
        }
    }
}
