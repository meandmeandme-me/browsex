package com.browsex.app

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
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

private const val HOME_URL = "resource://android/assets/home.html"
private const val SEARCH_GOOGLE = "https://www.google.com/search?q="
private const val SEARCH_DDG = "https://html.duckduckgo.com/html/?q="
private const val SEARCH_BING = "https://www.bing.com/search?q="
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
    var canGoForward: Boolean = false
)

class MainActivity : AppCompatActivity() {
    private val tabs = mutableListOf<BrowserTab>()
    private var activeTab: BrowserTab? = null
    private lateinit var store: BrowserStore
    private lateinit var browserView: GeckoView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var backButton: TextView
    private lateinit var forwardButton: TextView
    private lateinit var tabsButton: TextView
    private var fullscreen = false
    private var shuttingDown = false
    private var adBlockExtension: WebExtension? = null
    private var adBlockReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BrowserStore(this)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_PANEL
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

        prepareBuiltInExtension()
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
            setBackgroundColor(COLOR_BG)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
        }
        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(6))
        }
        address = EditText(this).apply {
            hint = "Search or enter website"
            setHintTextColor(Color.rgb(145, 155, 168))
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setPadding(dp(16), 0, dp(12), 0)
            background = rounded(COLOR_FIELD, 24)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                    navigate(address.text.toString())
                    true
                } else false
            }
            setOnFocusChangeListener { _, focused -> if (!focused) updateAddress() }
        }
        address.background = rounded(Color.argb(225, 247, 248, 255), 24)
        address.setTextColor(Color.rgb(34, 42, 62))
        addressRow.addView(address, LinearLayout.LayoutParams(0, dp(46), 1f))
        addressRow.addView(actionButton("⋮", "Browser menu") { showBrowserMenu() }, LinearLayout.LayoutParams(dp(44), dp(46)))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(COLOR_ACCENT)
            visibility = View.GONE
        }
        topBar.addView(addressRow)
        topBar.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        browserView = GeckoView(this).apply { setBackgroundColor(Color.WHITE) }
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.argb(232, 248, 249, 255), 28)
            elevation = dp(10).toFloat()
            setPadding(dp(5), 0, dp(5), 0)
        }
        backButton = bottomButton("‹", "Back") { activeTab?.takeIf { it.canGoBack }?.session?.goBack() }
        forwardButton = bottomButton("›", "Forward") { activeTab?.takeIf { it.canGoForward }?.session?.goForward() }
        bottomButton("⌂", "Home") { activeTab?.session?.loadUri(HOME_URL) }
        bottomButton("+", "New tab") { createTab(HOME_URL) }
        tabsButton = bottomButton("1", "Tabs") { showTabs() }
        listOf(backButton, forwardButton).forEach { it.alpha = 0.45f }

        root.addView(topBar)
        root.addView(browserView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomBar, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(dp(14), dp(5), dp(14), dp(9)) })
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
            setTextColor(Color.WHITE)
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
        backButton.alpha = if (tab.canGoBack) 1f else 0.4f
        forwardButton.alpha = if (tab.canGoForward) 1f else 0.4f
        tabsButton.text = tabs.size.toString()
    }

    private fun updateAddress() {
        if (!::address.isInitialized || address.hasFocus()) return
        val url = activeTab?.url.orEmpty()
        address.setText(if (url.startsWith("resource://")) "" else url)
    }

    private fun normalizeInput(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return HOME_URL
        if (value.startsWith("about:") || value.startsWith("resource://")) return value
        val explicitScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(value)
        if (explicitScheme) {
            val scheme = Uri.parse(value).scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return value
            return SEARCH_GOOGLE + Uri.encode(value)
        }
        val looksLikeHost = !value.contains(" ") &&
            (value == "localhost" || value.contains(".") || value.startsWith("192.168."))
        if (looksLikeHost) return "https://$value"
        val base = when (store.searchEngine) {
            "DuckDuckGo" -> SEARCH_DDG
            "Bing" -> SEARCH_BING
            else -> SEARCH_GOOGLE
        }
        return base + Uri.encode(value)
    }

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

    private fun showTabs() {
        val labels = tabs.mapIndexed { index, tab ->
            val current = if (tab === activeTab) "✓ " else ""
            "$current${index + 1}. ${tab.title}".take(70)
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Open tabs · ${tabs.size}")
            .setItems(labels) { _, which -> activateTab(tabs.getOrNull(which) ?: return@setItems) }
            .setPositiveButton("New tab") { _, _ -> createTab(HOME_URL) }
            .setNegativeButton("Close current") { _, _ -> activeTab?.let(::closeTab) }
            .setNeutralButton("Done", null)
            .show()
    }

    private fun showBrowserMenu() {
        val actions = arrayOf(
            "Ad blocker: ${if (store.adBlockEnabled) "On" else "Off"}",
            if (store.isBookmarked(activeTab?.url.orEmpty())) "Remove bookmark" else "Add bookmark",
            "Bookmarks", "History", "Share page", "Copy page address",
            "Search engine", "Clear browsing history"
        )
        AlertDialog.Builder(this).setTitle("BrowseX").setItems(actions) { _, index ->
            when (index) {
                0 -> toggleAdBlocking()
                1 -> activeTab?.url?.takeIf { it.startsWith("http") }?.let {
                    val added = store.toggleBookmark(it)
                    toast(if (added) "Bookmark saved" else "Bookmark removed")
                }
                2 -> showSavedPages("Bookmarks", store.bookmarks())
                3 -> showSavedPages("History", store.history())
                4 -> shareCurrentPage()
                5 -> copyCurrentAddress()
                6 -> chooseSearchEngine()
                7 -> AlertDialog.Builder(this).setMessage("Delete all browsing history?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear") { _, _ -> store.clearHistory(); toast("History cleared") }.show()
            }
        }.show()
    }

    private fun showSavedPages(title: String, pages: List<String>) {
        if (pages.isEmpty()) { toast("Nothing saved yet"); return }
        AlertDialog.Builder(this).setTitle(title)
            .setItems(pages.map { it.take(80) }.toTypedArray()) { _, index ->
                pages.getOrNull(index)?.let { createTab(it) }
            }.setNegativeButton("Close", null).show()
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

    private fun prepareBuiltInExtension() {
        try {
            val files = assets.list("ublock")
            if (files.isNullOrEmpty()) {
                adBlockReady = true
                toast("Ad blocker files are not included in this build")
                return
            }
            BrowserEngine.runtime(this).webExtensionController.ensureBuiltIn(
                "resource://android/assets/ublock/",
                "uBlock0@raymondhill.net"
            ).accept({ extension ->
                adBlockExtension = extension
                adBlockReady = true
                if (!store.adBlockEnabled && extension.isEnabled) {
                    BrowserEngine.runtime(this).webExtensionController.disable(
                        extension, WebExtensionController.EnableSource.APP
                    ).accept({ adBlockExtension = it }, { })
                }
                if (store.adBlockEnabled && !extension.isEnabled) {
                    BrowserEngine.runtime(this).webExtensionController.enable(
                        extension, WebExtensionController.EnableSource.APP
                    ).accept({ adBlockExtension = it }, { })
                }
            }, { error ->
                adBlockReady = true
                android.util.Log.e("BrowseX", "uBlock Origin could not be installed", error)
                runOnUiThread { toast("Ad blocker could not start") }
            })
        } catch (error: Exception) {
            adBlockReady = true
            android.util.Log.e("BrowseX", "uBlock Origin setup failed", error)
        }
    }

    private fun toggleAdBlocking() {
        val extension = adBlockExtension
        if (!adBlockReady || extension == null) {
            toast("Ad blocker is not ready. Check this build includes uBlock Origin.")
            return
        }
        val controller = BrowserEngine.runtime(this).webExtensionController
        val enable = !store.adBlockEnabled
        val operation = if (enable) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        operation.accept({ updated ->
            adBlockExtension = updated
            store.adBlockEnabled = enable
            toast(if (enable) "Ad blocking enabled" else "Ad blocking paused")
        }, { error ->
            android.util.Log.e("BrowseX", "Could not change ad blocker state", error)
            toast("Could not change ad blocker")
        })
    }
}
