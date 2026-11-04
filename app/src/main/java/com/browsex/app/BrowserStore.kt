package com.browsex.app

import android.content.Context
import org.json.JSONArray

/** Small durable store for browser preferences, bookmarks and recent history. */
class BrowserStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("browsex.browser", Context.MODE_PRIVATE)

    private companion object {
        const val HOME_URL_FOR_STORE = "resource://android/assets/home.html"
    }

    var adBlockEnabled: Boolean
        get() = prefs.getBoolean("ad_block_enabled", false)
        set(value) { prefs.edit().putBoolean("ad_block_enabled", value).apply() }

    var searchEngine: String
        get() = prefs.getString("search_engine", "Google") ?: "Google"
        set(value) { prefs.edit().putString("search_engine", value).apply() }

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

    fun bookmarks(): List<String> = readList("bookmarks")
    fun history(): List<String> = readList("history")

    fun isBookmarked(url: String): Boolean = bookmarks().contains(url)

    fun toggleBookmark(url: String): Boolean {
        val values = bookmarks().toMutableList()
        val added = if (values.contains(url)) { values.remove(url); false } else { values.add(0, url); true }
        writeList("bookmarks", values.take(200))
        return added
    }

    fun addHistory(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val values = history().toMutableList()
        values.remove(url)
        values.add(0, url)
        writeList("history", values.take(300))
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
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    } catch (_: Exception) { emptyList() }

    private fun writeList(key: String, values: List<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
