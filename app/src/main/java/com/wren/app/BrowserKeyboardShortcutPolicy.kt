package com.wren.app

import android.view.KeyEvent

/** Browser-level keyboard accelerators for physical keyboards and Chromebook layouts. */
internal enum class BrowserKeyboardAction {
    FOCUS_ADDRESS,
    NEW_TAB,
    NEW_PRIVATE_TAB,
    CLOSE_TAB,
    RELOAD,
    FIND_IN_PAGE,
    NEXT_TAB,
    PREVIOUS_TAB,
    TOGGLE_BOOKMARK,
    OPEN_DOWNLOADS,
    OPEN_HISTORY,
    OPEN_BOOKMARKS,
    GO_BACK,
    GO_FORWARD
}

internal object BrowserKeyboardShortcutPolicy {
    fun resolve(
        keyCode: Int,
        controlPressed: Boolean,
        metaPressed: Boolean,
        shiftPressed: Boolean,
        altPressed: Boolean,
        repeatCount: Int,
        imeVisible: Boolean
    ): BrowserKeyboardAction? {
        if (imeVisible || repeatCount > 0) return null

        // Ctrl is the default convention on Windows/ChromeOS; Meta supports keyboards
        // that expose a Command/Search modifier without stealing Alt navigation.
        val primary = controlPressed || metaPressed
        if (primary && !altPressed) {
            return when {
                keyCode == KeyEvent.KEYCODE_TAB && shiftPressed -> BrowserKeyboardAction.PREVIOUS_TAB
                keyCode == KeyEvent.KEYCODE_TAB -> BrowserKeyboardAction.NEXT_TAB
                keyCode == KeyEvent.KEYCODE_L -> BrowserKeyboardAction.FOCUS_ADDRESS
                keyCode == KeyEvent.KEYCODE_N && shiftPressed -> BrowserKeyboardAction.NEW_PRIVATE_TAB
                keyCode == KeyEvent.KEYCODE_T -> BrowserKeyboardAction.NEW_TAB
                keyCode == KeyEvent.KEYCODE_W -> BrowserKeyboardAction.CLOSE_TAB
                keyCode == KeyEvent.KEYCODE_R -> BrowserKeyboardAction.RELOAD
                keyCode == KeyEvent.KEYCODE_F -> BrowserKeyboardAction.FIND_IN_PAGE
                keyCode == KeyEvent.KEYCODE_D -> BrowserKeyboardAction.TOGGLE_BOOKMARK
                keyCode == KeyEvent.KEYCODE_J -> BrowserKeyboardAction.OPEN_DOWNLOADS
                keyCode == KeyEvent.KEYCODE_H -> BrowserKeyboardAction.OPEN_HISTORY
                keyCode == KeyEvent.KEYCODE_B -> BrowserKeyboardAction.OPEN_BOOKMARKS
                else -> null
            }
        }

        if (altPressed && !controlPressed && !metaPressed && !shiftPressed) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> BrowserKeyboardAction.GO_BACK
                KeyEvent.KEYCODE_DPAD_RIGHT -> BrowserKeyboardAction.GO_FORWARD
                else -> null
            }
        }
        return null
    }
}
