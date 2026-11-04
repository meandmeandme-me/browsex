package com.browsex.app

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/** One searchable surface for returning to open regular tabs, bookmarks and recent history. */
internal class RecallPage(
    private val activity: MainActivity,
    private val store: BrowserStore,
    private val openTabs: List<Pair<String, String>>,
    private val onOpen: (String) -> Unit
) {
    private val colors get() = BrowseXColors.resolve(activity, store)
    private fun t(en: String, ar: String) = if (BrowseXColors.isArabic(store)) ar else en
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
            setBackgroundColor(colors.canvas)
            BrowseXColors.applyDirection(this, store)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(activity).apply {
            text = t("Find anything", "اعثر على أي شيء")
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.ink)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(activity).apply {
            text = "×"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(colors.ink)
            background = rounded(colors.card, 14)
            contentDescription = t("Close", "إغلاق")
            minimumWidth = dp(48)
            minimumHeight = dp(48)
        }
        header.addView(close)
        root.addView(header)
        root.addView(TextView(activity).apply {
            text = t(
                "Search your open tabs, bookmarks and recent history in one place.",
                "ابحث في التبويبات المفتوحة والمفضلة والسجل الحديث من مكان واحد."
            )
            textSize = 12f
            setTextColor(colors.muted)
            setPadding(0, dp(4), 0, dp(12))
        })
        val search = EditText(activity).apply {
            hint = t("Search pages or websites", "ابحث عن صفحة أو موقع")
            textSize = 14f
            isSingleLine = true
            setTextColor(colors.ink)
            setHintTextColor(colors.muted)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(colors.card, 15)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        val scroll = ScrollView(activity).apply { clipToPadding = false }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(8))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun matches(query: String, title: String, url: String): Boolean {
            if (query.isBlank()) return true
            val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
            return title.contains(query, true) || url.contains(query, true) || host.contains(query, true)
        }
        fun section(label: String, rows: List<Pair<String, String>>, query: String) {
            val filtered = rows.filter { matches(query, it.first, it.second) }.take(12)
            if (filtered.isEmpty()) return
            content.addView(TextView(activity).apply {
                text = label.uppercase()
                textSize = 11f
                letterSpacing = .07f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colors.muted)
                setPadding(dp(3), dp(8), dp(3), dp(8))
            })
            filtered.forEach { (title, url) ->
                val item = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(13), dp(11), dp(13), dp(11))
                    background = rounded(colors.card, 14)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { dialog.dismiss(); onOpen(url) }
                }
                item.addView(TextView(activity).apply {
                    text = title.ifBlank { runCatching { Uri.parse(url).host }.getOrNull().orEmpty().ifBlank { url } }
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(colors.ink)
                    maxLines = 1
                })
                item.addView(TextView(activity).apply {
                    text = url
                    textSize = 11f
                    setTextColor(colors.muted)
                    maxLines = 2
                    setPadding(0, dp(3), 0, 0)
                })
                content.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
            }
        }
        fun render(raw: String) {
            content.removeAllViews()
            val query = raw.trim()
            val history = store.historyEntries().take(30).map { it.title to it.url }
            val bookmarks = store.bookmarks().take(30).map { it to it }
            section(t("Open tabs", "التبويبات المفتوحة"), openTabs, query)
            section(t("Bookmarks", "المفضلة"), bookmarks, query)
            section(t("Recent history", "السجل الحديث"), history, query)
            if (content.childCount == 0) {
                content.addView(TextView(activity).apply {
                    text = t("No matching pages. Try another search.", "لا توجد صفحات مطابقة. جرّب بحثاً آخر.")
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(colors.muted)
                    setPadding(dp(20), dp(34), dp(20), dp(34))
                    background = rounded(colors.card, 16)
                })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        render("")
        dialog = AlertDialog.Builder(activity).setView(root).create()
        close.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
