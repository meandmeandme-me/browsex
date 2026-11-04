package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserTabPersistencePolicyTest {
    private val home = "resource://android/assets/home.html"

    @Test fun excludesPrivateTabsAndRemapsSelectedRegularIndex() {
        val snapshot = BrowserTabPersistencePolicy.snapshot(
            urls = listOf("https://one.example", "https://private.example", "https://two.example"),
            privateFlags = listOf(false, true, false),
            activeIndex = 2,
            homeUrl = home
        )
        assertEquals(listOf("https://one.example", "https://two.example"), snapshot.urls)
        assertEquals(1, snapshot.activeIndex)
    }

    @Test fun privateActiveTabFallsBackToFirstRegularTab() {
        val snapshot = BrowserTabPersistencePolicy.snapshot(
            urls = listOf("https://one.example", "https://private.example", "https://two.example"),
            privateFlags = listOf(false, true, false),
            activeIndex = 1,
            homeUrl = home
        )
        assertEquals(0, snapshot.activeIndex)
    }

    @Test fun duplicateRegularUrlsRemainSeparateTabs() {
        val snapshot = BrowserTabPersistencePolicy.snapshot(
            urls = listOf("https://same.example", "https://same.example"),
            privateFlags = listOf(false, false),
            activeIndex = 1,
            homeUrl = home
        )
        assertEquals(listOf("https://same.example", "https://same.example"), snapshot.urls)
        assertEquals(1, snapshot.activeIndex)
    }

    @Test fun privateOnlySnapshotRestoresHome() {
        val snapshot = BrowserTabPersistencePolicy.snapshot(
            urls = listOf("https://private.example"),
            privateFlags = listOf(true),
            activeIndex = 0,
            homeUrl = home
        )
        assertEquals(TabSessionState.Snapshot(listOf(home), 0), snapshot)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchedTabMetadata() {
        BrowserTabPersistencePolicy.snapshot(
            urls = listOf("https://one.example"),
            privateFlags = emptyList(),
            activeIndex = 0,
            homeUrl = home
        )
    }
}
