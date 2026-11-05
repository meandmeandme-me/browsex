package com.browsex.app.storage

import com.browsex.app.NavigationInput
import com.browsex.app.TabSessionState
import java.util.UUID

/** Room-backed named-session and recently-closed-tab lifecycle. Private tabs are never accepted. */
internal class RoomSessionRepository(
    private val dao: BrowseXStorageDao
) {
    suspend fun sessions(): List<RoomSavedSession> {
        val tabsBySession = dao.savedSessionTabs().groupBy { it.sessionId }
        return dao.savedSessions().mapNotNull { saved ->
            val urls = tabsBySession[saved.id].orEmpty().sortedBy { it.position }.map { it.url }
            if (urls.isEmpty()) null else RoomSavedSession(saved.id, saved.name, urls, saved.activeIndex, saved.savedAt)
        }
    }

    suspend fun saveSession(
        rawName: String,
        urls: List<String>,
        activeIndex: Int,
        savedAt: Long = System.currentTimeMillis()
    ): Boolean {
        val name = rawName.trim().take(MAX_SESSION_NAME)
        if (name.isBlank() || urls.isEmpty() || savedAt < 0L) return false
        val snapshot = TabSessionState.normalize(urls, activeIndex, HOME_URL)
        val session = SavedSessionEntity(
            id = "session_${UUID.randomUUID()}",
            name = name,
            activeIndex = snapshot.activeIndex,
            savedAt = savedAt
        )
        val tabs = snapshot.urls.mapIndexed { index, url ->
            SavedSessionTabEntity(session.id, index, url)
        }
        return dao.saveNamedSession(session, tabs)
    }

    suspend fun deleteSession(id: String): Boolean {
        if (id.isBlank()) return false
        dao.clearTabsForSession(id)
        return dao.deleteSavedSession(id) > 0
    }

    suspend fun recentlyClosed(): List<RecentlyClosedTabEntity> = dao.recentlyClosedTabs().take(MAX_RECENTLY_CLOSED)

    suspend fun recordRecentlyClosed(
        url: String,
        title: String,
        closedAt: Long = System.currentTimeMillis()
    ): Boolean {
        val normalized = url.trim()
        if (!isSafeRestorableUrl(normalized) || normalized == HOME_URL || closedAt < 0L) return false
        val safeTitle = title.trim().take(MAX_RECENT_TITLE)
            .ifBlank { android.net.Uri.parse(normalized).host.orEmpty().ifBlank { normalized } }
        dao.recordRecentlyClosed(RecentlyClosedTabEntity(
            id = "closed_${UUID.randomUUID()}",
            url = normalized,
            title = safeTitle,
            closedAt = closedAt
        ))
        return true
    }

    suspend fun consumeRecentlyClosed(id: String): RecentlyClosedTabEntity? {
        if (id.isBlank()) return null
        val item = dao.recentlyClosedTabs().firstOrNull { it.id == id } ?: return null
        return item.takeIf { dao.deleteRecentlyClosedById(id) > 0 }
    }

    private fun isSafeRestorableUrl(url: String): Boolean =
        url.length <= MAX_URL_LENGTH && (
            url == HOME_URL || url == "about:blank" ||
                NavigationInput.isValidWebUrl(url)
            )

    private companion object {
        const val HOME_URL = "resource://android/assets/home.html"
        const val MAX_URL_LENGTH = 4096
        const val MAX_SESSION_NAME = 40
        const val MAX_RECENT_TITLE = 180
        const val MAX_RECENTLY_CLOSED = 10
    }
}

internal data class RoomSavedSession(
    val id: String,
    val name: String,
    val urls: List<String>,
    val activeIndex: Int,
    val savedAt: Long
)
