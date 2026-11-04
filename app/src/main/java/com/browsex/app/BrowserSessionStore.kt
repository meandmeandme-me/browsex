package com.browsex.app

import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Owns durable regular-tab snapshots, named sessions and recently-closed entries. */
internal class BrowserSessionStore(private val prefs: SharedPreferences) {
    fun saveOpenTabs(urls: List<String>, activeIndex: Int) {
        val snapshot = TabSessionState.normalize(urls, activeIndex, HOME_URL)
        val array = JSONArray()
        snapshot.urls.forEach { array.put(it) }
        prefs.edit().putString("open_tabs", array.toString())
            .putInt("active_tab_index", snapshot.activeIndex).apply()
    }

    fun restoreOpenTabs(): TabSessionState.Snapshot = TabSessionState.normalize(
        readOpenTabs(),
        prefs.getInt("active_tab_index", 0),
        HOME_URL
    )

    /** Saves a named, regular-tab-only session. Private tabs must never be passed here. */
    fun saveNamedSession(rawName: String, urls: List<String>, activeIndex: Int): Boolean {
        val name = rawName.trim().take(40)
        if (name.isBlank() || urls.isEmpty()) return false
        val snapshot = TabSessionState.normalize(urls, activeIndex, HOME_URL)
        val sessions = savedSessions().toMutableList()
        val existingIndex = sessions.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (existingIndex < 0 && sessions.size >= MAX_NAMED_SESSIONS) return false
        val previous = sessions.getOrNull(existingIndex)
        val saved = SavedBrowserSession(
            id = previous?.id ?: "session_${UUID.randomUUID()}",
            name = name,
            urls = snapshot.urls,
            activeIndex = snapshot.activeIndex,
            savedAt = System.currentTimeMillis()
        )
        if (existingIndex >= 0) sessions[existingIndex] = saved else sessions.add(0, saved)
        writeNamedSessions(sessions)
        return true
    }

    fun savedSessions(): List<SavedBrowserSession> = try {
        val stored = prefs.getString("named_sessions_v1", "[]") ?: "[]"
        if (stored.length > MAX_NAMED_SESSIONS_JSON_LENGTH) emptyList()
        else {
            val array = JSONArray(stored)
            (0 until minOf(array.length(), MAX_NAMED_SESSIONS)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").take(100)
                val name = item.optString("name").trim().take(40)
                val rawUrls = item.optJSONArray("urls") ?: JSONArray()
                val urls = (0 until minOf(rawUrls.length(), TabSessionState.MAX_TABS))
                    .mapNotNull { rawUrls.optString(it).takeIf(String::isNotBlank) }
                if (id.isBlank() || name.isBlank() || urls.isEmpty()) return@mapNotNull null
                val snapshot = TabSessionState.normalize(urls, item.optInt("activeIndex", 0), HOME_URL)
                SavedBrowserSession(id, name, snapshot.urls, snapshot.activeIndex,
                    item.optLong("savedAt", 0L).coerceAtLeast(0L))
            }
        }
    } catch (_: Exception) { emptyList() }

    fun removeNamedSession(id: String): Boolean {
        val sessions = savedSessions()
        val updated = sessions.filterNot { it.id == id }
        if (updated.size == sessions.size) return false
        writeNamedSessions(updated)
        return true
    }

    fun recordRecentlyClosedTab(url: String, title: String) {
        if (!NavigationInput.isValidWebUrl(url) || url == HOME_URL) return
        val tabs = recentlyClosedTabs().toMutableList()
        tabs.removeAll { it.url == url }
        tabs.add(0, RecentlyClosedTab(
            id = "closed_${UUID.randomUUID()}",
            url = url.take(2048),
            title = title.trim().take(180).ifBlank { Uri.parse(url).host.orEmpty() },
            closedAt = System.currentTimeMillis()
        ))
        writeRecentlyClosedTabs(tabs)
    }

    fun recentlyClosedTabs(): List<RecentlyClosedTab> = try {
        val stored = prefs.getString("recently_closed_tabs_v1", "[]") ?: "[]"
        if (stored.length > MAX_RECENTLY_CLOSED_JSON_LENGTH) emptyList() else {
            val array = JSONArray(stored)
            (0 until minOf(array.length(), MAX_RECENTLY_CLOSED)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").take(100)
                val url = item.optString("url").take(2048)
                if (id.isBlank() || !NavigationInput.isValidWebUrl(url)) return@mapNotNull null
                RecentlyClosedTab(
                    id, url,
                    item.optString("title").take(180).ifBlank { Uri.parse(url).host.orEmpty() },
                    item.optLong("closedAt", 0L).coerceAtLeast(0L)
                )
            }
        }
    } catch (_: Exception) { emptyList() }

    fun consumeRecentlyClosedTab(id: String): RecentlyClosedTab? {
        val tabs = recentlyClosedTabs()
        val selected = tabs.firstOrNull { it.id == id } ?: return null
        writeRecentlyClosedTabs(tabs.filterNot { it.id == id })
        return selected
    }

    private fun readOpenTabs(): List<String> = try {
        val array = JSONArray(prefs.getString("open_tabs", "[]"))
        (0 until minOf(array.length(), MAX_RAW_STORED_TABS))
            .mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    } catch (_: Exception) { emptyList() }

    private fun writeNamedSessions(sessions: List<SavedBrowserSession>) {
        val array = JSONArray()
        sessions.take(MAX_NAMED_SESSIONS).forEach { session ->
            val snapshot = TabSessionState.normalize(session.urls, session.activeIndex, HOME_URL)
            val urls = JSONArray()
            snapshot.urls.forEach { urls.put(it) }
            array.put(JSONObject()
                .put("id", session.id.take(100))
                .put("name", session.name.trim().take(40))
                .put("urls", urls)
                .put("activeIndex", snapshot.activeIndex)
                .put("savedAt", session.savedAt.coerceAtLeast(0L)))
        }
        prefs.edit().putString("named_sessions_v1", array.toString()).apply()
    }

    private fun writeRecentlyClosedTabs(tabs: List<RecentlyClosedTab>) {
        val array = JSONArray()
        tabs.take(MAX_RECENTLY_CLOSED).forEach { tab ->
            array.put(JSONObject()
                .put("id", tab.id)
                .put("url", tab.url)
                .put("title", tab.title)
                .put("closedAt", tab.closedAt))
        }
        prefs.edit().putString("recently_closed_tabs_v1", array.toString()).apply()
    }

    private companion object {
        const val HOME_URL = "resource://android/assets/home.html"
        const val MAX_RAW_STORED_TABS = 500
        const val MAX_NAMED_SESSIONS = 10
        const val MAX_NAMED_SESSIONS_JSON_LENGTH = 1_000_000
        const val MAX_RECENTLY_CLOSED = 10
        const val MAX_RECENTLY_CLOSED_JSON_LENGTH = 100_000
    }
}
