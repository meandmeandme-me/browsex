package com.browsex.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.net.Uri

/** Searchable, persistent bookmarks/history library with explicit empty and no-match states. */
internal class SavedPagesPage(
    private val activity: MainActivity,
    private val store: BrowserStore,
    private val title: String,
    initialPages: List<String>,
    private val isHistory: Boolean,
    private val onOpen: (String) -> Unit
) {
    private val pages = initialPages.toMutableList()
    private var dismissDialog: (() -> Unit)? = null
    private val ink = Color.rgb(19, 43, 64)
    private val muted = Color.rgb(83, 105, 123)
    private val accent = Color.rgb(8, 102, 217)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    fun show() {
        lateinit var dialog: AlertDialog
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            setBackgroundColor(Color.rgb(239, 245, 250))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(activity).apply {
            text = title
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
        })
        heading.addView(TextView(activity).apply {
            text = if (isHistory) "Pages you have visited" else "Websites saved for later"
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
            contentDescription = "Close"
        }
        header.addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header)

        val search = EditText(activity).apply {
            hint = if (isHistory) "Search browsing history" else "Search bookmarks"
            textSize = 14f
            isSingleLine = true
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(15), 0, dp(15), 0)
            background = rounded(Color.WHITE, 15)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(17)
        })

        val scroll = ScrollView(activity).apply { clipToPadding = false }
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        scroll.addView(list)
        val empty = TextView(activity).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(22), dp(38), dp(22), dp(38))
            background = rounded(Color.WHITE, 16)
        }

        val tools = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(10), dp(2), dp(6))
        }
        val count = TextView(activity).apply {
            textSize = 12f
            setTextColor(muted)
        }
        tools.addView(count, LinearLayout.LayoutParams(0, -2, 1f))
        if (isHistory) {
            val clear = TextView(activity).apply {
                text = "Clear history"
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(190, 65, 75))
                setPadding(dp(10), dp(8), dp(4), dp(8))
                setOnClickListener {
                    AlertDialog.Builder(activity)
                        .setTitle("Clear browsing history?")
                        .setMessage("This removes the history saved by Browse X. Bookmarks and downloaded files are kept.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Clear") { _, _ ->
                            store.clearHistory()
                            pages.clear()
                            search.text?.clear()
                            if (search.text.isNullOrEmpty()) {
                                list.removeAllViews()
                                count.text = "0 of 0 ${if (isHistory) "pages" else "saved sites"}"
                                empty.text = if (isHistory) "Your browsing history is empty." else "No bookmarks yet. Save a page to find it here."
                                list.addView(empty, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                            }
                        }.show()
                }
            }
            tools.addView(clear)
        }
        root.addView(tools)

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun refresh(query: String) = render(query, list, count, empty)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, countBefore: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, countAfter: Int) = refresh(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        refresh("")

        dialog = AlertDialog.Builder(activity).setView(root).create()
        close.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dismissDialog = { dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun render(
        query: String,
        list: LinearLayout,
        count: TextView,
        empty: TextView
    ) {
        list.removeAllViews()
        val normalized = query.trim()
        val filtered = pages.filter { page ->
            normalized.isBlank() || page.contains(normalized, ignoreCase = true) ||
                Uri.parse(page).host.orEmpty().contains(normalized, ignoreCase = true)
        }
        count.text = "${filtered.size} of ${pages.size} ${if (isHistory) "pages" else "saved sites"}"
        if (filtered.isEmpty()) {
            empty.text = when {
                pages.isEmpty() && isHistory -> "Your browsing history is empty."
                pages.isEmpty() -> "No bookmarks yet. Save a page to find it here."
                else -> "No pages match your search."
            }
            list.addView(empty, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
            })
            return
        }
        filtered.forEach { page ->
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(12), dp(8), dp(12))
                background = rounded(Color.WHITE, 15)
                isFocusable = true
                isClickable = true
            }
            val details = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            val host = Uri.parse(page).host ?: page
            details.addView(TextView(activity).apply {
                text = host
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ink)
                maxLines = 1
            })
            details.addView(TextView(activity).apply {
                text = page
                textSize = 10f
                setTextColor(muted)
                maxLines = 2
                setPadding(0, dp(4), 0, 0)
            })
            item.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            val remove = TextView(activity).apply {
                text = "×"
                textSize = 23f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(190, 65, 75))
                contentDescription = if (isHistory) "Remove from history" else "Remove bookmark"
                setPadding(dp(12), dp(5), dp(8), dp(5))
                setOnClickListener {
                    if (isHistory) store.removeHistory(page) else store.removeBookmark(page)
                    pages.remove(page)
                    render(query, list, count, empty)
                }
            }
            item.addView(remove)
            item.setOnClickListener { onOpen(page); dismissDialog?.invoke() }
            list.addView(item, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }
    }
}
