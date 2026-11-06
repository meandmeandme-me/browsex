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

    @Synchronized
    fun addBrowseXDownload(id: Long) {
        if (id <= 0L) return
        val ids = browseXDownloadIds().toMutableList()
        ids.remove(id)
        ids.add(id)
        val array = JSONArray()
        ids.takeLast(MAX_TRACKED_DOWNLOADS).forEach { array.put(it) }
        prefs.edit().putString("browsex_download_ids", array.toString()).apply()
    }

    @Synchronized
    fun removeBrowseXDownload(id: Long) {
        val array = JSONArray()
        browseXDownloadIds().filterNot { it == id }.forEach { array.put(it) }
        prefs.edit().putString("browsex_download_ids", array.toString()).apply()
    }

    @Synchronized
    fun beginStreamDownload(
        displayName: String,
        mimeType: String,
        totalBytes: Long = -1L
    ): String {
        val id = "stream_download_${UUID.randomUUID()}"
        val records = streamDownloads().toMutableList()
        records.add(0, StreamDownloadRecord(
            id = id,
            displayName = displayName.take(140),
            uri = "",
            mimeType = mimeType.take(120),
            bytesWritten = 0L,
            createdAt = System.currentTimeMillis(),
            status = "queued",
            totalBytes = totalBytes.coerceAtLeast(-1L)
        ))
        writeStreamDownloads(records)
        return id
    }

    @Synchronized
    fun updateStreamDownloadProgress(id: String, bytesWritten: Long) {
        if (bytesWritten < 0L) return
        updateStreamDownload(id) { current ->
            if (!current.isActive) current else current.copy(
                bytesWritten = maxOf(current.bytesWritten, bytesWritten),
                status = "running"
            )
        }
    }

    @Synchronized
    fun completeStreamDownload(id: String, uri: String, bytesWritten: Long) {
        val scheme = runCatching { android.net.Uri.parse(uri).scheme?.lowercase() }.getOrNull()
        if (scheme !in setOf("content", "file") || uri.length > 2048 || bytesWritten < 0L) return
        updateStreamDownload(id) { current ->
            if (!current.isActive) current else current.copy(
                uri = uri,
                bytesWritten = maxOf(current.bytesWritten, bytesWritten),
                failed = false,
                error = "",
                status = "completed"
            )
        }
    }

    @Synchronized
    fun failStreamDownload(id: String, error: String, cancelled: Boolean = false, bytesWritten: Long? = null) {
        if (bytesWritten != null && bytesWritten < 0L) return
        updateStreamDownload(id) { current ->
            if (!current.isActive) current else current.copy(
                uri = "",
                bytesWritten = bytesWritten?.let { maxOf(current.bytesWritten, it) } ?: current.bytesWritten,
                failed = !cancelled,
                error = error.take(240),
                status = if (cancelled) "cancelled" else "failed"
            )
        }
    }

    @Synchronized
    fun recoverInterruptedStreamDownloads() {
        val records = streamDownloads()
        var changed = false
        val recovered = records.map { item ->
            if (item.isActive) {
                changed = true
                item.copy(
                    uri = "",
                    failed = true,
                    status = "failed",
                    error = "Download interrupted when BrowseX was closed"
                )
            } else item
        }
        if (changed) writeStreamDownloads(recovered)
    }

    private fun updateStreamDownload(
        id: String,
        transform: (StreamDownloadRecord) -> StreamDownloadRecord
    ) {
        val records = streamDownloads().toMutableList()
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return
        records[index] = transform(records[index])
        writeStreamDownloads(records)
    }

    /** Backward-compatible helper for callers that already have a terminal result. */
    fun addStreamDownload(
        displayName: String,
        uri: String,
        mimeType: String,
        bytesWritten: Long,
        failed: Boolean = false,
        error: String = ""
    ): String {
        val id = beginStreamDownload(displayName, mimeType)
        if (failed) failStreamDownload(id, error, bytesWritten = bytesWritten)
        else completeStreamDownload(id, uri, bytesWritten)
        return id
    }

    fun streamDownloads(): List<StreamDownloadRecord> = try {
        val stored = prefs.getString("stream_downloads_v1", "[]") ?: "[]"
        if (stored.length > 500_000) emptyList() else {
            val array = JSONArray(stored)
            (0 until minOf(array.length(), MAX_TRACKED_DOWNLOADS)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").take(100)
                val name = item.optString("displayName").take(140)
                val uri = item.optString("uri").take(2048)
                val status = item.optString(
                    "status",
                    if (item.optBoolean("failed", false)) "failed" else "completed"
                ).takeIf { it in setOf("queued", "running", "completed", "failed", "cancelled") } ?: "failed"
                if (id.isBlank() || name.isBlank() ||
                    (uri.isBlank() && status !in setOf("queued", "running", "failed", "cancelled"))
                ) return@mapNotNull null
                StreamDownloadRecord(
                    id = id,
                    displayName = name,
                    uri = uri,
                    mimeType = item.optString("mimeType", "application/octet-stream").take(120),
                    bytesWritten = item.optLong("bytesWritten", 0L).coerceAtLeast(0L),
                    createdAt = item.optLong("createdAt", 0L).coerceAtLeast(0L),
                    failed = status == "failed",
                    error = item.optString("error").take(240),
                    status = status,
                    totalBytes = item.optLong("totalBytes", -1L).coerceAtLeast(-1L)
                )
            }
        }
    } catch (_: Exception) { emptyList() }

    @Synchronized
    fun removeStreamDownload(id: String) {
        writeStreamDownloads(streamDownloads().filterNot { it.id == id })
    }

    private fun writeStreamDownloads(records: List<StreamDownloadRecord>) {
        val array = JSONArray()
        records.take(MAX_TRACKED_DOWNLOADS).forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("displayName", item.displayName)
                .put("uri", item.uri)
                .put("mimeType", item.mimeType)
                .put("bytesWritten", item.bytesWritten)
                .put("createdAt", item.createdAt)
                .put("failed", item.failed)
                .put("error", item.error)
                .put("status", item.status)
                .put("totalBytes", item.totalBytes))
        }
        prefs.edit().putString("stream_downloads_v1", array.toString()).apply()
    }

}
