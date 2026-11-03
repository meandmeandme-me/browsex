package com.browsex.app

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
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

private const val TAG = "BrowseX"
private const val HOME = "resource://android/assets/home.html"

private val BG = Color.parseColor("#0F1217")
private val BAR = Color.parseColor("#161B22")
private val FIELD = Color.parseColor("#1E252E")
private val ACCENT = Color.parseColor("#2F7BFF")

/** One GeckoRuntime (the Firefox engine) for the whole app. */
object GeckoHolder {
    private var runtime: GeckoRuntime? = null
    fun get(context: Context): GeckoRuntime {
        val existing = runtime
        if (existing != null) return existing
        val created = GeckoRuntime.create(context.applicationContext)
        runtime = created
        return created
    }
}

/** One browser tab = one GeckoSession. */
class Tab(val session: GeckoSession) {
    var title: String = ""
    var url: String = ""
    var canGoBack: Boolean = false
    var canGoForward: Boolean = false
    var progress: Int = 0
}

class MainActivity : AppCompatActivity() {

    private val tabs = mutableListOf<Tab>()
    private var current: Tab? = null
    private var isFullscreen = false
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var geckoView: GeckoView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var backBtn: TextView
    private lateinit var fwdBtn: TextView
    private lateinit var tabsBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BAR
        setContentView(buildUi())

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val tab = current
                if (isFullscreen) {
                    tab?.session?.exitFullScreen()
                } else if (tab != null && tab.canGoBack) {
                    tab.session.goBack()
                } else if (tabs.size > 1 && tab != null) {
                    closeTab(tab)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        installExtensions()
        openTab(intent?.data?.toString() ?: HOME)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { openTab(it.toString()) }
    }

    override fun onDestroy() {
        super.onDestroy()
        tabs.forEach { it.session.close() }
    }

    // ---------------------------------------------------------------- UI

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Top: address bar + thin progress bar
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        urlBar = EditText(this).apply {
            hint = "Search or enter address"
            setHintTextColor(Color.parseColor("#7D8794"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setPadding(dp(18), 0, dp(18), 0)
            background = GradientDrawable().apply {
                setColor(FIELD)
                cornerRadius = dp(22).toFloat()
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    go(text.toString())
                    true
                } else {
                    false
                }
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) updateUrlBar()
            }
        }
        val urlRow = LinearLayout(this).apply {
            setPadding(dp(10), dp(8), dp(10), dp(6))
        }
        urlRow.addView(
            urlBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
        )
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = false
            progressTintList = ColorStateList.valueOf(ACCENT)
            visibility = View.INVISIBLE
        }
        topBar.addView(urlRow)
        topBar.addView(
            progressBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        )

        // Middle: the Firefox engine view
        geckoView = GeckoView(this)

        // Bottom: buttons
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(BAR)
        }
        backBtn = barButton("Back") { current?.session?.goBack() }
        fwdBtn = barButton("Forward") { current?.session?.goForward() }
        val reloadBtn = barButton("Reload") { current?.session?.reload() }
        val homeBtn = barButton("Home") { current?.session?.loadUri(HOME) }
        tabsBtn = barButton("Tabs 1") { showTabs() }
        listOf(backBtn, fwdBtn, reloadBtn, homeBtn, tabsBtn).forEach { bottomBar.addView(it) }

        root.addView(topBar)
        root.addView(
            geckoView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        root.addView(
            bottomBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
        )
        return root
    }

    private fun barButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { onClick() }
        }
    }

    private fun refreshUi() {
        val t = current ?: return
        updateUrlBar()
        progressBar.progress = t.progress
        progressBar.visibility = if (t.progress in 1..99) View.VISIBLE else View.INVISIBLE
        backBtn.alpha = if (t.canGoBack) 1f else 0.35f
        fwdBtn.alpha = if (t.canGoForward) 1f else 0.35f
        tabsBtn.text = "Tabs ${tabs.size}"
    }

    private fun updateUrlBar() {
        if (urlBar.hasFocus()) return
        val u = current?.url ?: ""
        urlBar.setText(if (u.startsWith("resource://")) "" else u)
    }

    private fun setFullscreen(fs: Boolean) {
        isFullscreen = fs
        topBar.visibility = if (fs) View.GONE else View.VISIBLE
        bottomBar.visibility = if (fs) View.GONE else View.VISIBLE
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fs) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---------------------------------------------------------------- Navigation

    private fun toUri(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return HOME
        val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t) ||
            t.startsWith("about:")
        if (hasScheme) return t
        if (!t.contains(' ') && (t.contains('.') || t.startsWith("localhost"))) {
            return "https://$t"
        }
        return "https://www.google.com/search?q=" + Uri.encode(t)
    }

    private fun go(input: String) {
        current?.session?.loadUri(toUri(input))
        urlBar.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
        geckoView.requestFocus()
    }

    // ---------------------------------------------------------------- Tabs

    private fun attachDelegates(tab: Tab) {
        val s = tab.session

        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.progress = 5
                if (tab === current) refreshUi()
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress
                if (tab === current) refreshUi()
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.progress = 100
                if (tab === current) refreshUi()
            }
        }

        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                tab.url = url ?: ""
                if (tab === current) refreshUi()
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
                if (tab === current) refreshUi()
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
                if (tab === current) refreshUi()
            }

            // Pages that open a new window after a real tap become a new tab.
            // (Gecko itself already blocks popups that happen without a tap.)
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val newTab = Tab(GeckoSession())
                attachDelegates(newTab)
                tabs.add(newTab)
                // Gecko opens the returned session itself; show it right after.
                ui.post { switchTo(newTab) }
                return GeckoResult.fromValue(newTab.session)
            }
        }

        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title ?: ""
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (tab === current) setFullscreen(fullScreen)
            }

            override fun onCloseRequest(session: GeckoSession) {
                closeTab(tab)
            }
        }
    }

    private fun openTab(uri: String) {
        val tab = Tab(GeckoSession())
        attachDelegates(tab)
        tab.session.open(GeckoHolder.get(this))
        tabs.add(tab)
        switchTo(tab)
        tab.session.loadUri(uri)
    }

    private fun switchTo(tab: Tab) {
        current = tab
        geckoView.setSession(tab.session)
        refreshUi()
    }

    private fun closeTab(tab: Tab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        val wasCurrent = tab === current
        if (wasCurrent) geckoView.releaseSession()
        tabs.removeAt(index)
        tab.session.close()
        if (tabs.isEmpty()) {
            openTab(HOME)
        } else if (wasCurrent) {
            switchTo(tabs[minOf(index, tabs.size - 1)])
        } else {
            refreshUi()
        }
    }

    private fun showTabs() {
        val labels = tabs.map {
            val name = if (it.title.isNotBlank()) it.title else it.url
            (if (name.isBlank()) "New tab" else name).take(45)
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Tabs (${tabs.size})")
            .setItems(labels) { _, which -> switchTo(tabs[which]) }
            .setPositiveButton("New tab") { _, _ -> openTab(HOME) }
            .setNegativeButton("Close this tab") { _, _ -> current?.let { closeTab(it) } }
            .setNeutralButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- Extensions

    /**
     * Installs uBlock Origin if the build bundled it (GitHub Actions downloads it
     * into assets/ublock). Fire-and-forget: Gecko logs any problem in logcat.
     */
    private fun installExtensions() {
        try {
            val files = assets.list("ublock")
            if (files == null || files.isEmpty()) {
                Log.i(TAG, "uBlock Origin is not bundled in this build")
                return
            }
            GeckoHolder.get(this).webExtensionController.ensureBuiltIn(
                "resource://android/assets/ublock/",
                "uBlock0@raymondhill.net"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not install uBlock Origin", e)
        }
    }
}
