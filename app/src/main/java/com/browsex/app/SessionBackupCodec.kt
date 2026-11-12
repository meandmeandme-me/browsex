package com.browsex.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned, privacy-safe export format for one regular browsing session.
 * Never include private tabs, credentials, cookies, permissions, or browsing history.
 */
internal object SessionBackupCodec {
    private const val FORMAT = "wren-session"
    private const val VERSION = 1

    data class Snapshot(
        val name: String,
        val urls: List<String>,
        val activeIndex: Int
    )

    fun encode(name: String, urls: List<String>, activeIndex: Int): String {
        require(name.isNotBlank()) { "Session name is required" }
        require(urls.isNotEmpty() && urls.size <= TabSessionState.MAX_TABS) {
            "Invalid session tab count"
        }
        require(urls.all(::isSafeWebUrl)) { "Session contains an invalid website URL" }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("name", name)
            .put("activeIndex", activeIndex.coerceIn(0, urls.lastIndex))
            .put("urls", JSONArray(urls))
            .toString(2)
    }

    /** Reads Wren JSON backups or the legacy newline-separated URL format. */
    fun decode(rawText: String, fallbackName: String = ""): Snapshot {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "Session backup is empty" }
        return if (trimmed.startsWith("{")) {
            val backup = JSONObject(trimmed)
            require(backup.optString("format") == FORMAT) { "Unsupported session backup format" }
            require(backup.optInt("version", -1) == VERSION) { "Unsupported session backup version" }
            val name = backup.optString("name").ifBlank { fallbackName }
            require(name.isNotBlank()) { "Session backup has no name" }
            val array = backup.optJSONArray("urls") ?: error("Session backup has no URL list")
            require(array.length() in 1..TabSessionState.MAX_TABS) { "Invalid session tab count" }
            val urls = (0 until array.length()).map { index ->
                val url = array.optString(index).trim()
                require(isSafeWebUrl(url)) { "Invalid website URL in backup" }
                url
            }
            // Keep duplicates: identical URLs can represent distinct tabs and positions.
            val activeIndex = backup.optInt("activeIndex", 0).coerceIn(0, urls.lastIndex)
            Snapshot(name, urls, activeIndex)
        } else {
            val urls = trimmed.lineSequence()
                .map { it.trim().replace(Regex("^\\\\d+[.)]\\\\s*"), "") }
                .filter(::isSafeWebUrl)
                .distinct()
                .take(TabSessionState.MAX_TABS)
                .toList()
            require(urls.isNotEmpty()) { "No valid website URLs found" }
            Snapshot(fallbackName, urls, 0)
        }
    }

    private fun isSafeWebUrl(url: String): Boolean =
        url.length <= 4096 &&
            (url.startsWith("https://", true) || url.startsWith("http://", true)) &&
            NavigationInput.isValidWebUrl(url)
}
