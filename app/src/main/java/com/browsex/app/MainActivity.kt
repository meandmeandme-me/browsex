package com.browsex.app

import android.app.DownloadManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.os.Environment

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.WebResponse

private const val HOME_URL = "resource://android/assets/home.html"
private val COLOR_BG = Color.rgb(13, 17, 23)
private val COLOR_PANEL = Color.rgb(22, 27, 34)
private val COLOR_FIELD = Color.rgb(33, 40, 50)
private val COLOR_ACCENT = Color.rgb(65, 132, 245)

/** GeckoRuntime is process-wide. Sessions are owned by MainActivity and always closed there. */
private object BrowserEngine {
    @Volatile private var instance: GeckoRuntime? = null
    fun runtime(context: Context): GeckoRuntime = instance ?: synchronized(this) {
        instance ?: GeckoRuntime.create(context.applicationContext).also { instance = it }
    }
}

private data class BrowserTab(
    val session: GeckoSession = GeckoSession(),
    var title: String = "New tab",
    var url: String = HOME_URL,
    var progress: Int = 0,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var sitePermissions: List<GeckoSession.PermissionDelegate.ContentPermission> = emptyList()
)

@ExperimentalGeckoViewApi
@OptIn(ExperimentalGeckoViewApi::class)
class MainActivity : AppCompatActivity() {
    private val tabs = mutableListOf<BrowserTab>()
    private var activeTab: BrowserTab? = null
    private lateinit var store: BrowserStore
    private lateinit var browserView: GeckoView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var tabsButton: TextView
    private var fullscreen = false
    private var shuttingDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BrowserStore(this)
        window.statusBarColor = Color.rgb(239, 245, 250)
        window.navigationBarColor = Color.rgb(239, 245, 250)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true
        setContentView(createBrowserUi())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val tab = activeTab
                when {
                    fullscreen -> tab?.session?.exitFullScreen()
                    tab?.canGoBack == true -> tab.session.goBack()
                    tabs.size > 1 && tab != null -> closeTab(tab)
                    else -> finish()
                }
            }
        })

        val initial = intent?.data?.let(::safeExternalUrl) ?: HOME_URL
        createTab(initial)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let(::safeExternalUrl)?.let(::createTab)
    }

    override fun onDestroy() {
        shuttingDown = true
        if (::browserView.isInitialized) browserView.releaseSession()
        tabs.toList().forEach { it.session.close() }
        tabs.clear()
        activeTab = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createBrowserUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(239, 245, 250))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.rgb(239, 245, 250))
        }
        val home = actionButton("⌂", "Home") { activeTab?.session?.loadUri(HOME_URL) }.apply {
            background = rounded(Color.WHITE, 10)
            textSize = 22f
        }
        topBar.addView(home, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            marginEnd = dp(4)
        })

        address = EditText(this).apply {
            hint = "Search or enter URL"
            setHintTextColor(Color.rgb(83, 105, 123))
            setTextColor(Color.rgb(19, 43, 64))
            textSize = 14f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setPadding(dp(14), 0, dp(12), 0)
            background = rounded(Color.WHITE, 24)
            elevation = dp(3).toFloat()
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                    navigate(address.text.toString())
                    true
                } else false
            }
            setOnFocusChangeListener { _, focused -> if (!focused) updateAddress() }
        }
        topBar.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginEnd = dp(4)
        })

        tabsButton = actionButton("▢", "Open tabs") { showTabs() }.apply {
            background = rounded(Color.WHITE, 10)
            textSize = 20f
        }
        topBar.addView(tabsButton, LinearLayout.LayoutParams(dp(44), dp(48)).apply {
            marginEnd = dp(4)
        })
        val menu = actionButton("⋮", "Browser menu") { showBrowserMenu() }.apply {
            background = rounded(Color.WHITE, 10)
            textSize = 22f
        }
        topBar.addView(menu, LinearLayout.LayoutParams(dp(44), dp(48)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(8, 102, 217))
            visibility = View.GONE
        }
        browserView = GeckoView(this).apply { setBackgroundColor(Color.WHITE) }
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(5), dp(14), dp(8))
            background = rounded(Color.WHITE, 22)
            elevation = dp(5).toFloat()
        }
        bottomButton("‹", "Go back") { activeTab?.takeIf { it.canGoBack }?.session?.goBack() }
        bottomButton("›", "Go forward") { activeTab?.takeIf { it.canGoForward }?.session?.goForward() }
        bottomButton("＋", "New tab") { createTab(HOME_URL) }
        bottomButton("☆", "Bookmark page") {
            activeTab?.url?.takeIf { it.startsWith("http") }?.let {
                val added = store.toggleBookmark(it)
                toast(if (added) "Saved to favorites" else "Removed from favorites")
            }
        }

        root.addView(topBar)
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))
        root.addView(browserView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomBar, LinearLayout.LayoutParams(-1, dp(62)).apply {
            setMargins(dp(12), dp(5), dp(12), dp(8))
        })
        return root
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun actionButton(label: String, description: String, action: () -> Unit) =
        TextView(this).apply {
            text = label
            contentDescription = description
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(48, 61, 88))
            setOnClickListener { action() }
        }

    private fun bottomButton(label: String, description: String, action: () -> Unit) =
        actionButton(label, description, action).also {
            it.textSize = 23f
            it.setTextColor(Color.rgb(54, 62, 86))
            bottomBar.addView(it, LinearLayout.LayoutParams(0, -1, 1f))
        }

    private fun updateChrome() {
        val tab = activeTab ?: return
        if (!address.hasFocus()) updateAddress()
        progress.progress = tab.progress
        progress.visibility = if (tab.progress in 1..99) View.VISIBLE else View.GONE
        tabsButton.text = "▢ ${tabs.size}"
    }

    private fun updateAddress() {
        if (!::address.isInitialized || address.hasFocus()) return
        val url = activeTab?.url.orEmpty()
        address.setText(if (url.startsWith("resource://")) "" else url)
    }

    private fun normalizeInput(raw: String): String =
        NavigationInput.normalize(raw, store.searchEngine, HOME_URL)

    private fun safeExternalUrl(uri: Uri): String? {
        val scheme = uri.scheme?.lowercase()
        return if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) uri.toString() else null
    }

    private fun navigate(raw: String) {
        val tab = activeTab ?: return
        val target = normalizeInput(raw)
        tab.session.loadUri(target)
        address.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)
        browserView.requestFocus()
    }

    private fun createTab(url: String) {
        if (shuttingDown) return
        val tab = BrowserTab()
        attachDelegates(tab)
        tab.session.open(BrowserEngine.runtime(this))
        tabs.add(tab)
        activateTab(tab)
        tab.session.loadUri(url)
    }

    private fun activateTab(tab: BrowserTab) {
        if (!tabs.contains(tab)) return
        activeTab = tab
        browserView.setSession(tab.session)
        updateChrome()
    }

    private fun closeTab(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        val wasActive = activeTab === tab
        if (wasActive) browserView.releaseSession()
        tabs.removeAt(index)
        tab.session.close()
        if (tabs.isEmpty()) createTab(HOME_URL)
        else if (wasActive) activateTab(tabs[minOf(index, tabs.lastIndex)])
        else updateChrome()
    }

    private fun attachDelegates(tab: BrowserTab) {
        val session = tab.session
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.progress = 1
                if (tab === activeTab) updateChrome()
            }
            override fun onProgressChange(session: GeckoSession, progressValue: Int) {
                tab.progress = progressValue
                if (tab === activeTab) updateChrome()
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.progress = 100
                if (success) store.addHistory(tab.url)
                if (tab === activeTab) updateChrome()
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                tab.url = url.orEmpty()
                tab.sitePermissions = perms.toList()
                if (tab === activeTab) updateChrome()
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
                if (tab === activeTab) updateChrome()
            }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
                if (tab === activeTab) updateChrome()
            }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val child = BrowserTab()
                attachDelegates(child)
                tabs.add(child)
                runOnUiThread { if (!isFinishing && !shuttingDown) activateTab(child) }
                return GeckoResult.fromValue(child.session)
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title?.takeIf { it.isNotBlank() } ?: "New tab"
                if (tab === activeTab) updateChrome()
            }
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (tab === activeTab) setFullscreen(fullScreen)
            }
            override fun onCloseRequest(session: GeckoSession) { closeTab(tab) }
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                enqueueDownload(response)
            }
        }
    }

    private fun setFullscreen(enabled: Boolean) {
        fullscreen = enabled
        topBar.visibility = if (enabled) View.GONE else View.VISIBLE
        bottomBar.visibility = if (enabled) View.GONE else View.VISIBLE
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else controller.show(WindowInsetsCompat.Type.systemBars())
    }

    /** Full-screen visual tab workspace, sized for phones and wide tablet layouts. */
    private fun showTabs() {
        lateinit var dialog: AlertDialog
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(Color.rgb(239, 245, 250))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(this).apply {
            text = "Your space"
            textSize = 25f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(19, 43, 64))
        })
        heading.addView(TextView(this).apply {
            text = "${tabs.size} open ${if (tabs.size == 1) "tab" else "tabs"}"
            textSize = 13f
            setTextColor(Color.rgb(83, 105, 123))
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val add = TextView(this).apply {
            text = "+  New tab"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = rounded(Color.rgb(8, 102, 217), 22)
            setOnClickListener { createTab(HOME_URL); dialog.dismiss(); showTabs() }
        }
        header.addView(add)
        page.addView(header)

        val hint = TextView(this).apply {
            text = "Tap a card to return to it"
            textSize = 12f
            setTextColor(Color.rgb(83, 105, 123))
            setPadding(0, dp(15), 0, dp(12))
        }
        page.addView(hint)

        val scroll = ScrollView(this)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(grid)
        val columns = if (resources.configuration.screenWidthDp >= 600) 3 else 2
        val rows = tabs.chunked(columns)
        rows.forEach { rowTabs ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            rowTabs.forEach { tab ->
                val selected = tab === activeTab
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(13), dp(13), dp(13), dp(12))
                    background = rounded(Color.WHITE, 18)
                    elevation = dp(if (selected) 4 else 1).toFloat()
                }
                val preview = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    background = rounded(if (selected) Color.rgb(225, 239, 255) else Color.rgb(243, 247, 251), 12)
                }
                preview.addView(TextView(this).apply {
                    text = "Browse X"
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(Color.rgb(8, 102, 217))
                })
                preview.addView(TextView(this).apply {
                    text = tab.title.ifBlank { "New tab" }
                    textSize = 13f
                    setTextColor(Color.rgb(31, 48, 65))
                    maxLines = 2
                    gravity = Gravity.CENTER
                    setPadding(0, dp(7), 0, dp(3))
                })
                card.addView(preview, LinearLayout.LayoutParams(-1, dp(104)))
                card.addView(TextView(this).apply {
                    text = tab.title.ifBlank { "New tab" }
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(Color.rgb(19, 43, 64))
                    maxLines = 1
                    setPadding(0, dp(10), 0, dp(3))
                })
                card.addView(TextView(this).apply {
                    text = tab.url.removePrefix("https://").removePrefix("http://").ifBlank { "Start page" }
                    textSize = 11f
                    setTextColor(Color.rgb(83, 105, 123))
                    maxLines = 2
                })
                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(9), 0, 0)
                }
                actions.addView(TextView(this).apply {
                    text = if (selected) "● Active" else "Open"
                    textSize = 12f
                    setTextColor(if (selected) Color.rgb(8, 102, 217) else Color.rgb(83, 105, 123))
                }, LinearLayout.LayoutParams(0, dp(32), 1f))
                actions.addView(TextView(this).apply {
                    text = "×"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(190, 65, 75))
                    contentDescription = "Close tab"
                    setPadding(dp(12), 0, dp(5), 0)
                    setOnClickListener {
                        closeTab(tab)
                        dialog.dismiss()
                        showTabs()
                    }
                })
                card.addView(actions)
                card.setOnClickListener {
                    activateTab(tab)
                    dialog.dismiss()
                }
                row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(9))
                })
            }
            repeat(columns - rowTabs.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
            }
            grid.addView(row)
        }
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val done = TextView(this).apply {
            text = "Done"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(19, 43, 64), 24)
            setPadding(dp(20), dp(13), dp(20), dp(13))
        }
        page.addView(done, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        dialog = AlertDialog.Builder(this).setView(page).create()
        done.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    @ExperimentalGeckoViewApi
    @OptIn(ExperimentalGeckoViewApi::class)
    private fun showBrowserMenu() {
        val actions = arrayOf(
            if (store.isBookmarked(activeTab?.url.orEmpty())) "Remove bookmark" else "Add bookmark",
            "Bookmarks", "History", "Downloads", "Share page", "Copy page address", "Find in page", "Reader mode",
            "Site information", "Search engine", "Clear browsing history", "Settings"
        )
        AlertDialog.Builder(this).setTitle("BrowseX").setItems(actions) { _, index ->
            when (index) {
                0 -> activeTab?.url?.takeIf { it.startsWith("http") }?.let {
                    val added = store.toggleBookmark(it)
                    toast(if (added) "Bookmark saved" else "Bookmark removed")
                }
                1 -> showSavedPages("Bookmarks", store.bookmarks())
                2 -> showSavedPages("History", store.history())
                3 -> openDownloads()
                4 -> shareCurrentPage()
                5 -> copyCurrentAddress()
                6 -> findInPage()
                7 -> openReaderMode()
                8 -> showSiteInformation()
                9 -> chooseSearchEngine()
                10 -> AlertDialog.Builder(this).setMessage("Delete all browsing history?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear") { _, _ -> store.clearHistory(); toast("History cleared") }.show()
                11 -> showSettings()
            }
        }.show()
    }


    /** Extracts the current page through GeckoView's page extractor; no page scripts are injected. */
    @ExperimentalGeckoViewApi
    @OptIn(ExperimentalGeckoViewApi::class)
    private fun openReaderMode() {
        val tab = activeTab ?: return
        if (!tab.url.startsWith("http://") && !tab.url.startsWith("https://")) {
            toast("Open a webpage first")
            return
        }
        val sourceUrl = tab.url
        val loading = AlertDialog.Builder(this)
            .setTitle("Reader mode")
            .setMessage("Preparing a clean reading view…")
            .setNegativeButton("Cancel", null)
            .create()
        loading.show()
        tab.session.getSessionPageExtractor().getPageContent().accept(
            { extracted ->
                runOnUiThread {
                    if (loading.isShowing) loading.dismiss()
                    if (isFinishing || isDestroyed || activeTab !== tab || tab.url != sourceUrl) return@runOnUiThread
                    val content = extracted?.trim().orEmpty()
                    if (content.isBlank()) {
                        toast("This page does not contain readable text")
                    } else {
                        ReaderPage(this, tab.title, sourceUrl, content).show()
                    }
                }
            },
            { error ->
                runOnUiThread {
                    if (loading.isShowing) loading.dismiss()
                    android.util.Log.w("BrowseX", "Reader extraction failed", error)
                    if (!isFinishing && !isDestroyed) toast("Could not prepare reader view")
                }
            }
        )
    }


    private fun showSiteInformation() {
        val tab = activeTab ?: return
        val url = tab.url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast("Open a website first")
            return
        }
        SiteInfoPage(
            activity = this,
            url = url,
            title = tab.title,
            permissions = tab.sitePermissions
        ).show()
    }

    private fun showSettings() {
        SettingsPage(
            activity = this,
            store = store,
            openBookmarks = { showSavedPages("Bookmarks", store.bookmarks()) },
            openHistory = { showSavedPages("History", store.history()) },
            openDownloads = { openDownloads() },
            clearHistory = {
                store.clearHistory()
                toast("History cleared")
            }
        ).show()
    }

    private fun showSavedPages(title: String, pages: List<String>) {
        val isHistory = title.contains("History", ignoreCase = true)
        val currentPages = pages.toMutableList()
        if (currentPages.isEmpty()) { toast("Nothing here yet"); return }
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        scroll.addView(list)
        lateinit var dialog: AlertDialog
        fun render() {
            list.removeAllViews()
            if (currentPages.isEmpty()) {
                list.addView(TextView(this).apply {
                    text = "Nothing here yet"
                    setTextColor(Color.rgb(83, 105, 123))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(24), 0, dp(24))
                })
                return
            }
            currentPages.forEach { page ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(9), dp(6), dp(9))
                    background = rounded(Color.WHITE, 13)
                }
                val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                details.addView(TextView(this).apply {
                    text = Uri.parse(page).host ?: page
                    textSize = 14f
                    setTextColor(Color.rgb(19, 43, 64))
                    maxLines = 1
                })
                details.addView(TextView(this).apply {
                    text = page
                    textSize = 10f
                    setTextColor(Color.rgb(83, 105, 123))
                    maxLines = 2
                })
                item.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                val remove = TextView(this).apply {
                    text = "×"
                    textSize = 23f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(190, 65, 75))
                    contentDescription = "Remove saved page"
                    setPadding(dp(12), dp(4), dp(12), dp(4))
                    setOnClickListener {
                        if (isHistory) store.removeHistory(page) else store.removeBookmark(page)
                        currentPages.remove(page)
                        render()
                    }
                }
                item.addView(remove)
                item.setOnClickListener { createTab(page); dialog.dismiss() }
                list.addView(item, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(7)
                })
            }
        }
        render()
        dialog = AlertDialog.Builder(this).setTitle(title).setView(scroll)
            .setNegativeButton("Close", null).create()
        dialog.show()
    }

    /** Shows the in-app view over Android's durable DownloadManager queue. */
    private fun openDownloads() {
        DownloadsPage(this).show()
    }

    /** Routes GeckoView's external responses through Android's supported download service. */
    private fun enqueueDownload(response: WebResponse) {
        val url = response.uri
        val uri = Uri.parse(url)
        if ((uri.scheme != "https" && uri.scheme != "http") || uri.host.isNullOrBlank()) {
            response.body?.close()
            toast("This download link is not supported")
            return
        }
        val disposition = response.headers.entries.firstOrNull {
            it.key.equals("content-disposition", ignoreCase = true)
        }?.value
        val mime = response.headers.entries.firstOrNull {
            it.key.equals("content-type", ignoreCase = true)
        }?.value?.substringBefore(';')
        val filename = URLUtil.guessFileName(url, disposition, mime)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(180)
            .ifBlank { "download" }
        try {
            val request = DownloadManager.Request(uri)
                .setTitle(filename)
                .setDescription("Downloading with Browse X")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            if (!mime.isNullOrBlank()) request.setMimeType(mime)
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                request.addRequestHeader("Cookie", it)
            }
            request.addRequestHeader("User-Agent", org.mozilla.geckoview.GeckoSession.getDefaultUserAgent())
            val id = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            response.body?.close()
            toast("Download added: $filename")
            android.util.Log.i("BrowseX", "Download queued with id=$id")
        } catch (error: Exception) {
            response.body?.close()
            android.util.Log.e("BrowseX", "Unable to queue download", error)
            toast("Could not start download")
        }
    }

    private fun chooseSearchEngine() {
        val choices = arrayOf("Google", "DuckDuckGo", "Bing")
        AlertDialog.Builder(this).setTitle("Default search engine")
            .setSingleChoiceItems(choices, choices.indexOf(store.searchEngine).coerceAtLeast(0)) { dialog, which ->
                store.searchEngine = choices[which]
                dialog.dismiss()
                toast("Search engine: ${store.searchEngine}")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun shareCurrentPage() {
        val tab = activeTab ?: return
        val url = tab.url
        if (!url.startsWith("http")) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }, "Share page"))
    }

    /** Uses GeckoView's native finder so matches are highlighted in page content. */
    private fun findInPage() {
        val session = activeTab?.session ?: return
        val field = EditText(this).apply {
            hint = "Find text on this page"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val count = TextView(this).apply {
            text = "Type to search"
            setTextColor(Color.rgb(83, 105, 123))
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(field)
            addView(count)
        }
        lateinit var dialog: AlertDialog
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
        dialog = AlertDialog.Builder(this).setTitle("Find in page").setView(panel)
            .setNegativeButton("Close") { _, _ -> session.getFinder().clear() }
            .setNeutralButton("Next", null)
            .setPositiveButton("Previous", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                session.getFinder().find(null, GeckoSession.FINDER_FIND_FORWARD)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                session.getFinder().find(null, GeckoSession.FINDER_FIND_BACKWARDS)
            }
        }
        dialog.show()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        field.requestFocus()
    }

    private fun copyCurrentAddress() {
        val url = activeTab?.url.orEmpty()
        if (!url.startsWith("http")) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Page address", url))
        toast("Address copied")
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

}
