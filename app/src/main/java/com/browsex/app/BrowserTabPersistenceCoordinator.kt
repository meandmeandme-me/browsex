package com.browsex.app

/**
 * Owns the persistence boundary between live browser tabs and durable tab snapshots.
 *
 * Only regular-tab URLs and the selected regular-tab index are persisted. GeckoSession
 * instances and all private-tab state remain in memory and are never passed to storage.
 */
internal class BrowserTabPersistenceCoordinator(
    private val store: BrowserStore,
    private val tabs: () -> List<BrowserTab>,
    private val activeTab: () -> BrowserTab?
) {
    fun restoreOpenTabs(): TabSessionState.Snapshot = store.restoreOpenTabs()

    fun persistOpenTabs() {
        val currentTabs = tabs()
        val selectedIndex = currentTabs.indexOfFirst { it === activeTab() }
        val snapshot = BrowserTabPersistencePolicy.snapshot(
            urls = currentTabs.map { it.url },
            privateFlags = currentTabs.map { it.privateMode },
            activeIndex = selectedIndex,
            homeUrl = HOME_URL
        )
        store.saveOpenTabs(snapshot.urls, snapshot.activeIndex)
    }
}
