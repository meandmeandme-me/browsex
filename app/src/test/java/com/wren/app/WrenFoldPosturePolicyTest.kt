package com.wren.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WrenFoldPosturePolicyTest {
    @Test
    fun halfOpenedHorizontalFoldIsTabletop() {
        assertEquals(
            WrenFoldPosture.TABLETOP,
            WrenFoldPosturePolicy.resolve(true, WrenFoldOrientation.HORIZONTAL, true)
        )
    }

    @Test
    fun halfOpenedVerticalFoldIsBookPosture() {
        assertEquals(
            WrenFoldPosture.BOOK,
            WrenFoldPosturePolicy.resolve(true, WrenFoldOrientation.VERTICAL, true)
        )
    }

    @Test
    fun flatDualScreenVerticalHingeIsStillSeparating() {
        assertEquals(
            WrenFoldPosture.SEPARATING_VERTICAL,
            WrenFoldPosturePolicy.resolve(false, WrenFoldOrientation.VERTICAL, true)
        )
    }

    @Test
    fun flatNonSeparatingFeatureDoesNotChangeBrowserLayout() {
        assertEquals(
            WrenFoldPosture.NONE,
            WrenFoldPosturePolicy.resolve(false, WrenFoldOrientation.HORIZONTAL, false)
        )
    }
}
