package com.browsex.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Compatibility facade over focused preference, session, download and library stores. */
class BrowserStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("browsex.browser", Context.MODE_PRIVATE)
    private val preferences = BrowserPreferencesStore(prefs)
    private val sessions = BrowserSessionStore(prefs)

    private companion object {
        // Bound legacy/corrupt session payloads before the pure session validator processes them.
        const val MAX_RAW_STORED_TABS = 500
        const val MAX_TRACKED_DOWNLOADS = 300
    }

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

    fun bookmarks(): List<String> = bookmarkItems().map { it.url }
    fun history(): List<String> = historyEntries().map { it.url }

    /** Title and visit time make history useful for recall while preserving legacy URL access. */
    internal fun historyEntries(): List<BrowserHistoryEntry> {
        val stored = prefs.getString("history_entries_v1", null)
        if (stored == null) {
            val migrated = readList("history").map { url ->
                BrowserHistoryEntry(url, android.net.Uri.parse(url).host.orEmpty().ifBlank { url }.take(160), 0L)
            }
            writeHistoryEntries(migrated)
            return migrated
        }
        return try {
            val array = JSONArray(stored)
            (0 until minOf(array.length(), BrowserStorePolicy.MAX_HISTORY)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url").trim()
                if (!NavigationInput.isValidWebUrl(url) || url.length > 4096) return@mapNotNull null
                BrowserHistoryEntry(
                    url,
                    item.optString("title").take(160).ifBlank { android.net.Uri.parse(url).host.orEmpty() },
                    item.optLong("visitedAt", 0L).coerceAtLeast(0L)
                )
            }.distinctBy { it.url }
        } catch (_: Exception) {
            // Recover from the legacy URL-only mirror if a newer metadata payload is damaged.
            val fallback = readList("history").map { url ->
                BrowserHistoryEntry(url, android.net.Uri.parse(url).host.orEmpty().ifBlank { url }.take(160), 0L)
            }
            writeHistoryEntries(fallback)
            fallback
        }
    }

    internal fun bookmarkFolders(): List<BookmarkFolder> = readBookmarkFolders()
    internal fun bookmarkItems(): List<BookmarkItem> = readBookmarkItems()

    internal fun createBookmarkFolder(rawName: String): BookmarkFolder? {
        val name = rawName.trim().take(40)
        if (name.isBlank() || name.equals("Unfiled", true)) return null
        val folders = readBookmarkFolders().toMutableList()
        if (folders.size >= 50 || folders.any { it.name.equals(name, true) }) return null
        val folder = BookmarkFolder("folder_${UUID.randomUUID()}", name)
        folders.add(folder)
        writeBookmarkLibrary(folders, readBookmarkItems())
        return folder
    }

    fun renameBookmarkFolder(folderId: String, rawName: String): Boolean {
        if (folderId == BookmarkFolder.DEFAULT_ID) return false
        val name = rawName.trim().take(40)
        if (name.isBlank()) return false
        val folders = readBookmarkFolders().toMutableList()
        if (folders.any { it.id != folderId && it.name.equals(name, true) }) return false
        val index = folders.indexOfFirst { it.id == folderId }
        if (index < 0) return false
        folders[index] = folders[index].copy(name = name)
        writeBookmarkLibrary(folders, readBookmarkItems())
        return true
    }

    fun removeBookmarkFolder(folderId: String): Boolean {
        if (folderId == BookmarkFolder.DEFAULT_ID) return false
        val folders = readBookmarkFolders()
        if (folders.none { it.id == folderId }) return false
        val items = readBookmarkItems().map { if (it.folderId == folderId) it.copy(folderId = BookmarkFolder.DEFAULT_ID) else it }
        writeBookmarkLibrary(folders.filterNot { it.id == folderId }, items)
        return true
    }

    fun moveBookmark(url: String, folderId: String): Boolean {
        if (readBookmarkFolders().none { it.id == folderId }) return false
        val items = readBookmarkItems().toMutableList()
        val index = items.indexOfFirst { it.url == url }
        if (index < 0) return false
        items[index] = items[index].copy(folderId = folderId)
        writeBookmarkLibrary(readBookmarkFolders(), items)
        return true
    }

    /** Reorders a bookmark without changing its stable identity or folder. */
    fun reorderBookmark(url: String, offset: Int, folderId: String? = null): Boolean {
        if (offset !in -1..1 || offset == 0) return false
        val items = readBookmarkItems().toMutableList()
        val index = items.indexOfFirst { it.url == url }
        if (index < 0) return false
        val groupIndices = items.indices.filter { folderId == null || items[it].folderId == folderId }
        val position = groupIndices.indexOf(index)
        if (position < 0) return false
        val targetPosition = (position + offset).coerceIn(0, groupIndices.lastIndex)
        if (targetPosition == position) return false
        val destination = groupIndices[targetPosition]
        val current = items[index]
        items[index] = items[destination]
        items[destination] = current
        writeBookmarkLibrary(readBookmarkFolders(), items)
        return true
    }

    private fun newBookmarkId(): String = "bookmark_${UUID.randomUUID()}"

    fun isBookmarked(url: String): Boolean = bookmarkItems().any { it.url == url }

    fun toggleBookmark(url: String): Boolean =
        toggleBookmark(url, android.net.Uri.parse(url).host.orEmpty().ifBlank { url })

    fun toggleBookmark(url: String, title: String): Boolean {
        if (!NavigationInput.isValidWebUrl(url)) return false
        val items = readBookmarkItems().toMutableList()
        val existing = items.indexOfFirst { it.url == url }
        val added = existing < 0
        if (added) items.add(0, BookmarkItem(
            id = newBookmarkId(),
            url = url,
            title = title.trim().take(160).ifBlank { android.net.Uri.parse(url).host.orEmpty().ifBlank { url } },
            folderId = BookmarkFolder.DEFAULT_ID,
            createdAt = System.currentTimeMillis()
        )) else items.removeAt(existing)
        writeBookmarkLibrary(readBookmarkFolders(), items)
        return added
    }

    /** Versioned JSON backup; excludes cookies, credentials, history and sessions. */
    fun exportBookmarks(): String {
        val root = JSONObject().put("format", "browsex-bookmarks").put("version", 1)
        val folders = JSONArray()
        readBookmarkFolders().forEach { folders.put(JSONObject().put("id", it.id).put("name", it.name)) }
        val items = JSONArray()
        readBookmarkItems().forEach {
            items.put(JSONObject().put("id", it.id).put("url", it.url).put("title", it.title)
                .put("folderId", it.folderId).put("createdAt", it.createdAt))
        }
        return root.put("folders", folders).put("bookmarks", items).toString(2)
    }

    /** Validates the complete backup before committing; returns the number of accepted bookmarks. */
    fun previewBookmarkImport(payload: String): BookmarkImportPreview {
        require(payload.length <= 1_000_000) { "Backup file is too large" }
        val root = JSONObject(payload)
        require(root.optString("format") == "browsex-bookmarks" && root.optInt("version") == 1) {
            "Unsupported bookmark backup"
        }
        val folders = root.optJSONArray("folders") ?: JSONArray()
        val bookmarks = root.optJSONArray("bookmarks") ?: JSONArray()
        require(folders.length() <= 50) { "Too many folders" }
        require(bookmarks.length() <= 1000) { "Too many bookmarks" }
        val validUrls = mutableListOf<String>()
        for (index in 0 until bookmarks.length()) {
            val item = bookmarks.optJSONObject(index) ?: continue
            val url = item.optString("url").trim()
            if (url.length <= 4096 && NavigationInput.isValidWebUrl(url)) validUrls.add(url)
        }
        val uniqueUrls = validUrls.distinct()
        val existingUrls = bookmarkItems().map { it.url }.toSet()
        return BookmarkImportPreview(
            totalItems = bookmarks.length(),
            validItems = uniqueUrls.size,
            skippedItems = bookmarks.length() - validUrls.size,
            duplicateIncomingItems = validUrls.size - uniqueUrls.size,
            existingUrlConflicts = uniqueUrls.count(existingUrls::contains),
            incomingFolders = folders.length()
        )
    }

    fun importBookmarks(payload: String, merge: Boolean = true): Int {
        require(payload.length <= 1_000_000) { "Backup file is too large" }
        val root = JSONObject(payload)
        require(root.optString("format") == "browsex-bookmarks" && root.optInt("version") == 1) {
            "Unsupported bookmark backup"
        }
        val incomingFolders = mutableListOf(BookmarkFolder.DEFAULT)
        val folderArray = root.optJSONArray("folders") ?: JSONArray()
        require(folderArray.length() <= 50) { "Too many folders" }
        for (i in 0 until folderArray.length()) {
            val obj = folderArray.optJSONObject(i) ?: continue
            val id = obj.optString("id").take(80)
            val name = obj.optString("name").trim().take(40)
            if (id.isNotBlank() && id != BookmarkFolder.DEFAULT_ID && name.isNotBlank() &&
                incomingFolders.none { it.id == id || it.name.equals(name, true) }) {
                incomingFolders.add(BookmarkFolder(id, name))
            }
        }
        val incoming = mutableListOf<BookmarkItem>()
        val bookmarkArray = root.optJSONArray("bookmarks") ?: JSONArray()
        require(bookmarkArray.length() <= 1000) { "Too many bookmarks" }
        for (i in 0 until bookmarkArray.length()) {
            val obj = bookmarkArray.optJSONObject(i) ?: continue
            val url = obj.optString("url").trim()
            if (!NavigationInput.isValidWebUrl(url) || url.length > 4096) continue
            val folderId = obj.optString("folderId").takeIf { id -> incomingFolders.any { it.id == id } }
                ?: BookmarkFolder.DEFAULT_ID
            incoming.add(BookmarkItem(
                id = obj.optString("id").take(80).ifBlank { "bookmark_${url.hashCode().toUInt().toString(16)}" },
                url = url,
                title = obj.optString("title").take(160).ifBlank { android.net.Uri.parse(url).host.orEmpty() },
                folderId = folderId,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()).coerceAtLeast(0L)
            ))
        }
        // Merge folders by name, not only by ID. An imported ID may collide with a
        // different local folder; remap it so imported bookmarks never land in the wrong folder.
        val folders = (if (merge) readBookmarkFolders() else listOf(BookmarkFolder.DEFAULT)).toMutableList()
        val folderIdMap = mutableMapOf(BookmarkFolder.DEFAULT_ID to BookmarkFolder.DEFAULT_ID)
        incomingFolders.drop(1).forEach { incomingFolder ->
            val sameName = folders.firstOrNull { it.name.equals(incomingFolder.name, ignoreCase = true) }
            if (sameName != null) {
                folderIdMap[incomingFolder.id] = sameName.id
            } else {
                val safeId = if (folders.any { it.id == incomingFolder.id }) "folder_${UUID.randomUUID()}" else incomingFolder.id
                val safeFolder = incomingFolder.copy(id = safeId)
                if (folders.size < 51) {
                    folders.add(safeFolder)
                    folderIdMap[incomingFolder.id] = safeId
                } else {
                    folderIdMap[incomingFolder.id] = BookmarkFolder.DEFAULT_ID
                }
            }
        }
        val folderIds = folders.map { it.id }.toSet()
        val remappedIncoming = incoming.map { item ->
            item.copy(folderId = folderIdMap[item.folderId]
                ?.takeIf(folderIds::contains) ?: BookmarkFolder.DEFAULT_ID)
        }
        val existing = if (merge) readBookmarkItems() else emptyList()
        val merged = (remappedIncoming + existing).map {
            it.copy(folderId = it.folderId.takeIf(folderIds::contains) ?: BookmarkFolder.DEFAULT_ID)
        }.distinctBy { it.url }.take(BrowserStorePolicy.MAX_BOOKMARKS)
        writeBookmarkLibrary(folders, merged)
        return remappedIncoming.map { it.url }.distinct().count { url -> merged.any { it.url == url } }
    }

    fun addHistory(url: String) {
        addHistory(url, android.net.Uri.parse(url).host.orEmpty().ifBlank { url })
    }

    fun addHistory(url: String, title: String) {
        if (!NavigationInput.isValidWebUrl(url)) return
        val entry = BrowserHistoryEntry(
            url = url,
            title = title.trim().take(160).ifBlank { android.net.Uri.parse(url).host.orEmpty().ifBlank { url } },
            visitedAt = System.currentTimeMillis()
        )
        writeHistoryEntries(listOf(entry) + historyEntries().filterNot { it.url == url })
    }

    fun removeBookmark(url: String) {
        writeBookmarkLibrary(readBookmarkFolders(), readBookmarkItems().filterNot { it.url == url })
    }

    fun removeHistory(url: String) {
        writeHistoryEntries(historyEntries().filterNot { it.url == url })
    }

    fun clearHistory() = prefs.edit()
        .remove("history")
        .remove("history_entries_v1")
        .remove("recently_closed_tabs_v1")
        .apply()

    private fun writeHistoryEntries(entries: List<BrowserHistoryEntry>) {
        val safe = entries.filter { NavigationInput.isValidWebUrl(it.url) }
            .distinctBy { it.url }.take(BrowserStorePolicy.MAX_HISTORY)
        val detailed = JSONArray()
        val legacy = JSONArray()
        safe.forEach { entry ->
            detailed.put(JSONObject()
                .put("url", entry.url)
                .put("title", entry.title.take(160))
                .put("visitedAt", entry.visitedAt.coerceAtLeast(0L)))
            legacy.put(entry.url)
        }
        prefs.edit()
            .putString("history_entries_v1", detailed.toString())
            .putString("history", legacy.toString())
            .apply()
    }

    private fun readBookmarkFolders(): List<BookmarkFolder> {
        val stored = prefs.getString("bookmark_folders_v1", null)
        if (stored == null) return listOf(BookmarkFolder.DEFAULT)
        return try {
            val array = JSONArray(stored)
            (0 until minOf(array.length(), 50)).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").take(80)
                val name = obj.optString("name").trim().take(40)
                if (id.isBlank() || name.isBlank()) null else BookmarkFolder(id, name)
            }.distinctBy { it.id }.let { list ->
                if (list.any { it.id == BookmarkFolder.DEFAULT_ID }) list
                else listOf(BookmarkFolder.DEFAULT) + list
            }
        } catch (_: Exception) { listOf(BookmarkFolder.DEFAULT) }
    }

    private fun readBookmarkItems(): List<BookmarkItem> {
        val stored = prefs.getString("bookmark_items_v1", null)
        if (stored == null) {
            val migrated = readList("bookmarks").map { url ->
                BookmarkItem(newBookmarkId(), url,
                    android.net.Uri.parse(url).host.orEmpty().ifBlank { url }.take(160),
                    BookmarkFolder.DEFAULT_ID, 0L)
            }
            writeBookmarkLibrary(listOf(BookmarkFolder.DEFAULT), migrated)
            return migrated
        }
        return try {
            val folders = readBookmarkFolders().map { it.id }.toSet()
            val array = JSONArray(stored)
            (0 until minOf(array.length(), BrowserStorePolicy.MAX_BOOKMARKS)).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val url = obj.optString("url").trim()
                if (!NavigationInput.isValidWebUrl(url) || url.length > 4096) return@mapNotNull null
                BookmarkItem(
                    obj.optString("id").take(80).ifBlank { newBookmarkId() },
                    url, obj.optString("title").take(160),
                    obj.optString("folderId").takeIf(folders::contains) ?: BookmarkFolder.DEFAULT_ID,
                    obj.optLong("createdAt", 0L).coerceAtLeast(0L)
                )
            }.distinctBy { it.url }
        } catch (_: Exception) { emptyList() }
    }

    private fun writeBookmarkLibrary(folders: List<BookmarkFolder>, items: List<BookmarkItem>) {
        val safeFolders = (listOf(BookmarkFolder.DEFAULT) + folders.filter { it.id != BookmarkFolder.DEFAULT_ID })
            .distinctBy { it.id }.take(51)
        val ids = safeFolders.map { it.id }.toSet()
        val safeItems = items.filter { NavigationInput.isValidWebUrl(it.url) }
            .distinctBy { it.url }.take(BrowserStorePolicy.MAX_BOOKMARKS)
            .map { it.copy(folderId = it.folderId.takeIf(ids::contains) ?: BookmarkFolder.DEFAULT_ID) }
            .let { items ->
                val seenIds = mutableSetOf<String>()
                items.map { item ->
                    val safeId = item.id.takeIf { it.isNotBlank() && seenIds.add(it) } ?: newBookmarkId().also { seenIds.add(it) }
                    item.copy(id = safeId)
                }
            }
        val folderJson = JSONArray()
        safeFolders.forEach { folderJson.put(JSONObject().put("id", it.id).put("name", it.name)) }
        val itemJson = JSONArray()
        safeItems.forEach { itemJson.put(JSONObject().put("id", it.id).put("url", it.url)
            .put("title", it.title.take(160)).put("folderId", it.folderId).put("createdAt", it.createdAt)) }
        val legacy = JSONArray()
        safeItems.forEach { legacy.put(it.url) }
        prefs.edit().putString("bookmark_folders_v1", folderJson.toString())
            .putString("bookmark_items_v1", itemJson.toString())
            .putString("bookmarks", legacy.toString()).apply()
    }

    private fun readList(key: String): List<String> = try {
        val array = JSONArray(prefs.getString(key, "[]"))
        val values = (0 until minOf(array.length(), MAX_RAW_STORED_TABS)).mapNotNull { index ->
            array.optString(index).takeIf(String::isNotBlank)
        }
        BrowserStorePolicy.normalizeUrls(values, limitFor(key))
    } catch (_: Exception) { emptyList() }

    private fun limitFor(key: String): Int = when (key) {
        "bookmarks" -> BrowserStorePolicy.MAX_BOOKMARKS
        "history" -> BrowserStorePolicy.MAX_HISTORY
        else -> TabSessionState.MAX_TABS
    }
}