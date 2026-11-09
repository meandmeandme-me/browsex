package com.browsex.app

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
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
    private val colors get() = BrowseXColors.resolve(activity, activity.currentBrowserSettings)
    private val isTvRemote get() = WrenAdaptiveUi.isTelevision(activity)

    private fun tr(value: String): String = BrowseXStrings.text(activity, value)
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), this@TabsPage.colors.line)
    }

    /** Visible keyboard/remote focus ring without changing a card's normal surface. */
    private fun focusableSurface(fill: Int, radius: Int): StateListDrawable =
        StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                GradientDrawable().apply {
                    setColor(this@TabsPage.colors.selected)
                    cornerRadius = dp(radius).toFloat()
                    setStroke(dp(3), this@TabsPage.colors.accent)
                }
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                GradientDrawable().apply {
                    setColor(this@TabsPage.colors.selected)
                    cornerRadius = dp(radius).toFloat()
                    setStroke(dp(2), this@TabsPage.colors.accent)
                }
            )
            addState(
                intArrayOf(android.R.attr.state_hovered),
                GradientDrawable().apply {
                    setColor(this@TabsPage.colors.selected)
                    cornerRadius = dp(radius).toFloat()
                    setStroke(dp(1), this@TabsPage.colors.accent)
                }
            )
            addState(intArrayOf(), rounded(fill, radius))
        }

    /** Full-screen visual tab workspace, sized for phones and wide tablet layouts. */
    fun show() {
        lateinit var dialog: AlertDialog
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(WrenAdaptiveUi.horizontalSafePadding(activity), WrenAdaptiveUi.verticalSafePadding(activity), WrenAdaptiveUi.horizontalSafePadding(activity), dp(if (isTvRemote) 28 else 12))
            setBackgroundColor(colors.canvas)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(activity).apply {
            text = tr("Your space")
            textSize = if (isTvRemote) 34f else 25f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(colors.ink)
        })
        heading.addView(TextView(activity).apply {
            text = "${tabs.size} · ${tr("Open tabs")}"
            textSize = if (isTvRemote) 16f else 13f
            setTextColor(colors.muted)
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val add = TextView(activity).apply {
            text = "+  ${tr("New tab")}"
            textSize = if (isTvRemote) 18f else 14f
            setTextColor(colors.card)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = focusableSurface(colors.accent, 22)
            minimumHeight = dp(if (isTvRemote) 64 else 48)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = false
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
                textSize = if (isTvRemote) 16f else 12f
                gravity = Gravity.CENTER
                setTextColor(colors.accent)
                background = focusableSurface(colors.card, 14)
                setPadding(dp(if (isTvRemote) 18 else 12), dp(if (isTvRemote) 14 else 10), dp(if (isTvRemote) 18 else 12), dp(if (isTvRemote) 14 else 10))
                minHeight = dp(if (isTvRemote) 64 else 48)
                isClickable = true
                isFocusable = true
                isFocusableInTouchMode = false
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
                                    val storage = runCatching {
                                        activity.storageRuntime.awaitReady()
                                    }.getOrElse {
                                        android.util.Log.e("BrowseX", "Could not prepare recently closed tab restore", it)
                                        onToast("Could not restore tab")
                                        return@launch
                                    }

                                    // The tab count may change while Room is initializing or the
                                    // coroutine is suspended. Do not consume the one-shot history
                                    // entry until capacity is checked again, or the entry could be
                                    // lost when createTab rejects the restore.
                                    if (tabs.size >= TabSessionState.MAX_TABS) {
                                        onToast("Tab limit reached")
                                        return@launch
                                    }

                                    val restored = runCatching {
                                        storage.sessions.consumeRecentlyClosed(recentlyClosed[index].id)
                                    }.onFailure {
                                        android.util.Log.e("BrowseX", "Could not consume recently closed tab", it)
                                    }.getOrNull()
                                    if (restored != null) {
                                        if (tabs.size < TabSessionState.MAX_TABS) {
                                            onCreateTab(restored.url)
                                        } else {
                                            // Put the item back if capacity changed during the
                                            // consume/create boundary; never silently lose it.
                                            runCatching {
                                                storage.sessions.recordRecentlyClosed(
                                                    restored.url, restored.title, restored.closedAt
                                                )
                                            }.onFailure {
                                                android.util.Log.e("BrowseX", "Could not restore recently closed entry", it)
                                            }
                                            onToast("Tab limit reached")
                                        }
                                    }
                                }
                            }
                        }
                        .setNegativeButton(tr("Close"), null)
                        .show()
                }
            }
        }
        sessionAction("Close all tabs") {
            if (tabs.isEmpty()) {
                onToast("No open tabs")
            } else {
                AlertDialog.Builder(activity)
                    .setTitle(tr("Close all tabs"))
                    .setMessage(tr("Close every open tab?"))
                    .setNegativeButton(tr("Cancel"), null)
                    .setPositiveButton(tr("Close all")) { _, _ ->
                        // Close a stable snapshot: the controller may create a fresh home
                        // tab after the final close, and that replacement must remain alive.
                        tabs.toList().forEach(onCloseTab)
                        dialog.dismiss()
                        show()
                    }
                    .show()
            }
        }
        page.addView(sessionActions)

        val hint = TextView(activity).apply {
            text = tr("Tap a card to return to it")
            textSize = if (isTvRemote) 16f else 12f
            setTextColor(colors.muted)
            setPadding(0, dp(if (isTvRemote) 22 else 15), 0, dp(12))
        }
        page.addView(hint)

        val scroll = ScrollView(activity)
        val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(grid)
        val windowLayout = activity.currentAdaptivePresentation?.windowLayout
            ?: when {
                activity.resources.configuration.screenWidthDp < 600 -> WrenWindowLayout.PHONE_COMPACT
                activity.resources.configuration.screenWidthDp < 840 -> WrenWindowLayout.MEDIUM
                activity.resources.configuration.screenWidthDp < 1200 -> WrenWindowLayout.EXPANDED
                activity.resources.configuration.screenWidthDp < 1600 -> WrenWindowLayout.LARGE
                else -> WrenWindowLayout.EXTRA_LARGE
            }
        val columns = AdaptiveTabGridPolicy.columns(windowLayout)
        val tvRemote = windowLayout == WrenWindowLayout.TV
        fun renderTabGrid(query: String = "") {
            grid.removeAllViews()
            val normalizedQuery = query.trim().lowercase()
            val visibleTabs = tabs.filter { tab ->
                normalizedQuery.isEmpty() ||
                    tab.title.contains(normalizedQuery, ignoreCase = true) ||
                    tab.url.contains(normalizedQuery, ignoreCase = true)
            }
            if (visibleTabs.isEmpty()) {
                grid.addView(TextView(activity).apply {
                    text = tr("No matching tabs")
                    textSize = if (tvRemote) 18f else 14f
                    setTextColor(colors.muted)
                    gravity = Gravity.CENTER
                    setPadding(dp(20), dp(36), dp(20), dp(36))
                }, LinearLayout.LayoutParams(-1, -2))
                return
            }
            val rows = visibleTabs.chunked(columns)
            rows.forEach { rowTabs ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            rowTabs.forEach { tab ->
                val selected = tab === activeTab
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(13), dp(13), dp(13), dp(12))
                    background = focusableSurface(if (selected) colors.selected else colors.card, 18)
                    elevation = dp(if (selected) 4 else 1).toFloat()
                    minimumHeight = dp(if (tvRemote) 300 else 200)
                    isClickable = true
                    isFocusable = true
                    isFocusableInTouchMode = false
                    isSelected = selected
                    contentDescription = (if (tab.privateMode) tr("Private") + " · " else "") +
                        tab.title.ifBlank { tr("New tab") } + ", " + tab.url
                }
                val preview = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    background = rounded(if (selected) colors.selected else colors.panel, 12)
                }
                preview.addView(TextView(activity).apply {
                    text = "Wren"
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(colors.accent)
                })
                preview.addView(TextView(activity).apply {
                    text = (if (tab.privateMode) tr("Private") + " · " else "") + tab.title.ifBlank { tr("New tab") }
                    textSize = if (tvRemote) 17f else 13f
                    setTextColor(colors.ink)
                    maxLines = 2
                    gravity = Gravity.CENTER
                    setPadding(0, dp(7), 0, dp(3))
                })
                card.addView(preview, LinearLayout.LayoutParams(-1, dp(if (tvRemote) 150 else 104)))
                card.addView(TextView(activity).apply {
                    text = (if (tab.privateMode) tr("Private") + " · " else "") + tab.title.ifBlank { "New tab" }
                    textSize = if (tvRemote) 20f else 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(colors.ink)
                    maxLines = 1
                    setPadding(0, dp(10), 0, dp(3))
                })
                card.addView(TextView(activity).apply {
                    text = tab.url.removePrefix("https://").removePrefix("http://").ifBlank { "Start page" }
                    textSize = if (tvRemote) 14f else 11f
                    setTextColor(colors.muted)
                    maxLines = 2
                })
                val actions = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(9), 0, 0)
                }
                actions.addView(TextView(activity).apply {
                    text = if (selected) "● ${tr("Active")}" else tr("Open")
                    textSize = 12f
                    setTextColor(if (selected) colors.accent else colors.muted)
                }, LinearLayout.LayoutParams(0, dp(32), 1f))
                actions.addView(TextView(activity).apply {
                    text = "×"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(colors.danger)
                    contentDescription = tr("Close tab")
                    minimumWidth = dp(if (tvRemote) 60 else 48)
                    minimumHeight = dp(if (tvRemote) 60 else 48)
                    background = focusableSurface(colors.card, 12)
                    isFocusable = true
                    isFocusableInTouchMode = false
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
                val showTabActions = {
                    AlertDialog.Builder(activity)
                        .setTitle(tab.title.ifBlank { tr("New tab") })
                        .setItems(arrayOf(tr("Open"), tr("Duplicate tab"), tr("Close tab"), tr("Close other tabs"))) { _, index ->
                            when (index) {
                                0 -> {
                                    onActivateTab(tab)
                                    dialog.dismiss()
                                }
                                1 -> {
                                    if (tabs.size >= TabSessionState.MAX_TABS) {
                                        onToast("Tab limit reached")
                                    } else {
                                        onCreateTab(tab.url)
                                    }
                                    dialog.dismiss()
                                    show()
                                }
                                2 -> {
                                    onCloseTab(tab)
                                    dialog.dismiss()
                                    show()
                                }
                                3 -> {
                                    // Keep the selected tab alive while closing the rest. Work
                                    // from a snapshot because each close mutates the live registry.
                                    tabs.filterNot { it === tab }.forEach(onCloseTab)
                                    onActivateTab(tab)
                                    dialog.dismiss()
                                    show()
                                }
                            }
                        }
                        .show()
                }
                card.setOnContextClickListener {
                    showTabActions()
                    true
                }
                card.setOnLongClickListener {
                    showTabActions()
                    true
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
        }
        val searchTabs = EditText(activity).apply {
            setHint(tr("Search tabs"))
            setTextSize(if (tvRemote) 18f else 14f)
            isSingleLine = true
            setTextColor(colors.ink)
            setHintTextColor(colors.muted)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(colors.panel, 16)
            contentDescription = tr("Search tabs")
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderTabGrid(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        page.addView(searchTabs, LinearLayout.LayoutParams(-1, dp(if (tvRemote) 60 else 50)).apply {
            topMargin = dp(12)
        })
        renderTabGrid()
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val done = TextView(activity).apply {
            text = tr("Done")
            textSize = if (tvRemote) 18f else 14f
            gravity = Gravity.CENTER
            setTextColor(colors.card)
            background = focusableSurface(colors.ink, 24)
            isFocusable = true
            isFocusableInTouchMode = false
            setPadding(dp(20), dp(13), dp(20), dp(13))
        }
        page.addView(done, LinearLayout.LayoutParams(-1, dp(if (tvRemote) 64 else 48)).apply { topMargin = dp(8) })

        dialog = AlertDialog.Builder(activity).setView(page).create()
        done.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        if (tvRemote) add.post { if (dialog.isShowing) add.requestFocus() }
    }
}
