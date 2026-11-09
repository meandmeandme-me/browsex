package com.browsex.app

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class AddressSuggestionKind { OPEN_TAB, BOOKMARK, HISTORY, SEARCH }

internal data class AddressSuggestion(
    val kind: AddressSuggestionKind,
    val title: String,
    val subtitle: String,
    val value: String
)

/** Native, accessible address-bar suggestions backed by open tabs, bookmarks and history. */
internal class AddressSuggestionsController(
    private val activity: MainActivity,
    private val root: FrameLayout,
    private val dock: View,
    private val address: EditText,
    private val loadSuggestions: suspend (String) -> List<AddressSuggestion>,
    private val onSelect: (AddressSuggestion) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var queryJob: Job? = null
    private val colors get() = BrowseXColors.resolve(activity, activity.currentBrowserSettings)
    private val panel = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(colors.panel)
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), colors.line)
        }
        elevation = dp(18).toFloat()
        clipToOutline = true
        visibility = View.GONE
        isClickable = true
        contentDescription = BrowseXStrings.text(activity, "Search suggestions", "اقتراحات البحث")
    }
    private val scroll = ScrollView(activity).apply {
        isFillViewport = false
        clipToPadding = false
        setPadding(dp(5), dp(5), dp(5), dp(5))
        isVerticalScrollBarEnabled = true
    }
    private val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    init {
        scroll.addView(rows, ScrollView.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        root.addView(panel, FrameLayout.LayoutParams(
            -1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            leftMargin = dp(12)
            rightMargin = dp(12)
            bottomMargin = dp(132)
        })
    }

    fun onQueryChanged(raw: String) {
        queryJob?.cancel()
        if (!address.hasFocus()) {
            hide()
            return
        }
        val query = raw.trim()
        queryJob = scope.launch {
            delay(110)
            val suggestions = runCatching { loadSuggestions(query) }.getOrElse {
                android.util.Log.w("Wren", "Address suggestions could not be loaded", it)
                emptyList()
            }
            if (!address.hasFocus() || address.text?.toString()?.trim() != query) return@launch
            render(query, suggestions)
        }
    }

    fun hide() {
        queryJob?.cancel()
        panel.visibility = View.GONE
    }

    fun destroy() {
        hide()
        scope.cancel()
        root.removeView(panel)
    }

    private fun render(query: String, suggestions: List<AddressSuggestion>) {
        rows.removeAllViews()
        val colors = colors
        val visible = suggestions.take(5).toMutableList()
        if (query.isNotBlank()) {
            visible += AddressSuggestion(
                kind = AddressSuggestionKind.SEARCH,
                title = BrowseXStrings.text(activity, "Search the web", "ابحث في الويب"),
                subtitle = query,
                value = query
            )
        }
        if (visible.isEmpty()) {
            hide()
            return
        }

        val heading = TextView(activity).apply {
            text = BrowseXStrings.text(activity, "Suggestions", "اقتراحات")
            textSize = 11f
            letterSpacing = .06f
            setTextColor(colors.muted)
            setPadding(dp(13), dp(8), dp(13), dp(9))
            isImportantForAccessibility = false
        }
        rows.addView(heading, LinearLayout.LayoutParams(-1, -2))

        visible.forEachIndexed { index, suggestion ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                minimumHeight = dp(58)
                isClickable = true
                isFocusable = false
                background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                setOnClickListener { onSelect(suggestion) }
                contentDescription = listOf(suggestion.title, suggestion.subtitle, kindLabel(suggestion.kind))
                    .filter(String::isNotBlank).joinToString(", ")
            }
            val icon = TextView(activity).apply {
                text = when (suggestion.kind) {
                    AddressSuggestionKind.OPEN_TAB -> "▣"
                    AddressSuggestionKind.BOOKMARK -> "☆"
                    AddressSuggestionKind.HISTORY -> "◷"
                    AddressSuggestionKind.SEARCH -> "⌕"
                }
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(colors.accent)
                background = GradientDrawable().apply {
                    setColor(colors.selected)
                    cornerRadius = dp(12).toFloat()
                }
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                marginEnd = dp(11)
            })
            val details = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            details.addView(TextView(activity).apply {
                text = suggestion.title
                textSize = 13.5f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(colors.ink)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            })
            details.addView(TextView(activity).apply {
                text = suggestion.subtitle
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setTextColor(colors.muted)
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(activity).apply {
                text = kindLabel(suggestion.kind)
                textSize = 10f
                setTextColor(colors.muted)
                gravity = Gravity.CENTER
                maxLines = 1
            }, LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(7)
            })
            rows.addView(row, LinearLayout.LayoutParams(-1, -2))
            if (index != visible.lastIndex) {
                rows.addView(View(activity).apply {
                    setBackgroundColor(colors.line)
                }, LinearLayout.LayoutParams(-1, dp(1)).apply {
                    leftMargin = dp(58)
                    rightMargin = dp(10)
                })
            }
        }

        val availableWidth = root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        val dockHeight = dock.height.takeIf { it > 0 } ?: dp(118)
        val params = (panel.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        params.width = (dock.width.takeIf { it > 0 } ?: (availableWidth - dp(24))).coerceAtLeast(dp(280))
        params.bottomMargin = dockHeight + dp(13)
        panel.layoutParams = params
        val rowCount = visible.size + 1
        scroll.layoutParams = LinearLayout.LayoutParams(-1, minOf(dp(280), dp(rowCount * 58 + 20)))
        panel.visibility = View.VISIBLE
        panel.bringToFront()
    }

    private fun kindLabel(kind: AddressSuggestionKind): String = when (kind) {
        AddressSuggestionKind.OPEN_TAB -> BrowseXStrings.text(activity, "Tab", "تبويب")
        AddressSuggestionKind.BOOKMARK -> BrowseXStrings.text(activity, "Bookmark", "مفضلة")
        AddressSuggestionKind.HISTORY -> BrowseXStrings.text(activity, "History", "السجل")
        AddressSuggestionKind.SEARCH -> BrowseXStrings.text(activity, "Search", "بحث")
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
