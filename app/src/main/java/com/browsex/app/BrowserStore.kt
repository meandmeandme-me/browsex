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

    var adBlockInstalled: Boolean
        get() = preferences.adBlockInstalled
        set(value) { preferences.adBlockInstalled = value }

    var adBlockPrivateEnabled: Boolean
        get() = preferences.adBlockPrivateEnabled
        set(value) { preferences.adBlockPrivateEnabled = value }

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

    internal fun streamDownloads(): List<StreamDownloadRecord> = downloads.streamDownloads()
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

    internal fun recentlyClosedTabs(): List<RecentlyClosedTab> = sessions.recentlyClosedTabs()

    internal fun consumeRecentlyClosedTab(id: String): RecentlyClosedTab? =
        sessions.consumeRecentlyClosedTab(id)

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
    internal fun previewBookmarkImport(payload: String): BookmarkImportPreview =
        library.previewBookmarkImport(payload)
    fun importBookmarks(payload: String, merge: Boolean = true): Int =
        library.importBookmarks(payload, merge)
    fun addHistory(url: String) = library.addHistory(url)
    fun addHistory(url: String, title: String) = library.addHistory(url, title)
    fun removeBookmark(url: String) = library.removeBookmark(url)
    fun removeHistory(url: String) = library.removeHistory(url)
    fun clearHistory() = library.clearHistory()

}