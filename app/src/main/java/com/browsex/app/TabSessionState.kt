package com.browsex.app

import kotlin.math.abs

object TabSessionState {
    const val MAX_TABS = 20

    data class Snapshot(val urls: List<String>, val activeIndex: Int)

    fun normalize(urls: List<String>, activeIndex: Int, homeUrl: String): Snapshot {
        val accepted = urls.mapIndexedNotNull { index, raw ->
            val url = raw.trim()
            val isConfiguredHome = url.startsWith("$homeUrl?") && run {
                val params = url.substringAfter('?').split('&').mapNotNull { part ->
                    val key = part.substringBefore('=', "")
                    val value = part.substringAfter('=', "")
                    key.takeIf { it.isNotBlank() }?.let { it to value }
                }.toMap()
                params.keys.containsAll(setOf("bxlang", "bxtheme")) &&
                    params.keys.all { it in setOf("bxlang", "bxtheme", "bxquick", "bxdiscovery", "bxlayout", "bxtv", "bxopaque", "bxcontrast", "bxmotion", "bxminimal") } &&
                    params["bxlang"]?.let { it == "system" || BrowserPreferencePolicy.normalizeLanguageTag(it) != "system" } == true &&
                    params["bxtheme"]?.let { it in setOf("system", "light", "dark") } == true &&
                    params["bxquick"]?.let { it in setOf("true", "false") } != false &&
                    params["bxdiscovery"]?.let { it in setOf("true", "false") } != false &&
                    params["bxlayout"]?.let { it in setOf("grid", "list") } != false &&
                    params["bxtv"]?.let { it in setOf("true", "false") } != false &&
                    params["bxopaque"]?.let { it in setOf("true", "false") } != false &&
                    params["bxcontrast"]?.let { it in setOf("true", "false") } != false &&
                    params["bxmotion"]?.let { it in setOf("true", "false") } != false &&
                    params["bxminimal"]?.let { it in setOf("true", "false") } != false
            }
            if (url == homeUrl || isConfiguredHome || url == "about:blank" || NavigationInput.isValidWebUrl(url)) {
                index to url
            } else null
        }.take(MAX_TABS)

        if (accepted.isEmpty()) return Snapshot(listOf(homeUrl), 0)

        // Keep duplicate URLs: two separate tabs may intentionally show the same page.
        val requestedIndex = activeIndex.coerceAtLeast(0)
        // If the selected original tab was rejected (for example a private or unsafe URL),
        // restore the nearest surviving tab. Prefer the next tab when distances are equal.
        val selectedIndex = accepted.indices.minWithOrNull(
            compareBy<Int> { abs(accepted[it].first.toLong() - requestedIndex.toLong()) }
                .thenBy { if (accepted[it].first >= requestedIndex) 0 else 1 }
        ) ?: 0
        return Snapshot(accepted.map { it.second }, selectedIndex)
    }
}
