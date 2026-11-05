package com.browsex.app.storage

import android.content.Context

/**
 * Single composition root for the Room-backed repositories.
 *
 * This graph deliberately does not replace BrowserStore in the Activity yet.
 * Production cutover must migrate each feature's reads and writes together,
 * then switch its controller to this graph without maintaining two sources of truth.
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
