package com.browsex.app.storage

import com.browsex.app.BrowserTabPersistencePolicy
import com.browsex.app.TabSessionState

/** Persists a regular-tab snapshot and enforces private-tab exclusion at the storage boundary. */
internal class RoomTabStateRepository(
    private val dao: BrowseXStorageDao
) {
    suspend fun save(
        urls: List<String>,
        privateFlags: List<Boolean>,
        activeIndex: Int,
        homeUrl: String = HOME_URL,
        savedAt: Long = System.currentTimeMillis()
    ): Boolean {
        if (savedAt < 0L) return false
        val snapshot = BrowserTabPersistencePolicy.snapshot(urls, privateFlags, activeIndex, homeUrl)
        val tabs = snapshot.urls.mapIndexed { index, url -> OpenTabEntity(index, url) }
        dao.saveOpenTabSnapshot(
            BrowserStateEntity(activeIndex = snapshot.activeIndex, savedAt = savedAt),
            tabs
        )
        return true
    }

    suspend fun restore(): TabSessionSnapshot {
        val tabs = dao.openTabs(MAX_TAB_SCAN).map { it.url }
        val state = dao.browserState().firstOrNull()
        val normalized = TabSessionState.normalize(tabs, state?.activeIndex ?: 0, HOME_URL)
        return TabSessionSnapshot(normalized.urls, normalized.activeIndex, state?.savedAt ?: 0L)
    }

    suspend fun clear(savedAt: Long = System.currentTimeMillis()) {
        dao.saveOpenTabSnapshot(
            BrowserStateEntity(activeIndex = 0, savedAt = savedAt.coerceAtLeast(0L)),
            listOf(OpenTabEntity(0, HOME_URL))
        )
    }

    private companion object {
        const val HOME_URL = "resource://android/assets/home.html"
        const val MAX_TAB_SCAN = TabSessionState.MAX_TABS * 4
    }
}

internal data class TabSessionSnapshot(
    val urls: List<String>,
    val activeIndex: Int,
    val savedAt: Long
)
