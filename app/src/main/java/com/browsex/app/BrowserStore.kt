package com.browsex.app

import android.content.Context
import org.json.JSONArray

/** Small durable store for browser preferences, bookmarks and recent history. */
class BrowserStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("browsex.browser", Context.MODE_PRIVATE)

    private companion object {
        const val HOME_URL_FOR_STORE = "resource://android/assets/home.html"
        // Bound legacy/corrupt session payloads before the pure session validator processes them.
        const val MAX_RAW_STORED_TABS = 500
    }

    var adBlockEnabled: Boolean
        get() = prefs.getBoolean("ad_block_enabled", false)
        set(value) { prefs.edit().putBoolean("ad_block_enabled", value).apply() }

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
        (0 until array.length()).mapNotNull { index ->
            array.optLong(index, -1L).takeIf { it > 0L }
        }.toSet()
    } catch (_: Exception) { emptySet() }

    fun addBrowseXDownload(id: Long) {
        if (id <= 0L) return
        val ids = browseXDownloadIds().toMutableList()
        ids.remove(id)
        ids.add(id)
        val array = JSONArray()
        ids.takeLast(300).forEach { array.put(it) }
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
