package com.browsex.app.storage

import com.browsex.app.NavigationInput
import com.browsex.app.TabSessionState
import java.util.UUID

/** Room-backed named-session and recently-closed-tab lifecycle. Callers must pass regular tabs only. */
internal class RoomSessionRepository(
    private val dao: BrowseXStorageDao
) {
    suspend fun sessions(): List<RoomSavedSession> {
        return dao.savedSessions(MAX_SAVED_SESSIONS).mapNotNull { saved ->
            val name = normalizeDisplayText(saved.name, MAX_SESSION_NAME)
            if (saved.id.length !in 1..MAX_SESSION_ID_LENGTH ||
                !saved.id.startsWith("session_") || name.isBlank() || saved.savedAt < 0L
            ) return@mapNotNull null

            // Bound corrupt rows before normalization, then reuse the same URL policy as
            // live tab restoration. The original positions remain intact so a rejected
            // active tab falls back to the nearest surviving regular tab.
            // Query only this session and cap rows in SQLite itself. Avoid loading
            // every saved-session tab into memory before applying the restore bound.
            val rawUrls = dao.savedSessionTabs(saved.id, MAX_SESSION_SCAN).map { it.url }
            if (rawUrls.none(::isSafeRestorableUrl)) return@mapNotNull null
            val snapshot = TabSessionState.normalize(rawUrls, saved.activeIndex, HOME_URL)
            RoomSavedSession(saved.id, name, snapshot.urls, snapshot.activeIndex, saved.savedAt)
        }
    }

    suspend fun saveSession(
        rawName: String,
        urls: List<String>,
        activeIndex: Int,
        savedAt: Long = System.currentTimeMillis()
    ): Boolean {
        val name = normalizeDisplayText(rawName, MAX_SESSION_NAME)
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
        return dao.removeSavedSession(id)
    }

    suspend fun renameSession(id: String, rawName: String): Boolean {
        val name = normalizeDisplayText(rawName, MAX_SESSION_NAME)
        if (id.isBlank() || name.isBlank()) return false
        return dao.renameSavedSessionIfNameAvailable(id, name)
    }

    suspend fun recentlyClosed(): List<RecentlyClosedTabEntity> {
        pruneExpiredRecentlyClosed()
        return dao.recentlyClosedTabs(MAX_RECENT_SCAN)
            .asSequence()
            .filter { it.id.length in 1..MAX_RECENT_ID_LENGTH && it.id.startsWith("closed_") }
            .filter { it.closedAt >= 0L && isSafeRestorableUrl(it.url) && it.url != HOME_URL }
            .take(MAX_RECENTLY_CLOSED)
            .map { it.copy(title = normalizeDisplayText(it.title, MAX_RECENT_TITLE)) }
    }

    suspend fun recordRecentlyClosed(
        url: String,
        title: String,
        closedAt: Long = System.currentTimeMillis()
    ): Boolean {
        val normalized = url.trim()
        if (!isSafeRestorableUrl(normalized) || normalized == HOME_URL || closedAt < 0L) return false
        val safeTitle = title
            .let { normalizeDisplayText(it, MAX_RECENT_TITLE) }
            .ifBlank { android.net.Uri.parse(normalized).host.orEmpty().ifBlank { normalized } }
        pruneExpiredRecentlyClosed(closedAt)
        dao.recordRecentlyClosed(RecentlyClosedTabEntity(
            id = "closed_${UUID.randomUUID()}",
            url = normalized,
            title = safeTitle,
            closedAt = closedAt
        ))
        return true
    }

    suspend fun clearRecentlyClosed() {
        dao.clearRecentlyClosed()
    }

    suspend fun consumeRecentlyClosed(id: String): RecentlyClosedTabEntity? {
        pruneExpiredRecentlyClosed()
        return dao.consumeRecentlyClosedTab(id)?.takeIf {
            it.id.length in 1..MAX_RECENT_ID_LENGTH &&
                it.id.startsWith("closed_") &&
                it.closedAt >= 0L &&
                isSafeRestorableUrl(it.url) &&
                it.url != HOME_URL
        }
    }

    /** Atomically consumes only the requested restore entries, preserving newer unselected items. */
    suspend fun consumeRecentlyClosed(ids: List<String>): List<RecentlyClosedTabEntity> {
        pruneExpiredRecentlyClosed()
        // A restore action should never be able to submit an unbounded batch from stale UI state.
        val safeIds = ids.asSequence()
            .filter { it.length in 1..MAX_RECENT_ID_LENGTH && it.startsWith("closed_") }
            .distinct()
            .take(MAX_RECENTLY_CLOSED)
            .toList()
        if (safeIds.isEmpty()) return emptyList()
        return dao.consumeRecentlyClosedTabs(safeIds).filter {
            it.id.length in 1..MAX_RECENT_ID_LENGTH &&
                it.id.startsWith("closed_") &&
                it.closedAt >= 0L &&
                isSafeRestorableUrl(it.url) &&
                it.url != HOME_URL
        }
    }

    private suspend fun pruneExpiredRecentlyClosed(now: Long = System.currentTimeMillis()) {
        val cutoff = (now - RECENTLY_CLOSED_RETENTION_MILLIS).coerceAtLeast(0L)
        dao.deleteRecentlyClosedBefore(cutoff)
    }

    /** Removes control and bidi-override characters from untrusted session labels. */
    private fun normalizeDisplayText(value: String, maxLength: Int): String =
        value.asSequence()
            .filterNot { char ->
                char.isISOControl() || char in '\u202A'..'\u202E' || char in '\u2066'..'\u2069'
            }
            .joinToString("")
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(maxLength)

    private fun isSafeRestorableUrl(url: String): Boolean =
        url.length <= MAX_URL_LENGTH && (
            url == HOME_URL || url == "about:blank" ||
                NavigationInput.isValidWebUrl(url)
            )

    private companion object {
        const val HOME_URL = "resource://android/assets/home.html"
        const val MAX_URL_LENGTH = 4096
        const val MAX_SESSION_NAME = 40
        const val MAX_SESSION_ID_LENGTH = 64
        const val MAX_SAVED_SESSIONS = 10
        const val MAX_SESSION_SCAN = TabSessionState.MAX_TABS * 4
        const val MAX_RECENT_TITLE = 180
        const val MAX_RECENT_ID_LENGTH = 64
        const val MAX_RECENTLY_CLOSED = 10
        const val MAX_RECENT_SCAN = MAX_RECENTLY_CLOSED * 4
        const val RECENTLY_CLOSED_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L
    }
}

internal data class RoomSavedSession(
    val id: String,
    val name: String,
    val urls: List<String>,
    val activeIndex: Int,
    val savedAt: Long
)
