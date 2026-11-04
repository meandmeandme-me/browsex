package com.browsex.app

/** Pure validation and bounds for persisted browser data. */
internal object BrowserStorePolicy {
    const val MAX_BOOKMARKS = 200
    const val MAX_HISTORY = 300
    private const val MAX_URL_LENGTH = 4096

    private val searchEngines = setOf("Google", "DuckDuckGo", "Bing")

    fun normalizeSearchEngine(value: String?): String =
        value?.trim()?.takeIf(searchEngines::contains) ?: "Google"

    /**
     * Keeps only credential-free HTTP(S) URLs, trims whitespace, removes duplicates,
     * and preserves the newest/first-seen ordering while enforcing a hard storage cap.
     */
    fun normalizeUrls(values: Iterable<String>, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val result = LinkedHashSet<String>(minOf(limit, 32))
        for (raw in values) {
            val url = raw.trim()
            if (url.length > MAX_URL_LENGTH || !NavigationInput.isValidWebUrl(url)) continue
            result.add(url)
            if (result.size >= limit) break
        }
        return result.toList()
    }
}
