package com.browsex.app.storage

import com.browsex.app.NavigationInput
import java.util.UUID

/** Local history persistence; private browsing visits are rejected at this boundary. */
internal class RoomHistoryRepository(
    private val dao: BrowseXStorageDao
) {
    suspend fun recent(limit: Int = MAX_HISTORY): List<HistoryEntryEntity> =
        dao.history(limit.coerceIn(0, MAX_HISTORY))

    suspend fun recordVisit(
        url: String,
        title: String,
        privateMode: Boolean,
        visitedAt: Long = System.currentTimeMillis()
    ): Boolean {
        // Privacy state is required (no fail-open default) and checked before any URL
        // normalization or database access, even if a caller bypasses the event coordinator.
        if (privateMode) return false

        val normalizedUrl = url.trim()
        if (normalizedUrl.length > MAX_URL_LENGTH ||
            !NavigationInput.isValidWebUrl(normalizedUrl) || visitedAt < 0L
        ) return false
        // History titles originate in page metadata and can contain invisible control
        // characters. Remove them before persistence so list rows and accessibility output
        // cannot be spoofed with embedded line breaks or directional controls.
        val safeTitle = title
            .filterNot { it.isISOControl() || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' }
            .replace(WHITESPACE, " ")
            .trim()
            .take(MAX_TITLE_LENGTH)
            .ifBlank { android.net.Uri.parse(normalizedUrl).host.orEmpty().ifBlank { normalizedUrl } }
        dao.recordHistoryEntry(HistoryEntryEntity(
            id = "history_${UUID.randomUUID()}",
            url = normalizedUrl,
            title = safeTitle,
            visitedAt = visitedAt,
            visitCount = 1
        ), MAX_HISTORY)
        return true
    }

    suspend fun remove(url: String): Boolean {
        val normalized = url.trim()
        if (normalized.length > MAX_URL_LENGTH || !NavigationInput.isValidWebUrl(normalized)) return false
        return dao.deleteHistoryByUrl(normalized) > 0
    }

    suspend fun clear() = dao.clearHistory()

    private companion object {
        const val MAX_HISTORY = 300
        const val MAX_URL_LENGTH = 4096
        const val MAX_TITLE_LENGTH = 160
        val WHITESPACE = Regex("\\s+")
    }
}
