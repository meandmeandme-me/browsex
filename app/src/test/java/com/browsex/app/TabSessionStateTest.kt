package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TabSessionStateTest {
    private val home = "resource://android/assets/home.html"

    @Test fun restoresSafeTabsAndKeepsActiveIndex() {
        val state = TabSessionState.normalize(
            listOf("https://example.com", "javascript:alert(1)", home, "https://openai.com"),
            2,
            home
        )
        assertEquals(listOf("https://example.com", home, "https://openai.com"), state.urls)
        assertEquals(2, state.activeIndex)
    }

    @Test fun invalidAndEmptySnapshotsFallBackToHome() {
        assertEquals(TabSessionState.Snapshot(listOf(home), 0),
            TabSessionState.normalize(listOf("file:///etc/passwd"), 99, home))
        assertEquals(TabSessionState.Snapshot(listOf(home), 0),
            TabSessionState.normalize(emptyList(), -1, home))
    }

    @Test fun capsRestoredTabsAndClampsActiveIndex() {
        val urls = (1..30).map { "https://site$it.example" }
        val state = TabSessionState.normalize(urls, 28, home)
        assertEquals(TabSessionState.MAX_TABS, state.urls.size)
        assertEquals(TabSessionState.MAX_TABS - 1, state.activeIndex)
    }
}
