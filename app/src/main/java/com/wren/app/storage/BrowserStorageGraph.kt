package com.wren.app.storage

import android.content.Context

/**
 * Single composition root for Room-backed repositories.
 *
 * BrowserStorageRuntime owns this graph and initializes it on first use. All durable
 * browsing data is stored through these Room repositories; settings use DataStore.
 * No SharedPreferences browsing data is imported into the clean Wren database.
 */
internal class BrowserStorageGraph(
    context: Context,
    databaseOverride: WrenDatabase? = null
) {
    val database: WrenDatabase =
        databaseOverride ?: WrenDatabase.getInstance(context.applicationContext)

    private val dao: WrenStorageDao = database.storageDao()

    val bookmarks = RoomBookmarkRepository(dao)
    val tabs = RoomTabStateRepository(dao)
    val sessions = RoomSessionRepository(dao)
    val history = RoomHistoryRepository(dao)
    val downloads = RoomDownloadRepository(dao)
    val sitePermissions = RoomSitePermissionRepository(dao)
}
