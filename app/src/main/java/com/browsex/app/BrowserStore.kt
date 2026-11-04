package com.browsex.app

import android.content.Context

/** Compatibility facade over focused preference, session, download and library stores. */
class BrowserStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("browsex.browser", Context.MODE_PRIVATE)
    private val preferences = BrowserPreferencesStore(prefs)
    private val sessions = BrowserSessionStore(prefs)
    private val library = BrowserLibraryStore(prefs)
    private val downloads = BrowserDownloadStore(prefs)

    var adBlockEnabled: Boolean
        get() = preferences.adBlockEnabled
        set(value) { preferences.adBlockEnabled = value }

    var themeMode: String
        get() = preferences.themeMode
        set(value) { preferences.themeMode = value }

    var languageMode: String
        get() = preferences.languageMode
        set(value) { preferences.languageMode = value }

    var siteNotificationsEnabled: Boolean
        get() = preferences.siteNotificationsEnabled
        set(value) { preferences.siteNotificationsEnabled = value }

    var homeShowQuickLinks: Boolean
        get() = preferences.homeShowQuickLinks
        set(value) { preferences.homeShowQuickLinks = value }

    var homeShowDiscovery: Boolean
        get() = preferences.homeShowDiscovery
        set(value) { preferences.homeShowDiscovery = value }

    var homeLayout: String
        get() = preferences.homeLayout
        set(value) { preferences.homeLayout = value }

    var searchEngine: String
        get() = preferences.searchEngine
        set(value) { preferences.searchEngine = value }

    fun resetHomePreferences() = preferences.resetHomePreferences()
    fun wasPlatformPermissionDenied(permission: String): Boolean =
        preferences.wasPlatformPermissionDenied(permission)
    fun markPlatformPermissionDenied(permission: String) =
        preferences.markPlatformPermissionDenied(permission)
    fun clearPlatformPermissionDenial(permission: String) =
        preferences.clearPlatformPermissionDenial(permission)
    fun siteMediaDecision(origin: String, feature: String): String? =
        preferences.siteMediaDecision(origin, feature)
    fun setSiteMediaDecision(origin: String, feature: String, decision: String) =
        preferences.setSiteMediaDecision(origin, feature, decision)
    fun clearSiteMediaDecisions(origin: String) = preferences.clearSiteMediaDecisions(origin)
    fun clearAllSiteMediaDecisions() = preferences.clearAllSiteMediaDecisions()

    fun reserveDownloadFileName(candidate: String): String =
        downloads.reserveDownloadFileName(candidate)

    fun browseXDownloadIds(): Set<Long> = downloads.browseXDownloadIds()
    fun addBrowseXDownload(id: Long) = downloads.addBrowseXDownload(id)
    fun removeBrowseXDownload(id: Long) = downloads.removeBrowseXDownload(id)

    fun beginStreamDownload(displayName: String, mimeType: String, totalBytes: Long = -1L): String =
        downloads.beginStreamDownload(displayName, mimeType, totalBytes)

    fun updateStreamDownloadProgress(id: String, bytesWritten: Long) =
        downloads.updateStreamDownloadProgress(id, bytesWritten)

    fun completeStreamDownload(id: String, uri: String, bytesWritten: Long) =
        downloads.completeStreamDownload(id, uri, bytesWritten)

    fun failStreamDownload(
        id: String,
        error: String,
        cancelled: Boolean = false,
        bytesWritten: Long? = null
    ) = downloads.failStreamDownload(id, error, cancelled, bytesWritten)

    fun recoverInterruptedStreamDownloads() = downloads.recoverInterruptedStreamDownloads()

    fun addStreamDownload(
        displayName: String,
        uri: String,
        mimeType: String,
        bytesWritten: Long,
        failed: Boolean = false,
        error: String = ""
    ): String = downloads.addStreamDownload(displayName, uri, mimeType, bytesWritten, failed, error)

    fun streamDownloads(): List<StreamDownloadRecord> = downloads.streamDownloads()
    fun removeStreamDownload(id: String) = downloads.removeStreamDownload(id)

    fun saveOpenTabs(urls: List<String>, activeIndex: Int) =
        sessions.saveOpenTabs(urls, activeIndex)

    fun restoreOpenTabs(): TabSessionState.Snapshot = sessions.restoreOpenTabs()

    fun saveNamedSession(rawName: String, urls: List<String>, activeIndex: Int): Boolean =
        sessions.saveNamedSession(rawName, urls, activeIndex)

    internal fun savedSessions(): List<SavedBrowserSession> = sessions.savedSessions()

    fun removeNamedSession(id: String): Boolean = sessions.removeNamedSession(id)

    fun recordRecentlyClosedTab(url: String, title: String) =
        sessions.recordRecentlyClosedTab(url, title)

    fun recentlyClosedTabs(): List<RecentlyClosedTab> = sessions.recentlyClosedTabs()

    fun consumeRecentlyClosedTab(id: String): RecentlyClosedTab? =
        sessions.consumeRecentlyClosedTab(id)

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
        updateStreamDownload(id) { it.copy(
            bytesWritten = bytesWritten.coerceAtLeast(it.bytesWritten),
            status = "running"
        ) }
    }

    @Synchronized
    fun completeStreamDownload(id: String, uri: String, bytesWritten: Long) {
        updateStreamDownload(id) { it.copy(
            uri = uri.take(2048),
            bytesWritten = bytesWritten.coerceAtLeast(0L),
            failed = false,
            error = "",
            status = "completed"
        ) }
    }

    @Synchronized
    fun failStreamDownload(id: String, error: String, cancelled: Boolean = false, bytesWritten: Long? = null) {
        updateStreamDownload(id) { it.copy(
            uri = "",
            bytesWritten = (bytesWritten ?: it.bytesWritten).coerceAtLeast(0L),
            failed = !cancelled,
            error = error.take(240),
            status = if (cancelled) "cancelled" else "failed"
        ) }
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

    fun bookmarks(): List<String> = library.bookmarks()
    fun history(): List<String> = library.history()
    internal fun historyEntries(): List<BrowserHistoryEntry> = library.historyEntries()
    internal fun bookmarkFolders(): List<BookmarkFolder> = library.bookmarkFolders()
    internal fun bookmarkItems(): List<BookmarkItem> = library.bookmarkItems()
    internal fun createBookmarkFolder(rawName: String): BookmarkFolder? = library.createBookmarkFolder(rawName)
    fun renameBookmarkFolder(folderId: String, rawName: String): Boolean =
        library.renameBookmarkFolder(folderId, rawName)
    fun removeBookmarkFolder(folderId: String): Boolean = library.removeBookmarkFolder(folderId)
    fun moveBookmark(url: String, folderId: String): Boolean = library.moveBookmark(url, folderId)
    fun reorderBookmark(url: String, offset: Int, folderId: String? = null): Boolean =
        library.reorderBookmark(url, offset, folderId)
    fun isBookmarked(url: String): Boolean = library.isBookmarked(url)
    fun toggleBookmark(url: String): Boolean = library.toggleBookmark(url)
    fun toggleBookmark(url: String, title: String): Boolean = library.toggleBookmark(url, title)
    fun exportBookmarks(): String = library.exportBookmarks()
    fun previewBookmarkImport(payload: String): BookmarkImportPreview =
        library.previewBookmarkImport(payload)
    fun importBookmarks(payload: String, merge: Boolean = true): Int =
        library.importBookmarks(payload, merge)
    fun addHistory(url: String) = library.addHistory(url)
    fun addHistory(url: String, title: String) = library.addHistory(url, title)
    fun removeBookmark(url: String) = library.removeBookmark(url)
    fun removeHistory(url: String) = library.removeHistory(url)
    fun clearHistory() = library.clearHistory()

}