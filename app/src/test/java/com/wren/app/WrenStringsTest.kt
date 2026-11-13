package com.wren.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WrenStringsTest {
    @Test
    fun arabicLocaleUsesArabicCallSiteFallback() {
        assertEquals(
            "تعذر فتح الصفحة",
            WrenStrings.fallbackForLanguage("ar", "Could not open page", "تعذر فتح الصفحة")
        )
    }

    @Test
    fun regionalArabicLocaleUsesArabicCallSiteFallback() {
        assertEquals(
            "تعذر فتح الصفحة",
            WrenStrings.fallbackForLanguage("ar-MA", "Could not open page", "تعذر فتح الصفحة")
        )
    }

    @Test
    fun otherAndUnknownLocalesUseEnglishFallback() {
        assertEquals(
            "Could not open page",
            WrenStrings.fallbackForLanguage("fr-FR", "Could not open page", "تعذر فتح الصفحة")
        )
        assertEquals(
            "Could not open page",
            WrenStrings.fallbackForLanguage(null, "Could not open page", "تعذر فتح الصفحة")
        )
    }
}
