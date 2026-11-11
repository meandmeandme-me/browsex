package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseXStringsTest {
    @Test
    fun arabicLocaleUsesArabicCallSiteFallback() {
        assertEquals(
            "تعذر فتح الصفحة",
            BrowseXStrings.fallbackForLanguage("ar", "Could not open page", "تعذر فتح الصفحة")
        )
    }

    @Test
    fun regionalArabicLocaleUsesArabicCallSiteFallback() {
        assertEquals(
            "تعذر فتح الصفحة",
            BrowseXStrings.fallbackForLanguage("ar-MA", "Could not open page", "تعذر فتح الصفحة")
        )
    }

    @Test
    fun otherAndUnknownLocalesUseEnglishFallback() {
        assertEquals(
            "Could not open page",
            BrowseXStrings.fallbackForLanguage("fr-FR", "Could not open page", "تعذر فتح الصفحة")
        )
        assertEquals(
            "Could not open page",
            BrowseXStrings.fallbackForLanguage(null, "Could not open page", "تعذر فتح الصفحة")
        )
    }
}
