package com.browsex.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

/**
 * Consistent keyboard / D-pad focus outline for existing Wren controls.
 * The overlay leaves each component's normal background intact.
 */
internal object WrenFocusStyle {
    fun overlay(context: Context, radiusDp: Int, focusColor: Int): StateListDrawable {
        val density = context.resources.displayMetrics.density
        val radius = radiusDp * density

        fun outline(widthDp: Int): GradientDrawable = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = radius
            setStroke((widthDp * density).toInt().coerceAtLeast(1), focusColor)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), outline(2))
            addState(intArrayOf(android.R.attr.state_pressed), outline(2))
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }
}
