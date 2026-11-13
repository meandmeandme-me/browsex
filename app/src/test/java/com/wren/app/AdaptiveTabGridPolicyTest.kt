package com.wren.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveTabGridPolicyTest {
    @Test
    fun phoneAndCompactLandscapeKeepTwoReadableColumns() {
        assertEquals(2, AdaptiveTabGridPolicy.columns(WrenWindowLayout.PHONE_COMPACT))
        assertEquals(2, AdaptiveTabGridPolicy.columns(WrenWindowLayout.COMPACT_LANDSCAPE))
    }

    @Test
    fun tabletGridExpandsWithAvailableWindowSpace() {
        assertEquals(3, AdaptiveTabGridPolicy.columns(WrenWindowLayout.MEDIUM))
        assertEquals(4, AdaptiveTabGridPolicy.columns(WrenWindowLayout.EXPANDED))
        assertEquals(5, AdaptiveTabGridPolicy.columns(WrenWindowLayout.LARGE))
        assertEquals(6, AdaptiveTabGridPolicy.columns(WrenWindowLayout.EXTRA_LARGE))
    }

    @Test
    fun tvUsesFiveLargeRemoteFriendlyColumns() {
        assertEquals(5, AdaptiveTabGridPolicy.columns(WrenWindowLayout.TV))
    }
}
