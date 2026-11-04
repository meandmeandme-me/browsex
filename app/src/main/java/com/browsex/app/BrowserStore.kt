package com.browsex.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small durable store for browser preferences, bookmarks and recent history. */
class BrowserStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("browsex.browser", Context.MODE_PRIVATE)

    private companion object {
        const val HOME_URL_FOR_STORE = "resource://android/assets/home.html"
        // Bound legacy/corrupt session payloads before the pure session validator processes them.
        const val MAX_RAW_STORED_TABS = 500
        const val MAX_TRACKED_DOWNLOADS = 300
    }

    var adBlockEnabled: Boolean
        get() = prefs.getBoolean("ad_block_enabled", false)
        set(value) { prefs.edit().putBoolean("ad_block_enabled", value).apply() }

    /** Theme preference: system, light or dark. */
    var themeMode: String
        get() = prefs.getString("theme_mode", "system")?.takeIf { it in setOf("system", "light", "dark") } ?: "system"
        set(value) { prefs.edit().putString("theme_mode", value.takeIf { it in setOf("system", "light", "dark") } ?: "system").apply() }

    /** UI language preference: system, ar or en. */
    var languageMode: String
        get() = prefs.getString("language_mode", "system")?.takeIf { it in setOf("system", "ar", "en") } ?: "system"
        set(value) { prefs.edit().putString("language_mode", value.takeIf { it in setOf("system", "ar", "en") } ?: "system").apply() }

    var siteNotificationsEnabled: Boolean
        get() = prefs.getBoolean("site_notifications_enabled", true)
        set(value) { prefs.edit().putBoolean("site_notifications_enabled", value).apply() }

    fun wasPlatformPermissionDenied(permission: String): Boolean =
        prefs.getStringSet("denied_platform_permissions", emptySet()).orEmpty().contains(permission)

    fun markPlatformPermissionDenied(permission: String) {
        val values = prefs.getStringSet("denied_platform_permissions", emptySet()).orEmpty().toMutableSet()
        values.add(permission)
        prefs.edit().putStringSet("denied_platform_permissions", values).apply()
    }

    fun clearPlatformPermissionDenial(permission: String) {
        val values = prefs.getStringSet("denied_platform_permissions", emptySet()).orEmpty().toMutableSet()
        if (values.remove(permission)) prefs.edit().putStringSet("denied_platform_permissions", values).apply()
    }

    fun siteMediaDecision(origin: String, feature: String): String? {
        if (!isSafePermissionOrigin(origin) || feature !in setOf("camera", "microphone")) return null
        return try {
            JSONObject(prefs.getString("site_media_permissions", "{}") ?: "{}")
                .optString("$origin|$feature").takeIf { it == "allow" || it == "deny" }
        } catch (_: Exception) { null }
    }

    fun setSiteMediaDecision(origin: String, feature: String, decision: String) {
        if (!isSafePermissionOrigin(origin) || feature !in setOf("camera", "microphone")) return
        if (decision !in setOf("allow", "deny", "ask")) return
        val key = "$origin|$feature"
        val json = try { JSONObject(prefs.getString("site_media_permissions", "{}") ?: "{}") }
        catch (_: Exception) { JSONObject() }
        if (decision == "ask") json.remove(key) else json.put(key, decision)
        while (json.length() > 100) {
            val oldest = json.keys().asSequence().firstOrNull() ?: break
            json.remove(oldest)
        }
        prefs.edit().putString("site_media_permissions", json.toString()).apply()
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

    private fun isSafePermissionOrigin(origin: String): Boolean = try {
        val uri = android.net.Uri.parse(origin)
        (uri.scheme == "https" || uri.scheme == "http") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }

    var searchEngine: String
        get() = BrowserStorePolicy.normalizeSearchEngine(prefs.getString("search_engine", "Google"))
        set(value) {
            prefs.edit().putString("search_engine", BrowserStorePolicy.normalizeSearchEngine(value)).apply()
        }

    fun saveOpenTabs(urls: List<String>, activeIndex: Int) {
        val snapshot = TabSessionState.normalize(urls, activeIndex, HOME_URL_FOR_STORE)
        writeList("open_tabs", snapshot.urls)
        prefs.edit().putInt("active_tab_index", snapshot.activeIndex).apply()
    }

    fun restoreOpenTabs(): TabSessionState.Snapshot = TabSessionState.normalize(
        readList("open_tabs"),
        prefs.getInt("active_tab_index", 0),
        HOME_URL_FOR_STORE
    )

    /** DownloadManager IDs explicitly enqueued by BrowseX; excludes other apps' downloads. */
    fun browseXDownloadIds(): Set<Long> = try {
        val array = JSONArray(prefs.getString("browsex_download_ids", "[]"))
        val firstIndex = (array.length() - MAX_TRACKED_DOWNLOADS).coerceAtLeast(0)
        buildSet {
            for (index in firstIndex until array.length()) {
                array.optLong(index, -1L).takeIf { it > 0L }?.let(::add)
            }
        }
    } catch (_: Exception) { emptySet() }

    fun addBrowseXDownload(id: Long) {
        if (id <= 0L) return
        val ids = browseXDownloadIds().toMutableList()
        ids.remove(id)
        ids.add(id)
        val array = JSONArray()
        ids.takeLast(MAX_TRACKED_DOWNLOADS).forEach { array.put(it) }
        prefs.edit().putString("browsex_download_ids", array.toString()).apply()
    }

    fun removeBrowseXDownload(id: Long) {
        val array = JSONArray()
        browseXDownloadIds().filterNot { it == id }.forEach { array.put(it) }
        prefs.edit().putString("browsex_download_ids", array.toString()).apply()
    }

    fun bookmarks(): List<String> = readList("bookmarks")
    fun history(): List<String> = readList("history")

    fun isBookmarked(url: String): Boolean = bookmarks().contains(url)

    fun toggleBookmark(url: String): Boolean {
        if (!NavigationInput.isValidWebUrl(url)) return false
        val values = bookmarks().toMutableList()
        val added = if (values.contains(url)) { values.remove(url); false } else { values.add(0, url); true }
        writeList("bookmarks", values)
        return added
    }

    fun addHistory(url: String) {
        if (!NavigationInput.isValidWebUrl(url)) return
        val values = history().toMutableList()
        values.remove(url)
        values.add(0, url)
        writeList("history", values)
    }

    fun removeBookmark(url: String) {
        writeList("bookmarks", bookmarks().filterNot { it == url })
    }

    fun removeHistory(url: String) {
        writeList("history", history().filterNot { it == url })
    }

    fun clearHistory() = prefs.edit().remove("history").apply()

    private fun readList(key: String): List<String> = try {
        val array = JSONArray(prefs.getString(key, "[]"))
        val values = (0 until minOf(array.length(), MAX_RAW_STORED_TABS)).mapNotNull { index ->
            array.optString(index).takeIf(String::isNotBlank)
        }
        // TabSessionState must see the original positions and duplicates so it can restore
        // the correct active tab. Bookmark/history collections intentionally deduplicate.
        if (key == "open_tabs") values
        else BrowserStorePolicy.normalizeUrls(values, limitFor(key))
    } catch (_: Exception) { emptyList() }

    private fun writeList(key: String, values: List<String>) {
        val array = JSONArray()
        if (key == "open_tabs") {
            TabSessionState.normalize(values, 0, HOME_URL_FOR_STORE).urls.forEach { array.put(it) }
        } else {
            BrowserStorePolicy.normalizeUrls(values, limitFor(key)).forEach { array.put(it) }
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun limitFor(key: String): Int = when (key) {
        "bookmarks" -> BrowserStorePolicy.MAX_BOOKMARKS
        "history" -> BrowserStorePolicy.MAX_HISTORY
        else -> TabSessionState.MAX_TABS
    }
}