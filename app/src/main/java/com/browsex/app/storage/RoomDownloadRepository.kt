package com.browsex.app.storage

import java.util.Locale
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
        val name = displayName.trim().take(MAX_DISPLAY_NAME)
        if (name.isBlank() || createdAt < 0L || totalBytes < -1L) return null
        val id = "stream_${UUID.randomUUID()}"
        val created = dao.createDownloadRecord(DownloadRecordEntity(
            id = id,
            sourceKind = "stream",
            displayName = name,
            mimeType = mimeType.trim().take(MAX_MIME_TYPE).ifBlank { DEFAULT_MIME },
            uri = "",
            bytesWritten = 0L,
            totalBytes = totalBytes,
            status = "queued",
            error = "",
            createdAt = createdAt
        ), MAX_DOWNLOADS)
        return id.takeIf { created }
    }

    suspend fun markRunning(id: String): Boolean = updateActive(id) { current ->
        if (current.status !in ACTIVE_STATUSES) null else current.copy(status = "running")
    }

    suspend fun updateProgress(id: String, bytesWritten: Long): Boolean {
        if (bytesWritten < 0L) return false
        return updateActive(id) { current ->
            if (current.status != "running") null
            else current.copy(bytesWritten = maxOf(current.bytesWritten, bytesWritten))
        }
    }

    suspend fun complete(id: String, uri: String, bytesWritten: Long): Boolean {
        val safeUri = uri.trim().take(MAX_URI_LENGTH)
        if (safeUri.isBlank() || bytesWritten < 0L) return false
        return updateActive(id) { current ->
            if (current.status !in ACTIVE_STATUSES) null
            else current.copy(
                uri = safeUri,
                bytesWritten = maxOf(current.bytesWritten, bytesWritten),
                status = "completed",
                error = ""
            )
        }
    }

    suspend fun fail(id: String, error: String, bytesWritten: Long? = null): Boolean =
        finishActive(id, "failed", error, bytesWritten)

    suspend fun cancel(id: String, bytesWritten: Long? = null): Boolean =
        finishActive(id, "cancelled", "", bytesWritten)

    suspend fun recoverInterrupted(): Int {
        val active = dao.downloads().filter { it.status in ACTIVE_STATUSES }
        if (active.isEmpty()) return 0
        dao.putDownloads(active.map { it.copy(
            uri = "",
            status = "failed",
            error = "Download interrupted before BrowseX could finish it"
        ) })
        return active.size
    }

    suspend fun remove(id: String): Boolean =
        id.isNotBlank() && dao.deleteDownloadRecord(id) > 0

    suspend fun reserveFileName(candidate: String, now: Long = System.currentTimeMillis()): String? {
        val safe = candidate.trim().take(MAX_DISPLAY_NAME)
        if (safe.isBlank() || now < 0L) return null
        return dao.reserveDownloadName(safe, now)
    }

    suspend fun trackPlatformDownload(id: Long, trackedAt: Long = System.currentTimeMillis()): Boolean {
        if (id <= 0L || trackedAt < 0L) return false
        return dao.trackPlatformDownloadId(PlatformDownloadIdEntity(id, 0, trackedAt))
    }

    suspend fun trackedPlatformDownloadIds(): Set<Long> =
        dao.platformDownloadIds().map { it.downloadId }.toSet()

    private suspend fun finishActive(
        id: String,
        status: String,
        error: String,
        bytesWritten: Long?
    ): Boolean {
        if (bytesWritten != null && bytesWritten < 0L) return false
        return updateActive(id) { current ->
            if (current.status !in ACTIVE_STATUSES) null
            else current.copy(
                uri = "",
                bytesWritten = bytesWritten?.let { maxOf(current.bytesWritten, it) } ?: current.bytesWritten,
                status = status,
                error = error.trim().take(MAX_ERROR_LENGTH)
            )
        }
    }

    private suspend fun updateActive(
        id: String,
        transform: (DownloadRecordEntity) -> DownloadRecordEntity?
    ): Boolean {
        if (id.isBlank()) return false
        val current = dao.downloadRecord(id) ?: return false
        val updated = transform(current) ?: return false
        dao.putDownloads(listOf(updated))
        return true
    }

    private companion object {
        const val MAX_DOWNLOADS = 300
        const val MAX_DISPLAY_NAME = 140
        const val MAX_MIME_TYPE = 120
        const val MAX_URI_LENGTH = 2048
        const val MAX_ERROR_LENGTH = 240
        const val DEFAULT_MIME = "application/octet-stream"
        val ACTIVE_STATUSES = setOf("queued", "running")
    }
}
