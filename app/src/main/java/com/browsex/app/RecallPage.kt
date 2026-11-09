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
    private val openTabs: List<Pair<String, String>>,
    private val bookmarkRows: List<Pair<String, String>>,
    private val historyRows: List<Pair<String, String>>,
    private val onOpen: (String) -> Unit
) {
    private val colors get() = BrowseXColors.resolve(activity, activity.currentBrowserSettings)
    private val isTvRemote get() = WrenAdaptiveUi.isTelevision(activity)
    private fun t(en: String, ar: String) = BrowseXStrings.text(activity, en, ar)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    fun show() {
        lateinit var dialog: AlertDialog
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(WrenAdaptiveUi.horizontalSafePadding(activity), WrenAdaptiveUi.verticalSafePadding(activity), WrenAdaptiveUi.horizontalSafePadding(activity), dp(if (isTvRemote) 28 else 14))
            setBackgroundColor(colors.canvas)
            BrowseXColors.applyDirection(this, activity.currentBrowserSettings)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(activity).apply {
            text = t("Find anything", "اعثر على أي شيء")
            textSize = if (isTvRemote) 34f else 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.ink)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(activity).apply {
            text = "×"
            textSize = if (isTvRemote) 34f else 27f
            gravity = Gravity.CENTER
            setTextColor(colors.ink)
            background = rounded(colors.card, 14)
            contentDescription = t("Close", "إغلاق")
            WrenAdaptiveUi.prepareFocusable(this, activity, 14, colors.accent, if (isTvRemote) 64 else 48)
        }
        header.addView(close)
        root.addView(header)
        root.addView(TextView(activity).apply {
            text = t(
                "Search your open tabs, bookmarks and recent history in one place.",
                "ابحث في التبويبات المفتوحة والمفضلة والسجل الحديث من مكان واحد."
            )
            textSize = if (isTvRemote) 17f else 12f
            setTextColor(colors.muted)
            setPadding(0, dp(4), 0, dp(if (isTvRemote) 20 else 12))
        })
        val search = EditText(activity).apply {
            hint = t("Search pages or websites", "ابحث عن صفحة أو موقع")
            filters = arrayOf(android.text.InputFilter.LengthFilter(MAX_RECALL_QUERY_LENGTH))
            textSize = if (isTvRemote) 19f else 14f
            isSingleLine = true
            setTextColor(colors.ink)
            setHintTextColor(colors.muted)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(colors.card, 15)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(if (isTvRemote) 64 else 48)))
        val scroll = ScrollView(activity).apply { clipToPadding = false }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(8))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun matches(query: String, title: String, url: String): Boolean {
            val normalizedQuery = BrowserTextNormalizer.normalize(query)
            if (normalizedQuery.isBlank()) return true
            val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
            return listOf(title, url, host).any {
                BrowserTextNormalizer.normalize(it).contains(normalizedQuery)
            }
        }
        fun section(label: String, rows: List<Pair<String, String>>, query: String) {
            // Keep a bounded scan even if an imported history/bookmark database is unusually large.
            val filtered = rows.asSequence()
                .take(MAX_RECALL_SOURCE_ROWS)
                .filter { matches(query, it.first, it.second) }
                .take(MAX_RECALL_SECTION_RESULTS)
                .toList()
            if (filtered.isEmpty()) return
            content.addView(TextView(activity).apply {
                text = label.uppercase()
                textSize = if (isTvRemote) 15f else 11f
                letterSpacing = .07f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colors.muted)
                setPadding(dp(3), dp(8), dp(3), dp(8))
            })
            filtered.forEach { (title, url) ->
                val item = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(if (isTvRemote) 20 else 13), dp(if (isTvRemote) 18 else 11), dp(if (isTvRemote) 20 else 13), dp(if (isTvRemote) 18 else 11))
                    background = rounded(colors.card, 14)
                    isClickable = true
                    WrenAdaptiveUi.prepareFocusable(this, activity, 14, colors.accent, if (isTvRemote) 76 else 48)
                    contentDescription = (
                        title.filterNot { it.isISOControl() }.take(MAX_RECALL_LABEL_LENGTH)
                            .ifBlank { url.filterNot { ch -> ch.isISOControl() }.take(MAX_RECALL_LABEL_LENGTH) } +
                            ", " + url.filterNot { it.isISOControl() }.take(MAX_RECALL_LABEL_LENGTH)
                        ).take(MAX_RECALL_LABEL_LENGTH * 2)
                    setOnClickListener { dialog.dismiss(); onOpen(url) }
                }
                item.addView(TextView(activity).apply {
                    text = title.ifBlank { runCatching { Uri.parse(url).host }.getOrNull().orEmpty().ifBlank { url } }
                    textSize = if (isTvRemote) 18f else 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(colors.ink)
                    maxLines = 1
                })
                item.addView(TextView(activity).apply {
                    text = url.filterNot { it.isISOControl() }.take(MAX_RECALL_URL_LENGTH)
                    textSize = if (isTvRemote) 14f else 11f
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
            section(t("Open tabs", "التبويبات المفتوحة"), openTabs, query)
            section(t("Bookmarks", "المفضلة"), bookmarkRows, query)
            section(t("Recent history", "السجل الحديث"), historyRows, query)
            if (content.childCount == 0) {
                content.addView(TextView(activity).apply {
                    text = t("No matching pages. Try another search.", "لا توجد صفحات مطابقة. جرّب بحثاً آخر.")
                    textSize = if (isTvRemote) 18f else 13f
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
        if (isTvRemote) search.post { if (dialog.isShowing) search.requestFocus() }
    }

    private companion object {
        const val MAX_RECALL_QUERY_LENGTH = 256
        const val MAX_RECALL_SOURCE_ROWS = 500
        const val MAX_RECALL_SECTION_RESULTS = 12
        const val MAX_RECALL_LABEL_LENGTH = 240
        const val MAX_RECALL_URL_LENGTH = 2048
    }
}
