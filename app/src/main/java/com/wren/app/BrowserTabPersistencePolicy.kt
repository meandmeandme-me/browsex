package com.wren.app

/** Pure policy for durable regular-tab snapshots; private tabs are never persisted. */
internal object BrowserTabPersistencePolicy {
    /**
     * Returns a normalized snapshot containing regular tabs only, or null when
     * the input contains no regular tabs. The nullable result lets named-session
     * persistence reject a private-only snapshot instead of saving a home placeholder.
     */
    fun regularSnapshot(
        urls: List<String>,
        privateFlags: List<Boolean>,
        activeIndex: Int,
        homeUrl: String
    ): TabSessionState.Snapshot? {
        require(urls.size == privateFlags.size) { "Tab URL and privacy snapshots must have equal sizes" }
        val regularIndexes = urls.indices.filter { !privateFlags[it] }
        if (regularIndexes.isEmpty()) return null
        val regularUrls = regularIndexes.map { urls[it] }
        val selectedIndex = regularIndexes.indexOf(activeIndex).takeIf { it >= 0 } ?: 0
        return TabSessionState.normalize(regularUrls, selectedIndex, homeUrl)
    }

    fun snapshot(
        urls: List<String>,
        privateFlags: List<Boolean>,
        activeIndex: Int,
        homeUrl: String
    ): TabSessionState.Snapshot =
        regularSnapshot(urls, privateFlags, activeIndex, homeUrl)
            ?: TabSessionState.normalize(emptyList(), 0, homeUrl)
}
