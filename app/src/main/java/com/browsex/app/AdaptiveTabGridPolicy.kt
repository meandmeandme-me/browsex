package com.browsex.app

/** Column count for the tab workspace, resolved from the current window class, not the device model. */
internal object AdaptiveTabGridPolicy {
    fun columns(layout: WrenWindowLayout): Int = when (layout) {
        WrenWindowLayout.PHONE_COMPACT,
        WrenWindowLayout.COMPACT_LANDSCAPE -> 2
        WrenWindowLayout.MEDIUM -> 3
        WrenWindowLayout.EXPANDED -> 4
        WrenWindowLayout.LARGE -> 5
        WrenWindowLayout.EXTRA_LARGE -> 6
        WrenWindowLayout.TV -> 5
    }
}
