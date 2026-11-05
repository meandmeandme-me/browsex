package com.browsex.app.storage

import android.content.Context

/**
 * Single composition root for Room-backed repositories.
 *
 * BrowserStorageRuntime owns this graph and initializes it on first use. No legacy
 * user-data import is performed because BrowseX is being rebuilt before launch.
 * Features still using BrowserStore are being moved to their final repositories in
 * focused slices; this graph remains the single entry point for durable browser data.
 */
internal class BrowserStorageGraph(
    context: Context,
    databaseOverride: BrowseXDatabase? = null
) {
    val database: BrowseXDatabase =
        databaseOverride ?: BrowseXDatabase.getInstance(context.applicationContext)

    private val dao: BrowseXStorageDao = database.storageDao()

    val bookmarks = RoomBookmarkRepository(dao)
    val tabs = RoomTabStateRepository(dao)
    val sessions = RoomSessionRepository(dao)
    val history = RoomHistoryRepository(dao)
    val downloads = RoomDownloadRepository(dao)
    val sitePermissions = RoomSitePermissionRepository(dao)
}
