package com.browsex.app

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserKeyboardShortcutPolicyTest {
    private fun resolve(
        key: Int,
        ctrl: Boolean = true,
        meta: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        repeat: Int = 0,
        ime: Boolean = false
    ) = BrowserKeyboardShortcutPolicy.resolve(key, ctrl, meta, shift, alt, repeat, ime)

    @Test
    fun standardBrowserShortcutsResolveToExpectedActions() {
        assertEquals(BrowserKeyboardAction.FOCUS_ADDRESS, resolve(KeyEvent.KEYCODE_L))
        assertEquals(BrowserKeyboardAction.NEW_TAB, resolve(KeyEvent.KEYCODE_T))
        assertEquals(BrowserKeyboardAction.CLOSE_TAB, resolve(KeyEvent.KEYCODE_W))
        assertEquals(BrowserKeyboardAction.RELOAD, resolve(KeyEvent.KEYCODE_R))
        assertEquals(BrowserKeyboardAction.FIND_IN_PAGE, resolve(KeyEvent.KEYCODE_F))
        assertEquals(BrowserKeyboardAction.TOGGLE_BOOKMARK, resolve(KeyEvent.KEYCODE_D))
        assertEquals(BrowserKeyboardAction.OPEN_DOWNLOADS, resolve(KeyEvent.KEYCODE_J))
        assertEquals(BrowserKeyboardAction.OPEN_HISTORY, resolve(KeyEvent.KEYCODE_H))
        assertEquals(BrowserKeyboardAction.OPEN_BOOKMARKS, resolve(KeyEvent.KEYCODE_B))
    }

    @Test
    fun tabCyclingAndPrivateTabShortcutsRespectShift() {
        assertEquals(BrowserKeyboardAction.NEXT_TAB, resolve(KeyEvent.KEYCODE_TAB))
        assertEquals(BrowserKeyboardAction.PREVIOUS_TAB, resolve(KeyEvent.KEYCODE_TAB, shift = true))
        assertEquals(BrowserKeyboardAction.NEW_PRIVATE_TAB, resolve(KeyEvent.KEYCODE_N, shift = true))
    }

    @Test
    fun metaModifierAlsoWorksForDesktopKeyboards() {
        assertEquals(
            BrowserKeyboardAction.FOCUS_ADDRESS,
            resolve(KeyEvent.KEYCODE_L, ctrl = false, meta = true)
        )
    }

    @Test
    fun imeAndRepeatedKeyEventsAreNotCaptured() {
        assertNull(resolve(KeyEvent.KEYCODE_T, ime = true))
        assertNull(resolve(KeyEvent.KEYCODE_T, repeat = 1))
        assertNull(resolve(KeyEvent.KEYCODE_T, ctrl = false))
    }

    @Test
    fun altNavigationUsesBrowserHistory() {
        assertEquals(
            BrowserKeyboardAction.GO_BACK,
            resolve(KeyEvent.KEYCODE_DPAD_LEFT, ctrl = false, alt = true)
        )
        assertEquals(
            BrowserKeyboardAction.GO_FORWARD,
            resolve(KeyEvent.KEYCODE_DPAD_RIGHT, ctrl = false, alt = true)
        )
        assertNull(resolve(KeyEvent.KEYCODE_DPAD_LEFT, ctrl = true, alt = true))
    }
}
