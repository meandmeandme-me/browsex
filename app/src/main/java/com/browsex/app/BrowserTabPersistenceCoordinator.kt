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
        val regularTabs = tabs().filterNot { it.privateMode }
        val selectedTab = activeTab()
        val activeIndex = regularTabs.indexOfFirst { it === selectedTab }.takeIf { it >= 0 } ?: 0
        store.saveOpenTabs(regularTabs.map { it.url }, activeIndex)
    }
}
