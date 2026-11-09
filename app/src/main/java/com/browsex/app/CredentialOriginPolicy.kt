package com.browsex.app

import java.net.URI

internal object CredentialOriginPolicy {
    fun canonicalHttpsOrigin(raw: String): String? {
        return try {
            val uri = URI(raw)
            if (!uri.scheme.equals("https", true) || uri.isOpaque || uri.host.isNullOrBlank() ||
                uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
                (uri.rawPath.isNotEmpty() && uri.rawPath != "/") ||
                uri.port == 0 || uri.port > 65535 || uri.rawAuthority?.endsWith(":") == true) return null
            val host = java.net.IDN.toASCII(uri.host.lowercase())
            val port = if (uri.port == -1 || uri.port == 443) "" else ":" + uri.port
            "https://" + host + port
        } catch (_: Exception) { null }
    }

    fun matchesExactOrigin(savedOrigin: String, requestedDomain: String): Boolean {
        val saved = canonicalHttpsOrigin(savedOrigin) ?: return false
        val candidate = if (requestedDomain.contains("://")) requestedDomain else "https://" + requestedDomain
        return saved == canonicalHttpsOrigin(candidate)
    }
}
