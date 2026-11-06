package com.browsex.app

import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** UI-only tab workspace. All browser mutations are injected through callbacks. */
internal class TabsPage(
    private val activity: MainActivity,
    private val store: BrowserStore,
    private val tabsProvider: () -> List<BrowserTab>,
    private val activeTabProvider: () -> BrowserTab?,
    private val onCreateTab: (String) -> Unit,
    private val onActivateTab: (BrowserTab) -> Unit,
    private val onCloseTab: (BrowserTab) -> Unit,
    private val homePageUrlProvider: () -> String,
    private val onToast: (String) -> Unit
) {
    private val tabs: List<BrowserTab> get() = tabsProvider()
    private val activeTab: BrowserTab? get() = activeTabProvider()
    private val colors get() = BrowseXColors.resolve(activity, store)

    private fun tr(value: String): String = BrowseXStrings.text(activity, value)
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    /** Full-screen visual tab workspace, sized for phones and wide tablet layouts. */
    fun show() {
        lateinit var dialog: AlertDialog
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(colors.canvas)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(activity).apply {
            text = tr("Your space")
            textSize = 25f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(colors.ink)
        })
        heading.addView(TextView(activity).apply {
            text = if (BrowseXColors.isArabic(activity, store)) "${tabs.size} تبويب مفتوح" else "${tabs.size} open ${if (tabs.size == 1) "tab" else "tabs"}"
            textSize = 13f
            setTextColor(colors.muted)
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val add = TextView(activity).apply {
            text = "+  ${tr("New tab")}"
            textSize = 14f
            setTextColor(colors.card)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = rounded(colors.accent, 22)
            setOnClickListener { onCreateTab(homePageUrlProvider()); dialog.dismiss(); show() }
        }
        header.addView(add)
        page.addView(header)

        val sessionActions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(2))
        }
        fun sessionAction(label: String, action: () -> Unit) {
            val button = TextView(activity).apply {
                text = tr(label)
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(colors.accent)
                background = rounded(colors.card, 14)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                minHeight = dp(42)
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
            sessionActions.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(6)
            })
        }
        sessionAction("Save session") {
            val regularTabs = tabs.filterNot { it.privateMode }
            if (regularTabs.isEmpty()) {
                onToast("No regular tabs to save")
            } else {
                val field = EditText(activity).apply {
                    hint = tr("Session name")
                    maxLines = 1
                }
                AlertDialog.Builder(activity)
                    .setTitle(tr("Save browsing session"))
                    .setView(field)
                    .setNegativeButton(tr("Cancel"), null)
                    .setPositiveButton(tr("Save")) { _, _ ->
                        val name = field.text?.toString().orEmpty().trim()
                        activity.lifecycleScope.launch {
                            val sessions = runCatching { activity.storageRuntime.awaitReady().sessions.sessions() }
                                .getOrElse {
                                    android.util.Log.e("BrowseX", "Could not read saved sessions", it)
                                    onToast("Could not save session")
                                    return@launch
                                }
                            val save = {
                                activity.lifecycleScope.launch {
                                    val activeIndex = regularTabs.indexOf(activeTab).takeIf { it >= 0 } ?: 0
                                    val saved = runCatching {
                                        activity.storageRuntime.awaitReady().sessions.saveSession(
                                            name, regularTabs.map { it.url }, activeIndex
                                        )
                                    }.getOrDefault(false)
                                    onToast(if (saved) "Session saved" else "Session name is invalid or the session limit was reached")
                                }
                            }
                            val replacesExisting = sessions.any { it.name.equals(name, ignoreCase = true) }
                            if (replacesExisting) {
                                AlertDialog.Builder(activity)
                                    .setMessage(tr("A session with this name already exists. Replace it?"))
                                    .setNegativeButton(tr("Cancel"), null)
                                    .setPositiveButton(tr("Replace")) { _, _ -> save() }
                                    .show()
                            } else save()
                        }
                    }
                    .show()
            }
        }
        sessionAction("Saved sessions") {
            activity.lifecycleScope.launch {
                val sessions = runCatching { activity.storageRuntime.awaitReady().sessions.sessions() }
                    .getOrElse {
                        android.util.Log.e("BrowseX", "Could not load saved sessions", it)
                        onToast("Could not load saved sessions")
                        return@launch
                    }
                if (sessions.isEmpty()) {
                    onToast("No saved sessions")
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle(tr("Saved sessions"))
                        .setItems(sessions.map { "${it.name} · ${it.urls.size}" }.toTypedArray()) { _, index ->
                            val selected = sessions[index]
                            AlertDialog.Builder(activity)
                                .setTitle(selected.name)
                                .setItems(arrayOf(tr("Open session"), tr("Delete session"))) { _, action ->
                                    if (action == 0) {
                                        val available = (TabSessionState.MAX_TABS - tabs.size).coerceAtLeast(0)
                                        val toOpen = selected.urls.take(available)
                                        val firstOpenedIndex = tabs.size
                                        toOpen.forEach { onCreateTab(it) }
                                        if (toOpen.isEmpty()) onToast("Tab limit reached")
                                        else {
                                            onActivateTab(tabs[firstOpenedIndex + selected.activeIndex.coerceIn(0, toOpen.lastIndex)])
                                            if (toOpen.size < selected.urls.size) onToast("Some tabs were skipped because the tab limit was reached")
                                        }
                                    } else {
                                        activity.lifecycleScope.launch {
                                            val removed = runCatching {
                                                activity.storageRuntime.awaitReady().sessions.deleteSession(selected.id)
                                            }.getOrDefault(false)
                                            onToast(if (removed) "Session deleted" else "Could not delete session")
                                        }
                                    }
                                }
                                .setNegativeButton(tr("Cancel"), null)
                                .show()
                        }
                        .setNegativeButton(tr("Close"), null)
                        .show()
                }
            }
        }
        sessionAction("Recently closed") {
            activity.lifecycleScope.launch {
                val recentlyClosed = runCatching { activity.storageRuntime.awaitReady().sessions.recentlyClosed() }
                    .getOrElse {
                        android.util.Log.e("BrowseX", "Could not load recently closed tabs", it)
                        onToast("Could not load recently closed tabs")
                        return@launch
                    }
                if (recentlyClosed.isEmpty()) {
                    onToast("No recently closed tabs")
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle(tr("Recently closed"))
                        .setItems(recentlyClosed.map { "${it.title} · ${Uri.parse(it.url).host.orEmpty()}" }.toTypedArray()) { _, index ->
                            if (tabs.size >= TabSessionState.MAX_TABS) {
                                onToast("Tab limit reached")
                            } else {
                                activity.lifecycleScope.launch {
                                    val restored = runCatching {
                                        activity.storageRuntime.awaitReady().sessions.consumeRecentlyClosed(recentlyClosed[index].id)
                                    }.getOrNull()
                                    restored?.let { onCreateTab(it.url) }
                                }
                            }
                        }
                        .setNegativeButton(tr("Close"), null)
                        .show()
                }
            }
        }
        page.addView(sessionActions)

        val hint = TextView(activity).apply {
            text = tr("Tap a card to return to it")
            textSize = 12f
            setTextColor(colors.muted)
            setPadding(0, dp(15), 0, dp(12))
        }
        page.addView(hint)

        val scroll = ScrollView(activity)
        val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(grid)
        val columns = if (activity.resources.configuration.screenWidthDp >= 600) 3 else 2
        val rows = tabs.chunked(columns)
        rows.forEach { rowTabs ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            rowTabs.forEach { tab ->
                val selected = tab === activeTab
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(13), dp(13), dp(13), dp(12))
                    background = rounded(colors.card, 18)
                    elevation = dp(if (selected) 4 else 1).toFloat()
                }
                val preview = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    background = rounded(if (selected) colors.selected else colors.panel, 12)
                }
                preview.addView(TextView(activity).apply {
                    text = "Browse X"
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(colors.accent)
                })
                preview.addView(TextView(activity).apply {
                    text = (if (tab.privateMode) tr("Private") + " · " else "") + tab.title.ifBlank { "New tab" }
                    textSize = 13f
                    setTextColor(colors.ink)
                    maxLines = 2
                    gravity = Gravity.CENTER
                    setPadding(0, dp(7), 0, dp(3))
                })
                card.addView(preview, LinearLayout.LayoutParams(-1, dp(104)))
                card.addView(TextView(activity).apply {
                    text = (if (tab.privateMode) tr("Private") + " · " else "") + tab.title.ifBlank { "New tab" }
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(colors.ink)
                    maxLines = 1
                    setPadding(0, dp(10), 0, dp(3))
                })
                card.addView(TextView(activity).apply {
                    text = tab.url.removePrefix("https://").removePrefix("http://").ifBlank { "Start page" }
                    textSize = 11f
                    setTextColor(colors.muted)
                    maxLines = 2
                })
                val actions = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(9), 0, 0)
                }
                actions.addView(TextView(activity).apply {
                    text = if (selected) "● Active" else "Open"
                    textSize = 12f
                    setTextColor(if (selected) colors.accent else colors.muted)
                }, LinearLayout.LayoutParams(0, dp(32), 1f))
                actions.addView(TextView(activity).apply {
                    text = "×"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(colors.danger)
                    contentDescription = "Close tab"
                    setPadding(dp(12), 0, dp(5), 0)
                    setOnClickListener {
                        onCloseTab(tab)
                        dialog.dismiss()
                        show()
                    }
                })
                card.addView(actions)
                card.setOnClickListener {
                    onActivateTab(tab)
                    dialog.dismiss()
                }
                row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(9))
                })
            }
            repeat(columns - rowTabs.size) {
                row.addView(View(activity), LinearLayout.LayoutParams(0, dp(1), 1f))
            }
            grid.addView(row)
        }
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val done = TextView(activity).apply {
            text = "Done"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(colors.card)
            background = rounded(colors.ink, 24)
            setPadding(dp(20), dp(13), dp(20), dp(13))
        }
        page.addView(done, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        dialog = AlertDialog.Builder(activity).setView(page).create()
        done.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
