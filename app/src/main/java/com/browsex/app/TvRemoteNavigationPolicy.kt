package com.browsex.app

/** Small, testable rules for moving between Wren chrome and Gecko content with a TV remote. */
internal enum class TvFocusTarget {
    ADDRESS,
    NAVIGATION_CONTROLS,
    WEB_CONTENT,
    NONE
}

internal object TvRemoteNavigationPolicy {
    fun moveDownFromAddress(hasVisibleNavigationControls: Boolean, imeVisible: Boolean): TvFocusTarget =
        when {
            imeVisible -> TvFocusTarget.NONE
            hasVisibleNavigationControls -> TvFocusTarget.NAVIGATION_CONTROLS
            else -> TvFocusTarget.WEB_CONTENT
        }

    fun moveFromNavigation(direction: Int, imeVisible: Boolean): TvFocusTarget {
        if (imeVisible) return TvFocusTarget.NONE
        return when (direction) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> TvFocusTarget.ADDRESS
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> TvFocusTarget.WEB_CONTENT
            else -> TvFocusTarget.NONE
        }
    }

    /**
     * GeckoView renders its document in a SurfaceView; View.canScrollVertically() is not a
     * reliable reading of the document's scroll offset. Use GeckoSession.ScrollDelegate data.
     */
    fun shouldReturnToChromeFromWeb(scrollY: Int, imeVisible: Boolean): Boolean =
        !imeVisible && scrollY <= 0
}
