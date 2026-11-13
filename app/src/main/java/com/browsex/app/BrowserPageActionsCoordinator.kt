package com.browsex.app

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
        val choices = BrowserPreferencePolicy.supportedSearchEngines().toTypedArray()
        AlertDialog.Builder(activity).setTitle(activity.getString(R.string.ui_default_search_engine))
            .setSingleChoiceItems(choices, choices.indexOf(currentSearchEngine()).coerceAtLeast(0)) { dialog, which ->
                updateSearchEngine(choices[which])
                dialog.dismiss()
            }.setNegativeButton(activity.getString(R.string.ui_cancel), null).show()
    }

    fun shareCurrentPage() {
        val url = activeUrl().orEmpty()
        if (!url.startsWith("http")) return
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }, activity.getString(R.string.ui_share_page)))
    }

    /** Uses GeckoView's native finder so matches are highlighted in page content. */
    fun findInPage() {
        val session = activeSession() ?: return
        val field = EditText(activity).apply {
            hint = activity.getString(R.string.ui_find_text_on_page)
            contentDescription = activity.getString(R.string.ui_find_text_on_page)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val count = TextView(activity).apply {
            text = activity.getString(R.string.ui_type_to_search)
            setTextColor(mutedColor())
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(field)
            addView(count)
        }
        val dialog = AlertDialog.Builder(activity).setTitle(activity.getString(R.string.ui_find_in_page)).setView(panel)
            .setNegativeButton(activity.getString(R.string.ui_close)) { _, _ -> session.getFinder().clear() }
            .setNeutralButton(activity.getString(R.string.ui_next), null)
            .setPositiveButton(activity.getString(R.string.ui_previous), null)
            .create()
        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, countBefore: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, countAfter: Int) {
                val query = s?.toString().orEmpty()
                if (query.isBlank()) {
                    session.getFinder().clear()
                    count.text = activity.getString(R.string.ui_type_to_search)
                } else {
                    session.getFinder().find(query, GeckoSession.FINDER_FIND_FORWARD)
                    count.text = activity.getString(R.string.ui_searching_page)
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
        // Clear highlighted matches for every dismissal path (button, back, or outside tap).
        dialog.setOnDismissListener { session.getFinder().clear() }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        field.requestFocus()
    }

    fun copyCurrentAddress() {
        val url = activeUrl().orEmpty()
        if (!url.startsWith("http")) return
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.ui_page_address), url))
        showMessage(activity.getString(R.string.ui_address_copied))
    }
}
