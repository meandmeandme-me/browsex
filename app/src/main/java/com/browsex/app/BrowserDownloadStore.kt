package com.browsex.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Owns BrowseX download metadata, ID tracking and collision-safe filename reservations. */
internal class BrowserDownloadStore(private val prefs: SharedPreferences) {
    private companion object {
        const val MAX_TRACKED_DOWNLOADS = 300
    }

    @Synchronized
    fun reserveDownloadFileName(candidate: String): String {
        val names = try {
            val array = JSONArray(prefs.getString("browsex_download_names", "[]"))
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toMutableSet()
        } catch (_: Exception) { mutableSetOf() }
        val dot = candidate.lastIndexOf('.').takeIf { it > 0 } ?: candidate.length
        val stem = candidate.substring(0, dot)
        val extension = candidate.substring(dot)
        var result = candidate
        var suffix = 1
        while (names.any { it.equals(result, ignoreCase = true) }) {
            result = "$stem ($suffix)$extension"
            suffix++
        }
        names.add(result)
        val array = JSONArray()
        names.toList().takeLast(MAX_TRACKED_DOWNLOADS).forEach { array.put(it) }
        prefs.edit().putString("browsex_download_names", array.toString()).apply()
        return result
    }
}
