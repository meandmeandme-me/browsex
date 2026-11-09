package com.browsex.app.storage

import java.util.UUID

/** Validated metadata lifecycle for streamed and platform-managed downloads. */
internal class RoomDownloadRepository(
    private val dao: BrowseXStorageDao
) {
    suspend fun all(): List<DownloadRecordEntity> = dao.downloads()

    suspend fun beginStream(
        displayName: String,
        mimeType: String,
        totalBytes: Long = -1L,
        createdAt: Long = System.currentTimeMillis()
    ): String? {
        val name = normalizeDisplayText(displayName, MAX_DISPLAY_NAME)
        if (name.isBlank() || createdAt < 0L || totalBytes < -1L) return null
        val id = "stream_${UUID.randomUUID()}"
        val created = dao.createDownloadRecord(DownloadRecordEntity(
            id = id,
            sourceKind = "stream",
            displayName = name,
            mimeType = normalizeDisplayText(mimeType, MAX_MIME_TYPE).ifBlank { DEFAULT_MIME },
            uri = "",
            bytesWritten = 0L,
            totalBytes = totalBytes,
            status = "queued",
            error = "",
            createdAt = createdAt
        ), MAX_DOWNLOADS)
        return id.takeIf { created }
    }

    suspend fun markRunning(id: String): Boolean =
        id.isNotBlank() && dao.markDownloadRunning(id) > 0

    suspend fun updateProgress(id: String, bytesWritten: Long): Boolean =
        id.isNotBlank() && bytesWritten >= 0L &&
            dao.updateRunningDownloadProgress(id, bytesWritten) > 0

    suspend fun complete(id: String, uri: String, bytesWritten: Long): Boolean {
        val safeUri = uri.trim().take(MAX_URI_LENGTH)
        val uriScheme = android.net.Uri.parse(safeUri).scheme?.lowercase()
        if (id.isBlank() || safeUri.isBlank() ||
            uriScheme !in COMPLETED_URI_SCHEMES || bytesWritten < 0L
        ) return false
        return dao.completeActiveDownload(id, safeUri, bytesWritten) > 0
    }

    suspend fun fail(id: String, error: String, bytesWritten: Long? = null): Boolean =
        finishActive(id, "failed", error, bytesWritten)

    suspend fun cancel(id: String, bytesWritten: Long? = null): Boolean =
        finishActive(id, "cancelled", "", bytesWritten)

    suspend fun recoverInterrupted(): Int = dao.failInterruptedDownloads()

    suspend fun remove(id: String): Boolean =
        id.isNotBlank() && dao.deleteDownloadRecord(id) > 0

    suspend fun reserveFileName(candidate: String, now: Long = System.currentTimeMillis()): String? {
        val safe = candidate.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .trimStart('.')
            .take(MAX_DISPLAY_NAME)
        if (safe.isBlank() || safe == "." || safe == ".." || now < 0L) return null
        return dao.reserveDownloadName(safe, now)
    }

    suspend fun trackPlatformDownload(id: Long, trackedAt: Long = System.currentTimeMillis()): Boolean {
        if (id <= 0L || trackedAt < 0L) return false
        return dao.trackPlatformDownloadId(PlatformDownloadIdEntity(id, 0, trackedAt))
    }

    suspend fun trackedPlatformDownloadIds(): Set<Long> =
        dao.platformDownloadIds().map { it.downloadId }.toSet()

    suspend fun removeTrackedPlatformDownload(id: Long): Boolean =
        id > 0L && dao.removePlatformDownloadId(id) > 0

    private suspend fun finishActive(
        id: String,
        status: String,
        error: String,
        bytesWritten: Long?
    ): Boolean {
        if (id.isBlank() || bytesWritten != null && bytesWritten < 0L) return false
        return dao.finishActiveDownload(
            id = id,
            status = status,
            error = normalizeDisplayText(error, MAX_ERROR_LENGTH),
            // A missing byte count means preserve the latest progress already stored in Room.
            bytesWritten = bytesWritten ?: -1L
        ) > 0
    }

    /** Removes control and bidi-override characters from untrusted display metadata. */
    private fun normalizeDisplayText(value: String, maxLength: Int): String =
        value.asSequence()
            .filterNot { char ->
                char.isISOControl() || char in '\u202A'..'\u202E' || char in '\u2066'..'\u2069'
            }
            .joinToString("")
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(maxLength)

    private companion object {
        const val MAX_DOWNLOADS = 300
        const val MAX_DISPLAY_NAME = 140
        const val MAX_MIME_TYPE = 120
        const val MAX_URI_LENGTH = 2048
        const val MAX_ERROR_LENGTH = 240
        const val DEFAULT_MIME = "application/octet-stream"
        val ACTIVE_STATUSES = setOf("queued", "running")
        val COMPLETED_URI_SCHEMES = setOf("content", "file")
    }
}
