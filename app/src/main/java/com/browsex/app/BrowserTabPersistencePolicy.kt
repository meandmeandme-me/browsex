package com.browsex.app

/** Pure policy for durable regular-tab snapshots; private tabs are never persisted. */
internal object BrowserTabPersistencePolicy {
    fun snapshot(
        urls: List<String>,
        privateFlags: List<Boolean>,
        activeIndex: Int,
        homeUrl: String
    ): TabSessionState.Snapshot {
        require(urls.size == privateFlags.size) { "Tab URL and privacy snapshots must have equal sizes" }
        val regularIndexes = urls.indices.filter { !privateFlags[it] }
        val regularUrls = regularIndexes.map { urls[it] }
        val selectedIndex = regularIndexes.indexOf(activeIndex).takeIf { it >= 0 } ?: 0
        return TabSessionState.normalize(regularUrls, selectedIndex, homeUrl)
    }
}
