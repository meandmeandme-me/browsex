package com.browsex.app

import android.Manifest
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.app.NotificationManagerCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.WebResponse
import org.mozilla.geckoview.WebNotification

private const val HOME_URL = "resource://android/assets/home.html"

/** GeckoRuntime is process-wide. Sessions are owned by MainActivity and always closed there. */
private object BrowserEngine {
    @Volatile private var instance: GeckoRuntime? = null
    fun runtime(context: Context): GeckoRuntime = instance ?: synchronized(this) {
        instance ?: GeckoRuntime.create(context.applicationContext).also { instance = it }
    }
}

private fun newBrowserSession(privateMode: Boolean): GeckoSession =
    GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(privateMode).build())

private data class BrowserTab(
    val privateMode: Boolean = false,
    var session: GeckoSession = newBrowserSession(privateMode),
    var sessionOpened: Boolean = false,
    var title: String = "New tab",
    var url: String = HOME_URL,
    var progress: Int = 0,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var sitePermissions: List<GeckoSession.PermissionDelegate.ContentPermission> = emptyList(),
    var permissionDelegate: BrowseXPermissionDelegate? = null,
    var recoveryAttempts: Int = 0,
    var crashed: Boolean = false,
    var lastActiveAt: Long = 0L
)

private data class PendingPlatformPermission(
    val session: GeckoSession,
    val permissions: List<String>,
    val callback: GeckoSession.PermissionDelegate.Callback,
    var completed: Boolean = false
)

@androidx.annotation.OptIn(markerClass = [ExperimentalGeckoViewApi::class])
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
    private var browserRuntime: GeckoRuntime? = null
    private var notificationBridge: BrowseXNotificationBridge? = null
    private lateinit var platformPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private val queuedPlatformPermissions = java.util.ArrayDeque<PendingPlatformPermission>()
    private var activePlatformPermission: PendingPlatformPermission? = null
    private val notificationPermissionCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var notificationPermissionInFlight = false
    private lateinit var legacyDownloadPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var bookmarkExportLauncher: ActivityResultLauncher<String>
    private lateinit var bookmarkImportLauncher: ActivityResultLauncher<Array<String>>
    private val waitingForLegacyDownloads = java.util.ArrayDeque<WebResponse>()
    private var legacyDownloadPermissionInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BrowserStore(this)
        bookmarkExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) runCatching {
                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(store.exportBookmarks().toByteArray(Charsets.UTF_8))
                } ?: error("Could not open destination")
            }.onSuccess { toast("Bookmarks exported") }
                .onFailure { android.util.Log.e("BrowseX", "Bookmark export failed", it); toast("Could not export bookmarks") }
        }
        bookmarkImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 1_000_000) { "Backup file is too large" }
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                } ?: error("Could not read backup")
                String(bytes, Charsets.UTF_8)
            }.onSuccess { payload ->
                AlertDialog.Builder(this)
                    .setTitle(tr("Import bookmarks"))
                    .setItems(arrayOf(tr("Merge with current bookmarks"), tr("Replace current bookmarks"))) { _, which ->
                        runCatching { store.importBookmarks(payload, merge = which == 0) }
                            .onSuccess { count -> toast("Imported $count bookmarks") }
                            .onFailure { error -> android.util.Log.w("BrowseX", "Bookmark import rejected", error); toast(error.message ?: "Could not import bookmarks") }
                    }
                    .setNegativeButton(tr("Cancel"), null)
                    .show()
            }.onFailure { error -> android.util.Log.w("BrowseX", "Bookmark backup read failed", error); toast(error.message ?: "Could not read backup") }
        }
        AppCompatDelegate.setDefaultNightMode(when (store.themeMode) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(if (store.languageMode == "system") "" else store.languageMode)
        )
        platformPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            val pending = activePlatformPermission
            activePlatformPermission = null
            if (pending != null && !pending.completed) {
                val granted = pending.permissions.all { grants[it] == true } &&
                    pending.session === activeTab?.session && tabs.any { it.session === pending.session }
                pending.permissions.forEach { permission ->
                    if (grants[permission] == true) store.clearPlatformPermissionDenial(permission)
                    else store.markPlatformPermissionDenied(permission)
                }
                if (granted) pending.callback.grant() else pending.callback.reject()
            }
            launchNextPlatformPermission()
        }
        legacyDownloadPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            legacyDownloadPermissionInFlight = false
            val pending = waitingForLegacyDownloads.toList()
            waitingForLegacyDownloads.clear()
            if (granted) pending.forEach(::enqueueDownload)
            else {
                pending.forEach { it.body?.close() }
                if (!isFinishing && !isDestroyed) toast("Storage permission was denied; download cancelled")
            }
        }
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            notificationPermissionInFlight = false
            if (granted) store.clearPlatformPermissionDenial(Manifest.permission.POST_NOTIFICATIONS)
            else store.markPlatformPermissionDenied(Manifest.permission.POST_NOTIFICATIONS)
            val callbacks = notificationPermissionCallbacks.toList()
            notificationPermissionCallbacks.clear()
            callbacks.forEach { callback -> callback(granted && NotificationManagerCompat.from(this).areNotificationsEnabled()) }
            launchNextPlatformPermission()
        }
        browserRuntime = BrowserEngine.runtime(applicationContext)
        browserRuntime?.settings?.setPreferredColorScheme(when (store.themeMode) {
            "dark" -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
            "light" -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            else -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
        })
        val requestedLocales = if (store.languageMode == "system") {
            resources.configuration.locales.toLanguageTags().split(',').filter { it.isNotBlank() }.toTypedArray()
        } else {
            arrayOf(store.languageMode)
        }
        browserRuntime?.settings?.setLocales(requestedLocales)
        notificationBridge = BrowseXNotificationBridge(this, store, ::requestSiteNotificationPermission)
        browserRuntime?.setWebNotificationDelegate(notificationBridge)
        window.statusBarColor = colors.canvas
        window.navigationBarColor = colors.canvas
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !colors.isDark
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = !colors.isDark
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

        val snapshot = store.restoreOpenTabs()
        snapshot.urls.forEach { savedUrl -> tabs.add(BrowserTab(url = savedUrl)) }
        val restoredActive = tabs.getOrNull(snapshot.activeIndex)
        val externalUrl = intent?.data?.let(::safeExternalUrl)
        if (externalUrl != null) {
            if (tabs.size >= TabSessionState.MAX_TABS) {
                val discardIndex = tabs.indices.firstOrNull { tabs[it] !== restoredActive } ?: 0
                tabs.removeAt(discardIndex)
            }
            val externalTab = BrowserTab(url = externalUrl)
            tabs.add(externalTab)
            activateTab(externalTab)
        } else {
            val initialTab = restoredActive ?: BrowserTab(url = homePageUrl()).also(tabs::add)
            activateTab(initialTab)
        }
        handleSiteNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == BrowseXNotificationBridge.ACTION_SITE_NOTIFICATION_CLICK) {
            handleSiteNotificationIntent(intent)
        } else {
            intent.data?.let(::safeExternalUrl)?.let(::createTab)
        }
    }

    override fun onStart() {
        super.onStart()
        activeTab?.takeIf { it.sessionOpened }?.session?.let {
            runCatching { it.setActive(true); it.setFocused(true) }
        }
    }

    override fun onStop() {
        persistTabs()
        tabs.filter { it.sessionOpened }.forEach { tab ->
            runCatching { tab.session.setFocused(false); tab.session.setActive(false) }
        }
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            persistTabs()
            tabs.filter { it !== activeTab && it.sessionOpened }.forEach { tab ->
                runCatching { tab.session.setFocused(false); tab.session.setActive(false) }
            }
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            // Keep the visible tab alive; inactive tabs retain their URL and reopen lazily.
            tabs.filter { it !== activeTab && it.sessionOpened }
                .sortedBy { it.lastActiveAt }
                .forEach(::releaseInactiveSession)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        persistTabs()
        tabs.filter { it !== activeTab && it.sessionOpened }
            .sortedBy { it.lastActiveAt }
            .forEach(::releaseInactiveSession)
    }

    private fun releaseInactiveSession(tab: BrowserTab) {
        if (tab === activeTab || !tab.sessionOpened) return
        tab.permissionDelegate?.cancelPending()
        cancelPlatformPermissionRequests(tab.session)
        runCatching {
            tab.session.setFocused(false)
            tab.session.setActive(false)
            tab.session.close()
        }
        tab.session = newBrowserSession(tab.privateMode)
        tab.sessionOpened = false
        tab.permissionDelegate = null
        tab.sitePermissions = emptyList()
    }

    /** Bound live Gecko sessions to the visible tab plus two warm background tabs. */
    private fun enforceLiveSessionBudget() {
        val maxLiveSessions = 3
        while (tabs.count { it.sessionOpened } > maxLiveSessions) {
            val oldest = tabs.filter { it !== activeTab && it.sessionOpened }
                .minByOrNull { it.lastActiveAt } ?: return
            releaseInactiveSession(oldest)
        }
    }

    private fun persistTabs() {
        if (!::store.isInitialized) return
        val regularTabs = tabs.filterNot { it.privateMode }
        val activeIndex = regularTabs.indexOf(activeTab).takeIf { it >= 0 } ?: 0
        store.saveOpenTabs(regularTabs.map { it.url }, activeIndex)
    }

    override fun onDestroy() {
        persistTabs()
        shuttingDown = true
        queuedPlatformPermissions.toList().forEach { it.callback.reject() }
        queuedPlatformPermissions.clear()
        waitingForLegacyDownloads.toList().forEach { it.body?.close() }
        waitingForLegacyDownloads.clear()
        activePlatformPermission?.let { pending ->
            if (!pending.completed) pending.callback.reject()
            pending.completed = true
        }
        activePlatformPermission = null
        notificationPermissionCallbacks.toList().forEach { it(false) }
        notificationPermissionCallbacks.clear()
        tabs.toList().forEach { tab ->
            tab.permissionDelegate?.cancelPending()
            cancelPlatformPermissionRequests(tab.session)
        }
        browserRuntime?.setWebNotificationDelegate(null)
        if (::browserView.isInitialized) browserView.releaseSession()
        tabs.toList().forEach { tab ->
            if (tab.sessionOpened) {
                runCatching { tab.session.setFocused(false); tab.session.setActive(false); tab.session.close() }
            }
            tab.sessionOpened = false
        }
        tabs.clear()
        activeTab = null
        super.onDestroy()
    }

    fun requestAndroidPermissions(
        session: GeckoSession,
        requested: Array<String>,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        if (shuttingDown || session !== activeTab?.session) {
            callback.reject()
            return
        }
        val supported = setOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        val permissions = requested.distinct()
        if (permissions.isEmpty() || permissions.any { it !in supported }) {
            callback.reject()
            return
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            permissions.forEach(store::clearPlatformPermissionDenial)
            callback.grant()
            return
        }
        if (missing.any(store::wasPlatformPermissionDenied)) {
            callback.reject()
            return
        }
        queuedPlatformPermissions.add(PendingPlatformPermission(session, missing, callback))
        launchNextPlatformPermission()
    }

    private fun launchNextPlatformPermission() {
        if (notificationPermissionInFlight || activePlatformPermission != null ||
            !::platformPermissionLauncher.isInitialized || isFinishing || isDestroyed
        ) return
        while (queuedPlatformPermissions.isNotEmpty()) {
            val next = queuedPlatformPermissions.removeFirst()
            if (next.completed || next.session !== activeTab?.session ||
                tabs.none { it.session === next.session }
            ) {
                if (!next.completed) next.callback.reject()
                continue
            }
            activePlatformPermission = next
            platformPermissionLauncher.launch(next.permissions.toTypedArray())
            return
        }
    }

    private fun cancelPlatformPermissionRequests(session: GeckoSession) {
        val iterator = queuedPlatformPermissions.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (pending.session === session) {
                pending.completed = true
                pending.callback.reject()
                iterator.remove()
            }
        }
        activePlatformPermission?.takeIf { it.session === session && !it.completed }?.let {
            it.completed = true
            it.callback.reject()
        }
    }

    fun requestSiteNotificationPermission(callback: (Boolean) -> Unit) {
        if (!store.siteNotificationsEnabled) {
            callback(false)
            return
        }
        if (Build.VERSION.SDK_INT < 33) {
            callback(NotificationManagerCompat.from(this).areNotificationsEnabled())
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            store.clearPlatformPermissionDenial(Manifest.permission.POST_NOTIFICATIONS)
            callback(NotificationManagerCompat.from(this).areNotificationsEnabled())
            return
        }
        if (store.wasPlatformPermissionDenied(Manifest.permission.POST_NOTIFICATIONS) ||
            !::notificationPermissionLauncher.isInitialized || isFinishing || isDestroyed
        ) {
            callback(false)
            return
        }
        notificationPermissionCallbacks.add(callback)
        if (!notificationPermissionInFlight) {
            notificationPermissionInFlight = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Suppress("DEPRECATION")
    private fun handleSiteNotificationIntent(intent: Intent?) {
        if (intent?.action != BrowseXNotificationBridge.ACTION_SITE_NOTIFICATION_CLICK) return
        val notification = intent.getParcelableExtra<WebNotification>(BrowseXNotificationBridge.EXTRA_WEB_NOTIFICATION)
        if (notification != null) {
            runCatching { notification.click(); notification.dismiss() }
            val source = notification.source?.let { safeExternalUrl(Uri.parse(it)) }
            if (source != null && !isFinishing && !isDestroyed) {
                val sourceUri = Uri.parse(source)
                val existing = tabs.firstOrNull { tab ->
                    val tabUri = Uri.parse(tab.url)
                    tabUri.scheme.equals(sourceUri.scheme, true) &&
                        tabUri.host.equals(sourceUri.host, true) &&
                        tabUri.port == sourceUri.port
                }
                if (existing != null) activateTab(existing) else createTab(source)
            }
        }
        intent.action = null
        intent.removeExtra(BrowseXNotificationBridge.EXTRA_WEB_NOTIFICATION)
    }

    private val colors get() = BrowseXColors.resolve(this, store)

    private fun tr(value: String): String {
        if (!BrowseXColors.isArabic(store)) return value
        return when (value) {
            "Home" -> "الرئيسية"
            "Open tabs" -> "فتح علامات التبويب"
            "Search or enter URL" -> "ابحث أو أدخل عنوان الموقع"
            "Your space" -> "مساحة التصفح"
            "tab" -> "تبويب"
            "tabs" -> "تبويبات"
            "Tap a card to return to it" -> "اضغط على بطاقة للعودة إلى التبويب"
            "New tab" -> "تبويب جديد"
            "Remove bookmark" -> "إزالة من المفضلة"
            "Add bookmark" -> "إضافة للمفضلة"
            "Bookmarks" -> "المفضلة"
            "History" -> "السجل"
            "Downloads" -> "التنزيلات"
            "Share page" -> "مشاركة الصفحة"
            "Copy page address" -> "نسخ عنوان الصفحة"
            "Find in page" -> "بحث في الصفحة"
            "Reader mode" -> "وضع القراءة"
            "Site information" -> "معلومات الموقع"
            "Search engine" -> "محرك البحث"
            "Clear browsing history" -> "مسح سجل التصفح"
            "BrowseX" -> "BrowseX"
            "Open webpage first" -> "افتح صفحة ويب أولًا"
            "Open a webpage first" -> "افتح صفحة ويب أولًا"
            "Preparing a clean reading view…" -> "جارٍ تجهيز وضع القراءة…"
            "Cancel" -> "إلغاء"
            "Clear" -> "مسح"
            "Delete all browsing history?" -> "حذف سجل التصفح بالكامل؟"
            "Close" -> "إغلاق"
            "Back" -> "رجوع"
            "Forward" -> "للأمام"
            "Reload" -> "إعادة تحميل"
            "Tabs" -> "علامات التبويب"
            "New tab" -> "تبويب جديد"
            "Bookmark page" -> "إضافة للمفضلة"
            "Site information" -> "معلومات الموقع"
            "Downloads" -> "التنزيلات"
            "Settings" -> "الإعدادات"
            "New private tab" -> "تبويب خاص جديد"
            "Close private tabs" -> "إغلاق التبويبات الخاصة"
            "No private tabs are open" -> "لا توجد تبويبات خاصة مفتوحة"
            "Close all private tabs? Their browsing data will be discarded." -> "إغلاق جميع التبويبات الخاصة؟ سيتم التخلص من بيانات تصفحها."
            "Reader mode" -> "وضع القراءة"
            "Find in page" -> "بحث في الصفحة"
            "Share page" -> "مشاركة الصفحة"
            "Copy address" -> "نسخ العنوان"
            "Close tab" -> "إغلاق التبويب"
            "Open a website first" -> "افتح موقعًا أولًا"
            "History cleared" -> "تم مسح السجل"
            "Page bookmarked" -> "تمت إضافة الصفحة للمفضلة"
            "Bookmark removed" -> "تمت إزالة الصفحة من المفضلة"
            "This download link is not supported" -> "رابط التنزيل غير مدعوم"
            "Android denied access to the Downloads folder" -> "رفض أندرويد الوصول إلى مجلد التنزيلات"
            "Storage permission was denied; download cancelled" -> "تم رفض إذن التخزين؛ أُلغي التنزيل"
            "No app can open this file" -> "لا يوجد تطبيق لفتح هذا الملف"
            "You can keep up to 20 tabs open" -> "يمكنك فتح 20 تبويبًا كحد أقصى"
            "Search or enter an address" -> "ابحث أو أدخل عنوانًا"
            "Search or enter URL" -> "ابحث أو أدخل عنوان الموقع"
            "Import bookmarks" -> "استيراد المفضلة"
            "Merge with current bookmarks" -> "دمج مع المفضلة الحالية"
            "Replace current bookmarks" -> "استبدال المفضلة الحالية"
            "Bookmarks exported" -> "تم تصدير المفضلة"
            "Could not export bookmarks" -> "تعذر تصدير المفضلة"
            "Could not read backup" -> "تعذرت قراءة النسخة الاحتياطية"
            "Show favorite shortcuts" -> "إظهار اختصارات المفضلة"
            "Show discovery card" -> "إظهار بطاقة الاستكشاف"
            "Home page" -> "الصفحة الرئيسية"
            "Next" -> "التالي"
            "Grid layout" -> "تخطيط شبكي"
            "List layout" -> "تخطيط قائمة"
            "Shortcut layout" -> "تخطيط الاختصارات"
            "Reset" -> "إعادة ضبط"
            "Site permissions reset" -> "تمت إعادة ضبط أذونات الموقع"
            else -> value
        }
    }

    private fun homePageUrl(): String =
        "$HOME_URL?bxlang=${store.languageMode}&bxtheme=${store.themeMode}" +
            "&bxquick=${store.homeShowQuickLinks}&bxdiscovery=${store.homeShowDiscovery}&bxlayout=${store.homeLayout}"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createBrowserUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.canvas)
            BrowseXColors.applyDirection(this, store)
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
            setBackgroundColor(colors.canvas)
        }
        val home = actionButton("⌂", "Home") { activeTab?.session?.loadUri(homePageUrl()) }.apply {
            background = rounded(colors.card, 10)
            textSize = 22f
        }
        topBar.addView(home, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            marginEnd = dp(4)
        })

        address = EditText(this).apply {
            hint = tr("Search or enter URL")
            setHintTextColor(colors.muted)
            setTextColor(colors.ink)
            textSize = 14f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setPadding(dp(14), 0, dp(12), 0)
            background = rounded(colors.card, 24)
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
            background = rounded(colors.card, 10)
            textSize = 20f
        }
        topBar.addView(tabsButton, LinearLayout.LayoutParams(dp(44), dp(48)).apply {
            marginEnd = dp(4)
        })
        val menu = actionButton("⋮", "Browser menu") { showBrowserMenu() }.apply {
            background = rounded(colors.card, 10)
            textSize = 22f
        }
        topBar.addView(menu, LinearLayout.LayoutParams(dp(44), dp(48)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(colors.accent)
            visibility = View.GONE
        }
        browserView = GeckoView(this).apply { setBackgroundColor(colors.card) }
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(5), dp(14), dp(8))
            background = rounded(colors.card, 22)
            elevation = dp(5).toFloat()
        }
        bottomButton("‹", "Go back") { activeTab?.takeIf { it.canGoBack }?.session?.goBack() }
        bottomButton("›", "Go forward") { activeTab?.takeIf { it.canGoForward }?.session?.goForward() }
        bottomButton("＋", "New tab") { createTab(homePageUrl()) }
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
        BrowseXColors.applyDirection(root, store)
        return root
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun actionButton(label: String, description: String, action: () -> Unit) =
        TextView(this).apply {
            text = tr(label)
            contentDescription = tr(description)
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(colors.ink)
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            setOnClickListener { action() }
        }

    private fun bottomButton(label: String, description: String, action: () -> Unit) =
        actionButton(label, description, action).also {
            it.textSize = 23f
            it.setTextColor(colors.ink)
            bottomBar.addView(it, LinearLayout.LayoutParams(0, -1, 1f))
        }

    private fun updateChrome() {
        val tab = activeTab ?: return
        if (!address.hasFocus()) updateAddress()
        progress.progress = tab.progress
        progress.visibility = if (tab.progress in 1..99) View.VISIBLE else View.GONE
        tabsButton.text = "▢ ${tabs.size}"
        tabsButton.contentDescription = "${tr("Open tabs")}: ${tabs.size}"
    }

    private fun updateAddress() {
        if (!::address.isInitialized || address.hasFocus()) return
        val url = activeTab?.url.orEmpty()
        address.setText(if (url.startsWith("resource://")) "" else url)
    }

    private fun normalizeInput(raw: String): String =
        if (raw.isBlank()) homePageUrl() else NavigationInput.normalize(raw, store.searchEngine, HOME_URL)

    private fun safeExternalUrl(uri: Uri): String? = NavigationInput.externalWebUrl(uri.toString())

    private fun navigate(raw: String) {
        val tab = activeTab ?: return
        val target = normalizeInput(raw)
        tab.session.loadUri(target)
        address.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)
        browserView.requestFocus()
    }

    private fun createTab(url: String, privateMode: Boolean = false) {
        if (shuttingDown) return
        if (tabs.size >= TabSessionState.MAX_TABS) {
            toast("You can keep up to ${TabSessionState.MAX_TABS} tabs open")
            return
        }
        val targetUrl = if (url == HOME_URL) homePageUrl() else url
        val tab = BrowserTab(privateMode = privateMode, url = targetUrl)
        tabs.add(tab)
        activateTab(tab)
    }

    private fun activateTab(tab: BrowserTab) {
        if (!tabs.contains(tab)) return
        val previous = activeTab
        if (previous !== tab) {
            previous?.takeIf { it.sessionOpened }?.let {
                runCatching { it.session.setFocused(false); it.session.setActive(false) }
            }
        }
        activeTab = tab
        tab.lastActiveAt = android.os.SystemClock.elapsedRealtime()
        try {
            if (tab.crashed) {
                tab.recoveryAttempts = 0
                tab.crashed = false
            }
            if (!tab.sessionOpened) {
                attachDelegates(tab)
                tab.session.open(BrowserEngine.runtime(this))
                tab.sessionOpened = true
                tab.session.loadUri(tab.url.ifBlank { homePageUrl() })
            }
            tab.session.setActive(true)
            tab.session.setFocused(true)
            browserView.setSession(tab.session)
            enforceLiveSessionBudget()
            updateChrome()
        } catch (error: RuntimeException) {
            tab.sessionOpened = false
            tab.crashed = true
            android.util.Log.e("BrowseX", "Could not activate tab session", error)
            toast("This tab could not be opened. Try again.")
            updateChrome()
        }
    }

    private fun closeTab(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        val wasActive = activeTab === tab
        tab.permissionDelegate?.cancelPending()
        cancelPlatformPermissionRequests(tab.session)
        if (tab.sessionOpened) {
            runCatching { tab.session.setFocused(false); tab.session.setActive(false) }
        }
        if (wasActive) browserView.releaseSession()
        tabs.removeAt(index)
        if (tab.sessionOpened) runCatching { tab.session.close() }
        tab.sessionOpened = false
        if (tabs.isEmpty()) createTab(homePageUrl())
        else if (wasActive) activateTab(tabs[minOf(index, tabs.lastIndex)])
        else updateChrome()
    }

    private fun attachDelegates(tab: BrowserTab) {
        val session = tab.session
        tab.permissionDelegate = BrowseXPermissionDelegate(
            this,
            store,
            { requested ->
                requested === activeTab?.session && tabs.any { it.session === requested } && !shuttingDown
            },
            tab.privateMode
        )
        session.permissionDelegate = tab.permissionDelegate
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
                if (success) {
                    if (!tab.privateMode) store.addHistory(tab.url)
                    tab.recoveryAttempts = 0
                    tab.crashed = false
                }
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
                if (isFinishing || shuttingDown || tabs.size >= TabSessionState.MAX_TABS) {
                    runOnUiThread { if (!isFinishing && !shuttingDown) toast("Tab limit reached") }
                    return null
                }
                val child = BrowserTab(privateMode = tab.privateMode)
                attachDelegates(child)
                child.session.open(BrowserEngine.runtime(this@MainActivity))
                child.sessionOpened = true
                tabs.add(child)
                runOnUiThread {
                    if (!isFinishing && !shuttingDown && tabs.contains(child)) activateTab(child)
                    else if (tabs.contains(child)) closeTab(child)
                }
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
            override fun onCrash(session: GeckoSession) {
                runOnUiThread {
                    if (shuttingDown || isFinishing || isDestroyed ||
                        !tabs.contains(tab) || tab.session !== session
                    ) return@runOnUiThread
                    if (tab.recoveryAttempts >= 1) {
                        tab.crashed = true
                        tab.progress = 0
                        if (tab === activeTab) toast("This tab crashed again. Reopen it to continue.")
                        updateChrome()
                        return@runOnUiThread
                    }
                    tab.recoveryAttempts += 1
                    val restoreUrl = tab.url.ifBlank { homePageUrl() }
                    val wasActive = tab === activeTab
                    tab.permissionDelegate?.cancelPending()
                    cancelPlatformPermissionRequests(session)
                    attachDelegates(tab)
                    try {
                        session.open(BrowserEngine.runtime(this@MainActivity))
                        tab.sessionOpened = true
                        if (wasActive) {
                            activateTab(tab)
                            session.loadUri(restoreUrl)
                        } else {
                            session.setFocused(false)
                            session.setActive(false)
                            session.loadUri(restoreUrl)
                        }
                    } catch (error: RuntimeException) {
                        tab.sessionOpened = false
                        tab.crashed = true
                        android.util.Log.e("BrowseX", "Tab recovery failed", error)
                        if (wasActive) toast("Could not restore this tab. Open a new tab.")
                    }
                }
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
            setBackgroundColor(colors.canvas)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(this).apply {
            text = tr("Your space")
            textSize = 25f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(colors.ink)
        })
        heading.addView(TextView(this).apply {
            text = if (BrowseXColors.isArabic(store)) "${tabs.size} تبويب مفتوح" else "${tabs.size} open ${if (tabs.size == 1) "tab" else "tabs"}"
            textSize = 13f
            setTextColor(colors.muted)
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val add = TextView(this).apply {
            text = "+  ${tr("New tab")}"
            textSize = 14f
            setTextColor(colors.card)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = rounded(colors.accent, 22)
            setOnClickListener { createTab(homePageUrl()); dialog.dismiss(); showTabs() }
        }
        header.addView(add)
        page.addView(header)

        val hint = TextView(this).apply {
            text = tr("Tap a card to return to it")
            textSize = 12f
            setTextColor(colors.muted)
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
                    background = rounded(colors.card, 18)
                    elevation = dp(if (selected) 4 else 1).toFloat()
                }
                val preview = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    background = rounded(if (selected) colors.selected else colors.panel, 12)
                }
                preview.addView(TextView(this).apply {
                    text = "Browse X"
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(colors.accent)
                })
                preview.addView(TextView(this).apply {
                    text = (if (tab.privateMode) "Private · " else "") + tab.title.ifBlank { "New tab" }
                    textSize = 13f
                    setTextColor(colors.ink)
                    maxLines = 2
                    gravity = Gravity.CENTER
                    setPadding(0, dp(7), 0, dp(3))
                })
                card.addView(preview, LinearLayout.LayoutParams(-1, dp(104)))
                card.addView(TextView(this).apply {
                    text = (if (tab.privateMode) "Private · " else "") + tab.title.ifBlank { "New tab" }
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(colors.ink)
                    maxLines = 1
                    setPadding(0, dp(10), 0, dp(3))
                })
                card.addView(TextView(this).apply {
                    text = tab.url.removePrefix("https://").removePrefix("http://").ifBlank { "Start page" }
                    textSize = 11f
                    setTextColor(colors.muted)
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
                    setTextColor(if (selected) colors.accent else colors.muted)
                }, LinearLayout.LayoutParams(0, dp(32), 1f))
                actions.addView(TextView(this).apply {
                    text = "×"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(colors.danger)
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
            setTextColor(colors.card)
            background = rounded(colors.ink, 24)
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

    private fun showBrowserMenu() {
        val actions = arrayOf(
            if (store.isBookmarked(activeTab?.url.orEmpty())) "Remove bookmark" else "Add bookmark",
            "Bookmarks", "History", "Downloads", "Share page", "Copy page address", "Find in page", "Reader mode",
            "Site information", "Search engine", "Clear browsing history", "Settings",
            "New private tab", "Close private tabs"
        ).map(::tr).toTypedArray()
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
                10 -> AlertDialog.Builder(this).setMessage(tr("Delete all browsing history?"))
                    .setNegativeButton(tr("Cancel"), null)
                    .setPositiveButton(tr("Clear")) { _, _ -> store.clearHistory(); toast("History cleared") }.show()
                11 -> showSettings()
                12 -> createTab(homePageUrl(), privateMode = true)
                13 -> {
                    val privateTabs = tabs.filter { it.privateMode }
                    if (privateTabs.isEmpty()) toast("No private tabs are open")
                    else AlertDialog.Builder(this)
                        .setMessage(tr("Close all private tabs? Their browsing data will be discarded."))
                        .setNegativeButton(tr("Cancel"), null)
                        .setPositiveButton(tr("Close")) { _, _ -> privateTabs.forEach(::closeTab) }
                        .show()
                }
            }
        }.show()
    }


    /** Extracts the current page through GeckoView's page extractor; no page scripts are injected. */
    private fun openReaderMode() {
        val tab = activeTab ?: return
        if (!tab.url.startsWith("http://") && !tab.url.startsWith("https://")) {
            toast("Open a webpage first")
            return
        }
        val sourceUrl = tab.url
        val loading = AlertDialog.Builder(this)
            .setTitle(tr("Reader mode"))
            .setMessage(tr("Preparing a clean reading view…"))
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
                        ReaderPage(this, tab.title, sourceUrl, content, store).show()
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
        fun showPage() {
            if (activeTab !== tab || tab.url != url || isFinishing || isDestroyed) return
            SiteInfoPage(
                activity = this,
                url = url,
                title = tab.title,
                permissions = tab.sitePermissions,
                store = store,
                onPermissionChange = { permission, value ->
                    val storage = browserRuntime?.storageController
                    storage?.setPermission(permission, value)
                    storage?.getPermissions(url, false)?.accept(
                        { updated -> runOnUiThread {
                            if (tabs.contains(tab) && tab.url == url) tab.sitePermissions = updated.orEmpty().toList()
                        } },
                        { error -> android.util.Log.w("BrowseX", "Could not refresh site permissions", error) }
                    )
                },
                onResetPermissions = {
                    val storage = browserRuntime?.storageController
                    tab.sitePermissions.filter {
                        it.permission != GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE
                    }.forEach { permission ->
                        storage?.setPermission(
                            permission,
                            GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT
                        )
                    }
                    val uri = Uri.parse(url)
                    val origin = Uri.Builder().scheme(uri.scheme).encodedAuthority(uri.encodedAuthority).build().toString()
                    store.clearSiteMediaDecisions(origin)
                    tab.sitePermissions = emptyList()
                    storage?.getPermissions(url, false)?.accept(
                        { updated -> runOnUiThread {
                            if (tabs.contains(tab) && tab.url == url) tab.sitePermissions = updated.orEmpty().toList()
                        } },
                        { error -> android.util.Log.w("BrowseX", "Could not refresh reset permissions", error) }
                    )
                    toast("Site permissions reset")
                }
            ).show()
        }
        val storage = browserRuntime?.storageController
        if (storage == null) {
            showPage()
            return
        }
        storage.getPermissions(url, false).accept(
            { permissions -> runOnUiThread {
                if (activeTab !== tab || tab.url != url || isFinishing || isDestroyed) return@runOnUiThread
                tab.sitePermissions = permissions.orEmpty().toList()
                showPage()
            } },
            { error -> runOnUiThread {
                android.util.Log.w("BrowseX", "Could not load site permissions", error)
                showPage()
            } }
        )
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
            },
            setWebsiteNotifications = { enabled ->
                store.siteNotificationsEnabled = enabled
                if (!enabled) NotificationManagerCompat.from(this).cancelAll()
            },
            importBookmarks = { bookmarkImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
            exportBookmarks = { bookmarkExportLauncher.launch("browsex-bookmarks.json") },
            showHomePreferences = { showHomePreferences() },
            openPrivacyCenter = { showPrivacyCenter() }
        ).show()
    }

    private fun showHomePreferences() {
        val labels = arrayOf(tr("Show favorite shortcuts"), tr("Show discovery card"))
        val checked = booleanArrayOf(store.homeShowQuickLinks, store.homeShowDiscovery)
        AlertDialog.Builder(this)
            .setTitle(tr("Home page"))
            .setMultiChoiceItems(labels, checked) { _, which, enabled ->
                if (which == 0) store.homeShowQuickLinks = enabled else store.homeShowDiscovery = enabled
            }
            .setPositiveButton(tr("Next")) { _, _ ->
                val layouts = arrayOf(tr("Grid layout"), tr("List layout"))
                val values = arrayOf("grid", "list")
                AlertDialog.Builder(this)
                    .setTitle(tr("Shortcut layout"))
                    .setSingleChoiceItems(layouts, values.indexOf(store.homeLayout).coerceAtLeast(0)) { choice, index ->
                        store.homeLayout = values[index]
                        choice.dismiss()
                        activeTab?.takeIf { it.url.startsWith(HOME_URL) }?.session?.loadUri(homePageUrl())
                    }
                    .setNeutralButton(tr("Reset")) { _, _ ->
                        store.resetHomePreferences()
                        activeTab?.takeIf { it.url.startsWith(HOME_URL) }?.session?.loadUri(homePageUrl())
                    }
                    .setNegativeButton(tr("Close"), null)
                    .show()
            }
            .setNegativeButton(tr("Cancel"), null)
            .show()
    }


    private fun showPrivacyCenter() {
        PrivacyPage(
            activity = this,
            store = store,
            clearData = ::clearBrowsingData
        ).show()
    }

    /**
     * GeckoView requires every open session to be closed before persistent site data is cleared.
     * Keep only validated tab URLs, close the sessions, clear data, then recreate the tabs lazily.
     */
    private fun clearBrowsingData(flags: Long, clearHistory: Boolean) {
        val regularTabs = tabs.filterNot { it.privateMode }
        val snapshot = TabSessionState.normalize(
            regularTabs.map { it.url },
            regularTabs.indexOf(activeTab).takeIf { it >= 0 } ?: 0,
            HOME_URL
        )
        tabs.toList().forEach { tab ->
            tab.permissionDelegate?.cancelPending()
            cancelPlatformPermissionRequests(tab.session)
        }
        if (::browserView.isInitialized) browserView.releaseSession()
        tabs.toList().forEach { tab ->
            if (tab.sessionOpened) {
                runCatching {
                    tab.session.setFocused(false)
                    tab.session.setActive(false)
                    tab.session.close()
                }
            }
            tab.session = newBrowserSession(tab.privateMode)
            tab.sessionOpened = false
            tab.permissionDelegate = null
            tab.sitePermissions = emptyList()
        }
        tabs.clear()
        activeTab = null

        fun restoreAfterClear(success: Boolean) {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (success) {
                    if (clearHistory) store.clearHistory()
                    if ((flags and org.mozilla.geckoview.StorageController.ClearFlags.PERMISSIONS) != 0L) {
                        store.clearAllSiteMediaDecisions()
                    }
                }
                snapshot.urls.forEach { tabs.add(BrowserTab(url = it)) }
                val restored = tabs.getOrNull(snapshot.activeIndex)
                    ?: BrowserTab(url = homePageUrl()).also(tabs::add)
                activateTab(restored)
                persistTabs()
                toast(if (success) "Selected browsing data cleared" else "Could not clear browsing data")
            }
        }

        if (flags == 0L) {
            restoreAfterClear(true)
            return
        }
        val storage = browserRuntime?.storageController
        if (storage == null) {
            restoreAfterClear(false)
            return
        }
        try {
            storage.clearData(flags).accept(
                { restoreAfterClear(true) },
                { error ->
                    android.util.Log.e("BrowseX", "Browsing data clearing failed", error)
                    restoreAfterClear(false)
                }
            )
        } catch (error: RuntimeException) {
            android.util.Log.e("BrowseX", "Could not start browsing data clearing", error)
            restoreAfterClear(false)
        }
    }

    private fun showSavedPages(title: String, pages: List<String>) {
        val isHistory = title.contains("History", ignoreCase = true)
        SavedPagesPage(
            activity = this,
            store = store,
            title = title,
            initialPages = pages,
            isHistory = isHistory,
            onOpen = { url -> createTab(url) }
        ).show()
    }

    /** Shows the in-app view over Android's durable DownloadManager queue. */
    private fun openDownloads() {
        DownloadsPage(this, store).show()
    }

    /** Routes GeckoView's external responses through Android's supported download service. */
    private fun enqueueDownload(response: WebResponse) {
        val url = response.uri
        val uri = try { Uri.parse(url) } catch (_: Exception) { null }
        if (uri == null || (uri.scheme != "https" && uri.scheme != "http") ||
            uri.host.isNullOrBlank() || uri.userInfo != null
        ) {
            response.body?.close()
            toast("This download link is not supported")
            return
        }
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            response.body?.close()
            if (!::legacyDownloadPermissionLauncher.isInitialized || isFinishing || isDestroyed) {
                toast("Storage permission is unavailable")
                return
            }
            waitingForLegacyDownloads.add(response)
            if (!legacyDownloadPermissionInFlight) {
                legacyDownloadPermissionInFlight = true
                legacyDownloadPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            return
        }
        val disposition = response.headers.entries.firstOrNull {
            it.key.equals("content-disposition", ignoreCase = true)
        }?.value
        val mime = response.headers.entries.firstOrNull {
            it.key.equals("content-type", ignoreCase = true)
        }?.value?.substringBefore(';')
        val candidate = URLUtil.guessFileName(url, disposition, mime)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .filterNot { it.isISOControl() }
            .trim()
            .trim('.')
            .take(140)
            .ifBlank { "download" }
        val filename = store.reserveDownloadFileName(candidate)
        try {
            val request = DownloadManager.Request(uri)
                .setTitle(filename)
                .setDescription("Downloading with BrowseX")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            if (!mime.isNullOrBlank()) request.setMimeType(mime)
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                request.addRequestHeader("Cookie", it)
            }
            request.addRequestHeader("User-Agent", GeckoSession.getDefaultUserAgent())
            val id = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            store.addBrowseXDownload(id)
            response.body?.close()
            toast("Download queued: $filename. Protected or POST requests may not transfer exactly.")
            android.util.Log.i("BrowseX", "Download queued with id=$id")
        } catch (error: SecurityException) {
            response.body?.close()
            android.util.Log.e("BrowseX", "Download destination permission denied", error)
            toast("Android denied access to the Downloads folder")
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
            setTextColor(colors.muted)
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
        android.widget.Toast.makeText(this, tr(message), android.widget.Toast.LENGTH_SHORT).show()
    }

}
