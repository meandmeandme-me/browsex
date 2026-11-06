package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTextNormalizerTest {
    @Test fun normalizesCompatibilityCharactersForSearch() {
        assertEquals(
            BrowserTextNormalizer.normalize("ＦＯＯ"),
            BrowserTextNormalizer.normalize("foo")
        )
    }

    @Test fun searchNormalizationIsLocaleIndependent() {
        assertEquals(
            BrowserTextNormalizer.normalize("Istanbul"),
            BrowserTextNormalizer.normalize("ISTANBUL")
        )
    }

    @Test fun preservesNonLatinScripts() {
        val arabic = BrowserTextNormalizer.normalize("مُتصفح")
        val japanese = BrowserTextNormalizer.normalize("ブラウザー")
        assertTrue(arabic.contains("متصفح") || arabic.contains("مُتصفح"))
        assertTrue(japanese.contains("ブラウザー"))
    }

    @Test fun trimsSurroundingWhitespace() {
        assertEquals(
            BrowserTextNormalizer.normalize("  hello  world "),
            BrowserTextNormalizer.normalize("hello  world")
        )
    }
}
