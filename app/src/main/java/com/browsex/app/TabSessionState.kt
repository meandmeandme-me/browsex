package com.browsex.app

/** Validated, bounded snapshot of the tabs that should be reopened on next launch. */
object TabSessionState {
    const val MAX_TABS = 20

    data class Snapshot(val urls: List<String>, val activeIndex: Int)

    fun normalize(urls: List<String>, activeIndex: Int, homeUrl: String): Snapshot {
        val accepted = urls.mapIndexedNotNull { index, raw ->
            val url = raw.trim()
            if (url == homeUrl || url == "about:blank" || NavigationInput.isValidWebUrl(url)) {
                index to url
            } else null
        }.take(MAX_TABS)

        if (accepted.isEmpty()) return Snapshot(listOf(homeUrl), 0)

        // Keep duplicate URLs: two separate tabs may intentionally show the same page.
        val selectedIndex = accepted.indexOfLast { it.first <= activeIndex.coerceAtLeast(0) }
            .takeIf { it >= 0 } ?: 0
        return Snapshot(accepted.map { it.second }, selectedIndex)
    }
}
