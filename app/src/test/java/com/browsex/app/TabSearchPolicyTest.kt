package com.browsex.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabSearchPolicyTest {
    @Test fun matchesArabicTextWithOrWithoutHarakat() {
        assertTrue(TabSearchPolicy.matches("مُتصفّح الإنترنت", "", "متصفح"))
        assertTrue(TabSearchPolicy.matches("متصفح الإنترنت", "", "مُتَصَفِّح"))
    }

    @Test fun ignoresTatweelAndRepeatedWhitespace() {
        assertTrue(TabSearchPolicy.matches("BrowseX ـ Browser", "", "browsex browser"))
    }

    @Test fun searchesBothTitleAndUrl() {
        assertTrue(TabSearchPolicy.matches("Example", "https://openai.com", "openai"))
        assertFalse(TabSearchPolicy.matches("Example", "https://example.com", "missing"))
    }

    @Test fun emptyQueryMatchesEveryTab() {
        assertTrue(TabSearchPolicy.matches("Any title", "https://example.com", "   "))
    }
}
