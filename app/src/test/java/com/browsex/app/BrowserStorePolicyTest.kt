package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserStorePolicyTest {
    @Test fun normalizesSearchEngineToSupportedValues() {
        assertEquals("Google", BrowserStorePolicy.normalizeSearchEngine(null))
        assertEquals("Google", BrowserStorePolicy.normalizeSearchEngine("Unknown"))
        assertEquals("Bing", BrowserStorePolicy.normalizeSearchEngine(" Bing "))
        assertEquals("DuckDuckGo", BrowserStorePolicy.normalizeSearchEngine("DuckDuckGo"))
    }

    @Test fun storedUrlsAreTrimmedValidatedAndDeduplicatedInOrder() {
        val urls = BrowserStorePolicy.normalizeUrls(
            listOf(
                " https://example.com ",
                "javascript:alert(1)",
                "https://example.com",
                "file:///etc/passwd",
                "http://localhost:8080/path",
                "https://user:secret@example.org"
            ),
            BrowserStorePolicy.MAX_BOOKMARKS
        )
        assertEquals(listOf("https://example.com", "http://localhost:8080/path"), urls)
    }

    @Test fun storedUrlsRespectTheConfiguredLimit() {
        val urls = (1..(BrowserStorePolicy.MAX_HISTORY + 50))
            .map { "https://site$it.example" }
        val normalized = BrowserStorePolicy.normalizeUrls(urls, BrowserStorePolicy.MAX_HISTORY)
        assertEquals(BrowserStorePolicy.MAX_HISTORY, normalized.size)
        assertEquals("https://site1.example", normalized.first())
        assertEquals("https://site300.example", normalized.last())
    }

    @Test fun nonPositiveLimitReturnsNoUrls() {
        assertTrue(BrowserStorePolicy.normalizeUrls(listOf("https://example.com"), 0).isEmpty())
    }
}
