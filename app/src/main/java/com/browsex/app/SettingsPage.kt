package com.browsex.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * Full-screen settings destination. Browser actions are injected from the activity so
 * this screen never owns GeckoSession or reaches into another feature's UI.
 */
internal class SettingsPage(
    private val activity: MainActivity,
    private val store: BrowserStore,
    private val openBookmarks: () -> Unit,
    private val openHistory: () -> Unit,
    private val openDownloads: () -> Unit,
    private val clearHistory: () -> Unit
) {
    private val ink = Color.rgb(19, 43, 64)
    private val muted = Color.rgb(83, 105, 123)
    private val accent = Color.rgb(8, 102, 217)
    private val canvas = Color.rgb(239, 245, 250)

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    fun show() {
        lateinit var dialog: AlertDialog
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(canvas)
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(activity).apply {
            text = "Settings"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
        })
        heading.addView(TextView(activity).apply {
            text = "Make Browse X work the way you like"
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(activity).apply {
            text = "×"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(ink)
            background = rounded(Color.WHITE, 14)
            contentDescription = "Close settings"
        }
        header.addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))
        page.addView(header)

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(18), 0, dp(8))
        }
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        fun section(title: String, subtitle: String? = null) {
            content.addView(TextView(activity).apply {
                text = title.uppercase()
                textSize = 11f
                letterSpacing = .08f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(muted)
                setPadding(dp(3), dp(17), dp(3), dp(8))
            })
            if (subtitle != null) content.addView(TextView(activity).apply {
                text = subtitle
                textSize = 12f
                setTextColor(muted)
                setPadding(dp(3), 0, dp(3), dp(8))
            })
        }

        fun row(title: String, description: String, trailing: String = "›", action: () -> Unit) {
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(13), dp(12), dp(13))
                background = rounded(Color.WHITE, 15)
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
            val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(activity).apply {
                text = title
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ink)
            })
            labels.addView(TextView(activity).apply {
                text = description
                textSize = 11f
                setTextColor(muted)
                setPadding(0, dp(4), 0, 0)
            })
            item.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            item.addView(TextView(activity).apply {
                text = trailing
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(accent)
            })
            content.addView(item, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }

        section("General", "Choose how you browse")
        row("Search engine", "Currently: ${store.searchEngine}", "Change") {
            val engines = arrayOf("Google", "DuckDuckGo", "Bing")
            AlertDialog.Builder(activity)
                .setTitle("Default search engine")
                .setSingleChoiceItems(engines, engines.indexOf(store.searchEngine).coerceAtLeast(0)) { choice, index ->
                    store.searchEngine = engines[index]
                    choice.dismiss()
                    dialog.dismiss()
                    show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        section("Your library", "Your saved pages stay on this device")
        row("Bookmarks", "Open and manage saved websites", action = {
            dialog.dismiss()
            openBookmarks()
        })
        row("Browsing history", "Review or remove visited pages", action = {
            dialog.dismiss()
            openHistory()
        })
        row("Downloads", "Open downloaded files", action = {
            dialog.dismiss()
            openDownloads()
        })

        section("Privacy & data")
        row("Clear browsing history", "Remove the browsing history stored by Browse X", "Clear") {
            AlertDialog.Builder(activity)
                .setTitle("Clear browsing history?")
                .setMessage("This removes the history list saved by Browse X. It does not delete downloaded files or bookmarks.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    clearHistory()
                    dialog.dismiss()
                    show()
                }
                .show()
        }

        section("About")
        row("About Browse X", "A calmer way to explore the web", "ⓘ") {
            AlertDialog.Builder(activity)
                .setTitle("Browse X")
                .setMessage("Browse X is powered by Mozilla GeckoView. Some browser features are still being built.")
                .setPositiveButton("Done", null)
                .show()
        }

        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = TextView(activity).apply {
            text = "Browse X  ·  Built for the open web"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(10), 0, dp(2))
        }
        page.addView(footer)

        dialog = AlertDialog.Builder(activity).setView(page).create()
        close.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
