package com.wren.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoSession

/**
 * Owns transient page actions that do not belong in the Activity's lifecycle or
 * browser-session composition root. UI copy and interaction behavior are unchanged.
 */
internal class BrowserPageActionsCoordinator(
    private val activity: AppCompatActivity,
    private val activeSession: () -> GeckoSession?,
    private val activeUrl: () -> String?,
    private val currentSearchEngine: () -> String,
    private val updateSearchEngine: (String) -> Unit,
    private val showMessage: (String) -> Unit,
    private val mutedColor: () -> Int,
    private val dp: (Int) -> Int
) {
    fun chooseSearchEngine() {
        val choices = arrayOf("Google", "DuckDuckGo", "Bing")
        AlertDialog.Builder(activity).setTitle("Default search engine")
            .setSingleChoiceItems(choices, choices.indexOf(currentSearchEngine()).coerceAtLeast(0)) { dialog, which ->
                updateSearchEngine(choices[which])
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }

    fun shareCurrentPage() {
        val url = activeUrl().orEmpty()
        if (!url.startsWith("http")) return
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }, "Share page"))
    }

    /** Uses GeckoView's native finder so matches are highlighted in page content. */
    fun findInPage() {
        val session = activeSession() ?: return
        val field = EditText(activity).apply {
            hint = "Find text on this page"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val count = TextView(activity).apply {
            text = "Type to search"
            setTextColor(mutedColor())
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(field)
            addView(count)
        }
        val dialog = AlertDialog.Builder(activity).setTitle("Find in page").setView(panel)
            .setNegativeButton("Close") { _, _ -> session.getFinder().clear() }
            .setNeutralButton("Next", null)
            .setPositiveButton("Previous", null)
            .create()
        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, countBefore: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, countAfter: Int) {
                val query = s?.toString().orEmpty()
                if (query.isBlank()) {
                    session.getFinder().clear()
                    count.text = "Type to search"
                } else {
                    session.getFinder().find(query, GeckoSession.FINDER_FIND_FORWARD)
                    count.text = "Searching page…"
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                session.getFinder().find(null, GeckoSession.FINDER_FIND_FORWARD)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                session.getFinder().find(null, GeckoSession.FINDER_FIND_BACKWARDS)
            }
        }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        field.requestFocus()
    }

    fun copyCurrentAddress() {
        val url = activeUrl().orEmpty()
        if (!url.startsWith("http")) return
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Page address", url))
        showMessage("Address copied")
    }
}
