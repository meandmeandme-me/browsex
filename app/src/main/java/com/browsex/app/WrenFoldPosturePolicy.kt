package com.browsex.app

/** Fold states relevant to browser chrome placement, not hardware-model names. */
internal enum class WrenFoldPosture {
    NONE,
    TABLETOP,
    BOOK,
    SEPARATING_HORIZONTAL,
    SEPARATING_VERTICAL
}

internal enum class WrenFoldOrientation { HORIZONTAL, VERTICAL }

internal object WrenFoldPosturePolicy {
    fun resolve(
        isHalfOpened: Boolean,
        orientation: WrenFoldOrientation,
        isSeparating: Boolean
    ): WrenFoldPosture = when {
        isHalfOpened && orientation == WrenFoldOrientation.HORIZONTAL -> WrenFoldPosture.TABLETOP
        isHalfOpened && orientation == WrenFoldOrientation.VERTICAL -> WrenFoldPosture.BOOK
        isSeparating && orientation == WrenFoldOrientation.VERTICAL -> WrenFoldPosture.SEPARATING_VERTICAL
        isSeparating && orientation == WrenFoldOrientation.HORIZONTAL -> WrenFoldPosture.SEPARATING_HORIZONTAL
        else -> WrenFoldPosture.NONE
    }
}
