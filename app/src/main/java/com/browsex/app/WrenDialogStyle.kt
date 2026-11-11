package com.browsex.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.browsex.app.storage.BrowserSettings

/** Shared native dialog styling so system dialogs follow the same Wren surface and contrast tokens. */
internal object WrenDialogStyle {
    fun apply(dialog: AlertDialog, context: Context, settings: BrowserSettings? = null) {
        val resolvedSettings = settings ?: (context as? MainActivity)?.currentBrowserSettings
            ?: BrowserSettings()
        val colors = BrowseXColors.resolve(context, resolvedSettings)
        val radius = WrenAdaptiveUi.dp(context, 24).toFloat()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                setColor(colors.card)
                cornerRadius = radius
                setStroke(WrenAdaptiveUi.dp(context, 1), colors.line)
            }
        )
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(colors.ink)
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(colors.muted)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(colors.accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(colors.muted)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(colors.accent)
        dialog.listView?.let { list ->
            list.setBackgroundColor(colors.card)
            list.divider = android.graphics.drawable.ColorDrawable(colors.line)
            list.dividerHeight = WrenAdaptiveUi.dp(context, 1)
            for (index in 0 until list.childCount) {
                val row = list.getChildAt(index)
                row.setBackgroundColor(colors.card)
                row.findViewById<TextView>(android.R.id.text1)?.setTextColor(colors.ink)
                (row as? CompoundButton)?.buttonTintList = ColorStateList.valueOf(colors.accent)
            }
        }
    }
}
