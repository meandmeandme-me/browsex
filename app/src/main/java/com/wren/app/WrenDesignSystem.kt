package com.wren.app

import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shared Wren component geometry.
 *
 * One shape scale is applied at the browser shell so the address dock, sheets,
 * settings groups, tab cards, and feature panels read as one product rather than
 * a collection of unrelated screens.
 */
internal object WrenDesignSystem {
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(26.dp),
        extraLarge = RoundedCornerShape(32.dp)
    )

    const val PHONE_CONTENT_MAX_WIDTH_DP = 760
    const val TABLET_CONTENT_MAX_WIDTH_DP = 960
    const val MIN_TOUCH_TARGET_DP = 48
}
