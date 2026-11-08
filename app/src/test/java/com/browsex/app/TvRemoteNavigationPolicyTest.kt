package com.browsex.app

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRemoteNavigationPolicyTest {
    @Test
    fun addressMovesToNavigationControlsBeforeEnteringThePage() {
        assertEquals(
            TvFocusTarget.NAVIGATION_CONTROLS,
            TvRemoteNavigationPolicy.moveDownFromAddress(
                hasVisibleNavigationControls = true,
                imeVisible = false
            )
        )
        assertEquals(
            TvFocusTarget.WEB_CONTENT,
            TvRemoteNavigationPolicy.moveDownFromAddress(
                hasVisibleNavigationControls = false,
                imeVisible = false
            )
        )
    }

    @Test
    fun keyboardRetainsControlOfDirectionalKeys() {
        assertEquals(
            TvFocusTarget.NONE,
            TvRemoteNavigationPolicy.moveDownFromAddress(
                hasVisibleNavigationControls = true,
                imeVisible = true
            )
        )
        assertEquals(
            TvFocusTarget.NONE,
            TvRemoteNavigationPolicy.moveFromNavigation(
                KeyEvent.KEYCODE_DPAD_DOWN,
                imeVisible = true
            )
        )
        assertFalse(TvRemoteNavigationPolicy.shouldReturnToChromeFromWeb(scrollY = 0, imeVisible = true))
    }

    @Test
    fun navigationRowMovesUpToAddressAndDownToPage() {
        assertEquals(
            TvFocusTarget.ADDRESS,
            TvRemoteNavigationPolicy.moveFromNavigation(KeyEvent.KEYCODE_DPAD_UP, imeVisible = false)
        )
        assertEquals(
            TvFocusTarget.WEB_CONTENT,
            TvRemoteNavigationPolicy.moveFromNavigation(KeyEvent.KEYCODE_DPAD_DOWN, imeVisible = false)
        )
    }

    @Test
    fun webContentReturnsToChromeOnlyAtTopOfDocument() {
        assertTrue(TvRemoteNavigationPolicy.shouldReturnToChromeFromWeb(scrollY = 0, imeVisible = false))
        assertTrue(TvRemoteNavigationPolicy.shouldReturnToChromeFromWeb(scrollY = -1, imeVisible = false))
        assertFalse(TvRemoteNavigationPolicy.shouldReturnToChromeFromWeb(scrollY = 1, imeVisible = false))
        assertFalse(TvRemoteNavigationPolicy.shouldReturnToChromeFromWeb(scrollY = 240, imeVisible = false))
    }
}
