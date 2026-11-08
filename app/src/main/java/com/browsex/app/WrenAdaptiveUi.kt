package com.browsex.app

import android.content.Context
import android.content.res.Configuration
import android.view.View

/**
 * Shared rules for screens that are not rendered by MainActivity's browser chrome.
 *
 * Keep form-factor decisions tied to the current window policy, not physical model names.
 * The Leanback/configuration fallback is used only before the Activity has a resolved snapshot.
 */
internal object WrenAdaptiveUi {
    fun isTelevision(context: Context): Boolean {
        val resolved = (context as? MainActivity)?.currentAdaptivePresentation
        if (resolved != null) return resolved.windowLayout == WrenWindowLayout.TV
        return context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION) ||
            (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
                Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** Android TV overscan-safe padding for critical controls; keep decorative backgrounds full bleed. */
    fun horizontalSafePadding(context: Context, phoneDp: Int = 18): Int =
        if (isTelevision(context)) dp(context, 48) else dp(context, phoneDp)

    fun verticalSafePadding(context: Context, phoneDp: Int = 16): Int =
        if (isTelevision(context)) dp(context, 28) else dp(context, phoneDp)

    /**
     * Makes an existing custom View reachable by D-pad/keyboard and gives it the same
     * visible focus/pressed outline used by the browser chrome. Existing backgrounds stay intact.
     */
    fun prepareFocusable(
        view: View,
        context: Context,
        radiusDp: Int,
        focusColor: Int,
        minimumTargetDp: Int = 48
    ) {
        val minimum = dp(context, minimumTargetDp)
        view.isFocusable = true
        view.isFocusableInTouchMode = false
        view.minimumWidth = maxOf(view.minimumWidth, minimum)
        view.minimumHeight = maxOf(view.minimumHeight, minimum)
        view.foreground = WrenFocusStyle.overlay(context, radiusDp, focusColor)
    }
}
