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
    fun fallsBackToSystemForInvalidLanguageTags() {
        assertEquals("system", BrowserPreferencePolicy.normalizeLanguageTag(null))
        assertEquals("system", BrowserPreferencePolicy.normalizeLanguageTag("system"))
        assertEquals("system", BrowserPreferencePolicy.normalizeLanguageTag("x"))
        assertEquals("system", BrowserPreferencePolicy.normalizeLanguageTag("en_US"))
        assertEquals("system", BrowserPreferencePolicy.normalizeLanguageTag("https://example.com"))
    }

    @Test
    fun normalizesThemeAndHomeLayoutToSupportedValues() {
        assertEquals("dark", BrowserPreferencePolicy.normalizeTheme("dark"))
        assertEquals("system", BrowserPreferencePolicy.normalizeTheme("contrast"))
        assertEquals("list", BrowserPreferencePolicy.normalizeHomeLayout("list"))
        assertEquals("grid", BrowserPreferencePolicy.normalizeHomeLayout("masonry"))
    }
}
