package com.browsex.app.storage

import android.content.Context

/**
 * Single composition root for the Room-backed repositories.
 *
 * BrowserStorageRuntime owns this graph and exposes it only after the legacy snapshot
 * migration completes. Features are cut over as complete read/write slices; BrowserStore
 * remains a compatibility façade only for features that have not yet moved to Room/DataStore.
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
