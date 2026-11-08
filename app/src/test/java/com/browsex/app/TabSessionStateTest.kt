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
        assertEquals(1, state.activeIndex)
    }

    @Test fun selectsNearestSurvivingTabWhenOriginalActiveTabWasRejected() {
        val state = TabSessionState.normalize(
            listOf("https://before.example", "file:///private", "https://after.example"),
            1,
            home
        )
        assertEquals(listOf("https://before.example", "https://after.example"), state.urls)
        assertEquals(1, state.activeIndex)
    }

    @Test fun acceptsOnlyKnownHomePreferenceParameters() {
        val localized = "$home?bxlang=ar&bxtheme=dark"
        val invalid = "$home?bxlang=ar&bxtheme=dark&redirect=https://evil.example"
        assertEquals(
            listOf(localized),
            TabSessionState.normalize(listOf(localized, invalid), 0, home).urls
        )
    }

    @Test fun acceptsAnyValidBcp47HomeLanguageTag() {
        val french = "$home?bxlang=fr-FR&bxtheme=dark"
        val traditionalChinese = "$home?bxlang=zh-Hant-TW&bxtheme=light"
        val invalid = "$home?bxlang=not_a_locale&bxtheme=dark"
        assertEquals(
            listOf(french, traditionalChinese),
            TabSessionState.normalize(listOf(french, traditionalChinese, invalid), 0, home).urls
        )
    }

    @Test fun acceptsOnlyKnownHomeCustomizationParameters() {
        val customized = "$home?bxlang=ar&bxtheme=dark&bxquick=false&bxdiscovery=true&bxlayout=list"
        val invalid = "$home?bxlang=ar&bxtheme=dark&bxdebug=true"
        assertEquals(listOf(customized), TabSessionState.normalize(listOf(customized, invalid), 0, home).urls)
    }

    @Test fun restoresTvAndAccessibilityHomePreferencesButRejectsMalformedFlags() {
        val adaptive = "$home?bxlang=ar&bxtheme=dark&bxtv=true&bxopaque=true&bxcontrast=true&bxmotion=true"
        val malformed = "$home?bxlang=ar&bxtheme=dark&bxtv=true&bxmotion=yes"

        assertEquals(
            listOf(adaptive),
            TabSessionState.normalize(listOf(adaptive, malformed), 0, home).urls
        )
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

    @Test fun keepsDuplicateUrlsBecauseTheyCanRepresentSeparateTabs() {
        val state = TabSessionState.normalize(
            listOf("https://example.com", "https://example.com", home),
            1,
            home
        )
        assertEquals(listOf("https://example.com", "https://example.com", home), state.urls)
        assertEquals(1, state.activeIndex)
    }

    @Test fun activeTabIndexTracksOriginalPositionWhenEarlierEntriesAreRejected() {
        val state = TabSessionState.normalize(
            listOf("javascript:alert(1)", "https://one.example", "file:///etc/passwd", "https://two.example"),
            3,
            home
        )
        assertEquals(listOf("https://one.example", "https://two.example"), state.urls)
        assertEquals(1, state.activeIndex)
    }
}
