package com.browsex.app

import java.net.URI
import java.util.Locale

/**
 * Produces a canonical web origin for site permission decisions.
 *
 * Paths, queries and fragments are intentionally discarded. Credentials are rejected
 * rather than copied into permission keys or user-facing origin labels.
 */
internal object BrowserSiteOriginPolicy {
    fun canonicalOrigin(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.isOpaque || uri.rawUserInfo != null) return null
        val host = uri.host?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT) ?: return null
        val port = uri.port
        if (port !in -1..65535) return null
        val normalizedPort = when {
            port == -1 -> null
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
