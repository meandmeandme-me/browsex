package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserPreferencePolicyTest {
    @Test
    fun normalizesValidLanguageTagsAndPreservesRegion() {
        assertEquals("ar-MA", BrowserPreferencePolicy.normalizeLanguageTag("ar-ma"))
        assertEquals("zh-Hant-TW", BrowserPreferencePolicy.normalizeLanguageTag("zh-hant-tw"))
    }

    @Test
    fun normalizesSearchEngineAndRejectsUnsupportedValues() {
        assertEquals("Google", BrowserPreferencePolicy.normalizeSearchEngine(null))
        assertEquals("Google", BrowserPreferencePolicy.normalizeSearchEngine("Other"))
        assertEquals("Bing", BrowserPreferencePolicy.normalizeSearchEngine(" Bing "))
        assertEquals("DuckDuckGo", BrowserPreferencePolicy.normalizeSearchEngine("DuckDuckGo"))
        assertEquals("Brave", BrowserPreferencePolicy.normalizeSearchEngine("Brave"))
        assertEquals("Startpage", BrowserPreferencePolicy.normalizeSearchEngine("Startpage"))
    }

    @Test
    fun exposesEverySupportedSearchEngineInStableOrder() {
        assertEquals(
            listOf("Google", "DuckDuckGo", "Bing", "Brave", "Ecosia", "Startpage", "Yahoo"),
            BrowserPreferencePolicy.supportedSearchEngines()
        )
        BrowserPreferencePolicy.supportedSearchEngines().forEach { engine ->
            assertEquals(engine, BrowserPreferencePolicy.normalizeSearchEngine(engine))
        }
    }

    @Test
    fun fallsBackToSystemForInvalidLanguageTags() {
        assertEquals("system", BrowserPreferencePolicy.normalizeLanguageTag(null))
        assertEquals("system", BrowserPreferencePolicy.normalizeLanguageTag("system"))
        assertEquals("system", BrowserPreferencePolicy.normalizeLanguageTag("x"))
        assertEquals("system", BrowserPreferencePolicy.normalizeLanguageTag("en_US"))
        assertEquals("system", BrowserPreferencePolicy.normalizeLanguageTag("https://example.com"))
    }

    @Test
    fun normalizesAddressBarPositionAndFallsBackToAutomatic() {
        assertEquals("top", BrowserPreferencePolicy.normalizeAddressBarPosition("TOP"))
        assertEquals("bottom", BrowserPreferencePolicy.normalizeAddressBarPosition("bottom"))
        assertEquals("auto", BrowserPreferencePolicy.normalizeAddressBarPosition(null))
        assertEquals("auto", BrowserPreferencePolicy.normalizeAddressBarPosition("floating"))
    }

    @Test
    fun normalizesThemeAndHomeLayoutToSupportedValues() {
        assertEquals("dark", BrowserPreferencePolicy.normalizeTheme("dark"))
        assertEquals("system", BrowserPreferencePolicy.normalizeTheme("contrast"))
        assertEquals("list", BrowserPreferencePolicy.normalizeHomeLayout("list"))
        assertEquals("grid", BrowserPreferencePolicy.normalizeHomeLayout("masonry"))
    }
}
