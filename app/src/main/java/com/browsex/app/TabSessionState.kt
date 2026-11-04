package com.browsex.app

/** Validated, bounded snapshot of the tabs that should be reopened on next launch. */
internal object TabSessionState {
    const val MAX_TABS = 20

    data class Snapshot(val urls: List<String>, val activeIndex: Int)

    fun normalize(urls: List<String>, activeIndex: Int, homeUrl: String): Snapshot {
        val safe = urls.asSequence()
            .map(String::trim)
            .filter { it == homeUrl || it == "about:blank" || NavigationInput.isValidWebUrl(it) }
            .distinct()
            .take(MAX_TABS)
            .toList()
            .ifEmpty { listOf(homeUrl) }
        return Snapshot(safe, activeIndex.coerceIn(0, safe.lastIndex))
    }
}
