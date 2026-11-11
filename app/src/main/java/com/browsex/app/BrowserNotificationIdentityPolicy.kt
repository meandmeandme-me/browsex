package com.browsex.app

import java.security.MessageDigest

/** Builds collision-resistant Android notification tags with Web Notifications API semantics. */
internal object BrowserNotificationIdentityPolicy {
    fun androidTag(origin: String, webTag: String?, uniqueId: String): String {
        require(origin.isNotBlank() && origin.length <= 4096)
        require(uniqueId.isNotBlank() && uniqueId.length <= 128)
        val normalizedWebTag = webTag?.takeIf { it.isNotBlank() }
            ?: return "browsex-web-$uniqueId"
        val source = "$origin|$normalizedWebTag".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(source)
        return "browsex-web-" + digest.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
