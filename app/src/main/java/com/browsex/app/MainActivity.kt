package com.browsex.app

import android.Manifest
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.CookieManager
import android.os.Environment

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.browsex.app.storage.BrowserStorageRuntime
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.WebResponse
import org.mozilla.geckoview.WebNotification

internal const val HOME_URL = "resource://android/assets/home.html"


@androidx.annotation.OptIn(markerClass = [ExperimentalGeckoViewApi::class])
class MainActivity : AppCompatActivity() {
    private val tabRegistry = BrowserTabRegistry<BrowserTab>()
    private val deviceCapabilityProfile by lazy { DeviceCapabilityProbe(this).snapshot() }
    private val adaptiveRuntimeConfig by lazy {
        AdaptiveRuntimePolicy.resolve(deviceCapabilityProfile, DeviceCapabilityProbe(this).runtimePressure())
    }
    private val tabEventCoordinator by lazy {
        BrowserTabEventCoordinator(
            isActiveTab = { tab -> tab === activeTab },
            updateChrome = ::updateChrome,
            recordHistory = { url, title ->
                lifecycleScope.launch {
                    runCatching { storageRuntime.awaitReady().history.recordVisit(url, title) }
                        .onFailure { error ->
                            android.util.Log.e("BrowseX", "Could not persist browsing history", error)
                        }
                }
            },
            openPopupTab = { parent -> tabController.onNewSession(parent) },
            setFullscreen = ::setFullscreen,
            closeTab = ::closeTab,
            enqueueDownload = ::enqueueDownload,
            recoverContentProcess = { tab, session ->
                contentProcessRecoveryCoordinator.onContentProcessCrash(tab, session)
            }
        )
    }
    private val sessionDelegateFactory by lazy {
        BrowserSessionDelegateFactory(tabEventCoordinator)
    }
    private val sessionLifecycleCoordinator by lazy {
        BrowserSessionLifecycleCoordinator(
            registry = tabRegistry,
            beforeSessionClose = { tab ->
                tab.permissionDelegate?.cancelPending()
                cancelPlatformPermissionRequests(tab.session)
            },
            createSession = ::newBrowserSession,
            maxLiveSessions = adaptiveRuntimeConfig.maxLiveGeckoSessions
        )
    }
    private val tabController by lazy {
        BrowserTabController(
            registry = tabRegistry,
            sessionLifecycle = sessionLifecycleCoordinator,
            homeUrl = ::homePageUrl,
            isHostUnavailable = { shuttingDown || isFinishing || isDestroyed },
            attachDelegates = ::attachDelegates,
            runtime = { BrowserRuntimeOwner.runtime(this) },
            attachSessionToView = { session -> browserView.setSession(session) },
            releaseViewSession = { browserView.releaseSession() },
            enforceSessionBudget = ::enforceLiveSessionBudget,
            updateChrome = ::updateChrome,
            showMessage = ::toast,
            recordRecentlyClosed = { tab -> storageRuntime.recordRecentlyClosed(tab.url, tab.title) },
            dispatchToMain = { action -> runOnUiThread(action) },
            setExtensionTabActive = { session, active ->
                uBlockOriginManager?.setTabActive(session, active)
            }
        )
    }
    private val tabs: List<BrowserTab> get() = tabController.tabs
    private val activeTab: BrowserTab? get() = tabController.activeTab
    private val contentProcessRecoveryCoordinator by lazy {
        BrowserContentProcessRecoveryCoordinator(
            dispatchToMain = { action -> runOnUiThread(action) },
            isHostUnavailable = { shuttingDown || isFinishing || isDestroyed },
            containsTab = { tab -> tabs.any { it === tab } },
            isActiveTab = { tab -> tab === activeTab },
            homeUrl = ::homePageUrl,
            runtime = { BrowserRuntimeOwner.runtime(this) },
            cancelPlatformPermissions = ::cancelPlatformPermissionRequests,
            attachDelegates = ::attachDelegates,
            activateTab = ::activateTab,
            updateUi = ::updateChrome,
            showMessage = ::toast
        )
    }
    private lateinit var store: BrowserStore
    internal val storageRuntime: BrowserStorageRuntime
        get() = (application as BrowseXApplication).storageRuntime
    private lateinit var credentialVault: BrowserCredentialVault
    private val hostLifecycleCoordinator by lazy {
        BrowserHostLifecycleCoordinator(
            tabs = { tabs },
            activeTab = { activeTab },
            isSessionOpened = { it.sessionOpened },
            persistTabs = ::persistTabs,
            setSessionActive = { tab, active ->
                runCatching {
                    if (active) {
                        tab.session.setActive(true)
                        tab.session.setFocused(true)
                    } else {
                        tab.session.setFocused(false)
                        tab.session.setActive(false)
                    }
                    uBlockOriginManager?.setTabActive(tab.session, active)
                }
            },
            releaseForMemoryPressure = sessionLifecycleCoordinator::releaseForMemoryPressure
        )
    }
    private lateinit var streamDownloadCoordinator: StreamDownloadCoordinator
    private lateinit var downloadRequestCoordinator: BrowserDownloadRequestCoordinator
    private lateinit var browserView: GeckoView
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var tabsButton: TextView
    private lateinit var uBlockButton: TextView
    private var fullscreen = false
    private var shuttingDown = false
    private var initialBrowserTabsRestored = false
    private var browserRuntime: GeckoRuntime? = null
    private var uBlockOriginManager: UBlockOriginManager? = null
    private var notificationBridge: BrowseXNotificationBridge? = null
    private var dataClearDialog: AlertDialog? = null
    private val browsingDataCoordinator by lazy {
        BrowserBrowsingDataCoordinator(
            tabs = { tabs },
            activeTab = { activeTab },
            sessionLifecycle = sessionLifecycleCoordinator,
            releaseViewSession = { if (::browserView.isInitialized) browserView.releaseSession() },
            replaceTabs = tabController::replaceTabs,
            activateRestoredSelection = tabController::activateRestoredSelection,
            persistTabs = ::persistTabs,
            storageController = { browserRuntime?.storageController },
            clearHistory = {
                lifecycleScope.launch {
                    runCatching { storageRuntime.awaitReady().history.clear() }
                        .onFailure { error ->
                            android.util.Log.e("BrowseX", "Could not clear browsing history", error)
                        }
                }
            },
            clearSiteMediaDecisions = { origin ->
                if (origin == null) store.clearAllSiteMediaDecisions()
                else store.clearSiteMediaDecisions(origin)
            },
            dispatchToMain = { action -> runOnUiThread(action) },
            isHostUnavailable = { isFinishing || isDestroyed },
            onBusyChanged = ::setBrowsingDataBusy,
            onFinished = { success ->
                toast(if (success) "Selected browsing data cleared" else "Could not clear browsing data")
            }
        )
    }
    private lateinit var platformPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var platformPermissionCoordinator: BrowserAndroidPermissionCoordinator
    private lateinit var notificationPermissionCoordinator: BrowserNotificationPermissionCoordinator
    private lateinit var legacyDownloadPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var bookmarkExportLauncher: ActivityResultLauncher<String>
    private lateinit var bookmarkImportLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BrowserStore(this)
        BrowserContentProtectionPolicy.configure(store.adBlockEnabled, store.adBlockPrivateEnabled)
        credentialVault = BrowserCredentialVault(applicationContext) { onAuthenticated, onRejected ->
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    onRejected()
                } else {
                    BrowserDeviceAuthenticator.authenticate(
                        activity = this,
                        title = "Unlock saved passwords",
                        subtitle = "Verify your identity to use saved passwords",
                        onAuthenticated = onAuthenticated,
                        onRejected = onRejected
                    )
                }
            }
        }
        store.recoverInterruptedStreamDownloads()
        streamDownloadCoordinator = StreamDownloadCoordinator(storageRuntime, DownloadResponseWriter(applicationContext))
        bookmarkExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) lifecycleScope.launch {
                runCatching {
                    val payload = storageRuntime.awaitReady().bookmarks.exportBookmarks()
                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open destination")
                }.onSuccess { toast("Bookmarks exported") }
                    .onFailure { android.util.Log.e("BrowseX", "Bookmark export failed", it); toast("Could not export bookmarks") }
            }
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
                lifecycleScope.launch {
                    val preview = runCatching { storageRuntime.awaitReady().bookmarks.previewImport(payload) }.getOrElse { error ->
                        android.util.Log.w("BrowseX", "Bookmark import preview rejected", error)
                        toast(error.message ?: "Could not read backup")
                        return@launch
                    }
                    val summary = getString(
                        R.string.ui_import_preview_message,
                        preview.totalItems,
                        preview.validItems,
                        preview.skippedItems,
                        preview.duplicateIncomingItems,
                        preview.existingUrlConflicts,
                        preview.incomingFolders
                    )
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(tr("Import preview"))
                        .setMessage(summary)
                        .setPositiveButton(tr("Merge with current bookmarks")) { _, _ ->
                            lifecycleScope.launch {
                                runCatching { storageRuntime.awaitReady().bookmarks.importBookmarks(payload, merge = true) }
                                    .onSuccess { result -> toast("Imported ${result.importedBookmarks} bookmarks") }
                                    .onFailure { error -> android.util.Log.w("BrowseX", "Bookmark import rejected", error); toast(error.message ?: "Could not import bookmarks") }
                            }
                        }
                        .setNegativeButton(tr("Replace current bookmarks")) { _, _ ->
                            AlertDialog.Builder(this@MainActivity)
                                .setMessage(tr("Replace all current bookmarks and folders?"))
                                .setNegativeButton(tr("Cancel"), null)
                                .setPositiveButton(tr("Replace")) { _, _ ->
                                    lifecycleScope.launch {
                                        runCatching { storageRuntime.awaitReady().bookmarks.importBookmarks(payload, merge = false) }
                                            .onSuccess { result -> toast("Imported ${result.importedBookmarks} bookmarks") }
                                            .onFailure { error -> android.util.Log.w("BrowseX", "Bookmark import rejected", error); toast(error.message ?: "Could not import bookmarks") }
                                    }
                                }
                                .show()
                        }
                        .setNeutralButton(tr("Cancel"), null)
                        .show()
                }
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
            platformPermissionCoordinator.onResult(grants)
        }
        platformPermissionCoordinator = BrowserAndroidPermissionCoordinator(
            activeSession = { activeTab?.session },
            containsSession = { session -> tabs.any { it.session === session } },
            isHostAvailable = { !shuttingDown && !isFinishing && !isDestroyed },
            anotherPromptInFlight = { ::notificationPermissionCoordinator.isInitialized && notificationPermissionCoordinator.isInFlight },
            isGranted = { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            },
            wasDenied = store::wasPlatformPermissionDenied,
            clearDenial = store::clearPlatformPermissionDenial,
            markDenied = store::markPlatformPermissionDenied,
            launchRequest = { permissions -> platformPermissionLauncher.launch(permissions) }
        )
        legacyDownloadPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (::downloadRequestCoordinator.isInitialized) {
                downloadRequestCoordinator.onLegacyPermissionResult(granted)
            }
        }
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            notificationPermissionCoordinator.onResult(granted)
        }
        notificationPermissionCoordinator = BrowserNotificationPermissionCoordinator(
            apiLevel = Build.VERSION.SDK_INT,
            featureEnabled = { store.siteNotificationsEnabled },
            permissionGranted = {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            },
            notificationsEnabled = { NotificationManagerCompat.from(this).areNotificationsEnabled() },
            previouslyDenied = { store.wasPlatformPermissionDenied(Manifest.permission.POST_NOTIFICATIONS) },
            clearDenial = { store.clearPlatformPermissionDenial(Manifest.permission.POST_NOTIFICATIONS) },
            markDenied = { store.markPlatformPermissionDenied(Manifest.permission.POST_NOTIFICATIONS) },
            hostAvailable = { !isFinishing && !isDestroyed },
            launchRequest = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            afterResolved = { platformPermissionCoordinator.launchNext() }
        )
        downloadRequestCoordinator = BrowserDownloadRequestCoordinator(
            apiLevel = Build.VERSION.SDK_INT,
            hasLegacyStoragePermission = {
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
            },
            canRequestLegacyStoragePermission = {
                ::legacyDownloadPermissionLauncher.isInitialized && !isFinishing && !isDestroyed
            },
            requestLegacyStoragePermission = {
                legacyDownloadPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            },
            startDownload = ::startStreamDownload,
            isHostAvailable = { !isFinishing && !isDestroyed },
            showMessage = ::toast
        )
        browserRuntime = BrowserRuntimeOwner.runtime(applicationContext)
        browserRuntime?.setAutocompleteStorageDelegate(credentialVault)
        browserRuntime?.settings?.setLoginAutofillEnabled(true)
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
        // Temporary engine-only host: the legacy chrome is intentionally detached while the
        // browser engine and feature services are reorganized for the new UI architecture.
        browserView = GeckoView(this)
        setContentView(browserView)

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

        val extensionRuntime = browserRuntime
        if (extensionRuntime == null) {
            BrowserContentProtectionPolicy.configure(store.adBlockEnabled, store.adBlockPrivateEnabled)
            restoreInitialBrowserTabs(intent)
        } else {
            uBlockOriginManager = UBlockOriginManager(
                activity = this,
                runtime = extensionRuntime,
                activeSession = { activeTab?.session },
                existingSessions = { tabs.filter { it.sessionOpened }.map { it.session } },
                createTab = { parentSession ->
                    val parent = tabs.firstOrNull { it.session === parentSession } ?: activeTab
                    parent?.let { tabController.onNewSession(it) }
                },
                closeTab = { session ->
                    val tab = tabs.firstOrNull { it.session === session }
                    if (tab == null) false else {
                        closeTab(tab)
                        true
                    }
                },
                onActionChanged = {
                    if (!isFinishing && !isDestroyed) {
                        runOnUiThread { syncUBlockButton() }
                    }
                }
            ).also { manager ->
                manager.refresh(
                    onComplete = { installed ->
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            val enabled = installed?.metaData?.enabled == true
                            val privateAllowed = enabled && installed?.metaData?.allowedInPrivateBrowsing == true
                            store.adBlockInstalled = installed != null
                            store.adBlockEnabled = enabled
                            store.adBlockPrivateEnabled = privateAllowed
                            BrowserContentProtectionPolicy.configure(enabled, privateAllowed)
                            restoreInitialBrowserTabs(intent)
                        }
                    },
                    onFailure = { error ->
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            android.util.Log.w("BrowseX", "Could not discover uBlock Origin", error)
                            // Unknown extension state must never leave Gecko tracking protection disabled.
                            BrowserContentProtectionPolicy.configure(enabled = false, privateAllowed = false)
                            restoreInitialBrowserTabs(intent)
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == BrowseXNotificationBridge.ACTION_SITE_NOTIFICATION_CLICK) {
            handleSiteNotificationIntent(intent)
        } else {
            intent.data?.let(::safeExternalUrl)?.let { url -> createTab(url) }
        }
    }

    override fun onStart() {
        super.onStart()
        hostLifecycleCoordinator.onStart()
    }

    override fun onStop() {
        if (::credentialVault.isInitialized) credentialVault.lock()
        hostLifecycleCoordinator.onStop()
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        hostLifecycleCoordinator.onTrimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        hostLifecycleCoordinator.onLowMemory()
    }

    /** GeckoSession resource limits and background release are owned by the lifecycle coordinator. */
    private fun enforceLiveSessionBudget() {
        sessionLifecycleCoordinator.enforceLiveSessionBudget()
    }

    private fun persistTabs() {
        if (!::store.isInitialized || !initialBrowserTabsRestored) return
        val currentTabs = tabs
        val selectedIndex = currentTabs.indexOfFirst { it === activeTab }
        val snapshot = BrowserTabPersistencePolicy.snapshot(
            urls = currentTabs.map { it.url },
            privateFlags = currentTabs.map { it.privateMode },
            activeIndex = selectedIndex,
            homeUrl = homePageUrl()
        )
        storageRuntime.persistOpenTabs(snapshot.urls, snapshot.activeIndex)
    }

    override fun onDestroy() {
        persistTabs()
        shuttingDown = true
        if (::platformPermissionCoordinator.isInitialized) platformPermissionCoordinator.rejectAll()
        if (::notificationPermissionCoordinator.isInitialized) notificationPermissionCoordinator.rejectAll()
        if (::credentialVault.isInitialized) {
            credentialVault.lock()
            credentialVault.setPrivateTabsOpen(false)
        }
        if (::downloadRequestCoordinator.isInitialized) downloadRequestCoordinator.cancelPending()
        if (::streamDownloadCoordinator.isInitialized) streamDownloadCoordinator.shutdown()
        uBlockOriginManager?.destroy()
        uBlockOriginManager = null
        dataClearDialog?.dismiss()
        dataClearDialog = null
        browserRuntime?.setWebNotificationDelegate(null)
        if (::browserView.isInitialized) browserView.releaseSession()
        sessionLifecycleCoordinator.closeAllSessions()
        tabController.clear()
        super.onDestroy()
    }

    fun requestAndroidPermissions(
        session: GeckoSession,
        requested: Array<String>,
        callback: GeckoSession.PermissionDelegate.Callback
    ) = platformPermissionCoordinator.request(session, requested, callback)

    private fun cancelPlatformPermissionRequests(session: GeckoSession) {
        platformPermissionCoordinator.cancelSession(session)
    }

    fun requestSiteNotificationPermission(callback: (Boolean) -> Unit) =
        notificationPermissionCoordinator.request(callback)

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
                    if (tab.privateMode) return@firstOrNull false
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

    private val passwordManager by lazy {
        BrowserPasswordManagerCoordinator(
            activity = this,
            vault = credentialVault,
            isHostUnavailable = { isFinishing || isDestroyed },
            showMessage = ::toast,
            translate = ::tr
        )
    }

    private val uBlockSettings by lazy {
        BrowserUBlockSettingsCoordinator(
            activity = this,
            manager = { uBlockOriginManager },
            store = store,
            activeSession = { activeTab?.session },
            isHostUnavailable = { isFinishing || isDestroyed },
            showMessage = ::toast,
            translate = ::tr,
            dp = ::dp,
            textColor = colors.ink,
            onSettingsChanged = ::recreate
        )
    }

    private val pageActions by lazy {
        BrowserPageActionsCoordinator(
            activity = this,
            activeSession = { activeTab?.session },
            activeUrl = { activeTab?.url },
            currentSearchEngine = { store.searchEngine },
            updateSearchEngine = { store.searchEngine = it },
            showMessage = ::toast,
            mutedColor = { colors.muted },
            dp = ::dp
        )
    }


    private fun tr(value: String): String = BrowseXStrings.text(this, value)

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
        uBlockButton = actionButton("uB", "uBlock Origin controls") {
            uBlockOriginManager?.clickBrowserAction(activeTab?.session)
        }.apply {
            textSize = 14f
            visibility = View.GONE
            background = rounded(colors.card, 10)
        }
        topBar.addView(uBlockButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
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
                val added = store.toggleBookmark(it, activeTab?.title.orEmpty().takeUnless { title -> title == "New tab" }.orEmpty())
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
        // Presentation is intentionally absent during the engine/platform refoundation.
        if (::credentialVault.isInitialized) credentialVault.setPrivateTabsOpen(tabs.any { it.privateMode })
    }

    private fun syncUBlockButton() = Unit

    private fun updateAddress() = Unit

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

    private fun createTab(url: String, privateMode: Boolean = false) =
        tabController.createTab(url, privateMode)

    private fun activateTab(tab: BrowserTab) = tabController.activateTab(tab)

    private fun closeTab(tab: BrowserTab) = tabController.closeTab(tab)

    private fun attachDelegates(tab: BrowserTab) {
        tab.permissionDelegate = BrowseXPermissionDelegate(
            this,
            store,
            { requested ->
                requested === activeTab?.session && tabs.any { it.session === requested } && !shuttingDown
            },
            tab.privateMode
        )
        tab.session.permissionDelegate = tab.permissionDelegate
        sessionDelegateFactory.attach(tab)
        uBlockOriginManager?.attachSession(tab.session)
        tab.session.promptDelegate = BrowserCredentialPromptDelegate(
            activity = this,
            privateMode = tab.privateMode,
            hasPrivateTabsOpen = { credentialVault.privateBrowsingActive },
            onDeviceAuthenticated = credentialVault::unlockForSession,
            isStillValid = { session, origin ->
                !shuttingDown && tabs.any { it === tab } && tab.session === session &&
                    CredentialOriginPolicy.canonicalHttpsOrigin(tab.url) == origin
            }
        )
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
        TabsPage(
            activity = this,
            store = store,
            tabsProvider = { tabs },
            activeTabProvider = { activeTab },
            onCreateTab = { url -> createTab(url) },
            onActivateTab = ::activateTab,
            onCloseTab = ::closeTab,
            homePageUrlProvider = ::homePageUrl,
            onToast = ::toast
        ).show()
    }

    private fun showBrowserMenu() {
        val actions = arrayOf(
            if (store.isBookmarked(activeTab?.url.orEmpty())) "Remove bookmark" else "Add bookmark",
            "Bookmarks", "History", "Downloads", "Share page", "Copy page address", "Find in page", "Reader mode",
            "Site information", "Search engine", "Clear browsing history", "Settings",
            "New private tab", "Close private tabs", "Find anything", "Passwords"
        ).map(::tr).toTypedArray()
        AlertDialog.Builder(this).setTitle("BrowseX").setItems(actions) { _, index ->
            when (index) {
                0 -> activeTab?.url?.takeIf { it.startsWith("http") }?.let {
                    val added = store.toggleBookmark(it, activeTab?.title.orEmpty().takeUnless { title -> title == "New tab" }.orEmpty())
                    toast(if (added) "Bookmark saved" else "Bookmark removed")
                }
                1 -> showSavedPages("Bookmarks", store.bookmarks())
                2 -> showSavedPages("History", store.history())
                3 -> openDownloads()
                4 -> pageActions.shareCurrentPage()
                5 -> pageActions.copyCurrentAddress()
                6 -> pageActions.findInPage()
                7 -> openReaderMode()
                8 -> showSiteInformation()
                9 -> pageActions.chooseSearchEngine()
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
                14 -> showRecall()
                15 -> passwordManager.show()
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


    private fun originForUrl(url: String): String =
        BrowserSiteOriginPolicy.canonicalOrigin(url).orEmpty()

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
                            if (tabs.any { it === tab } && tab.url == url) tab.sitePermissions = updated.orEmpty().toList()
                        } },
                        { error -> android.util.Log.w("BrowseX", "Could not refresh site permissions", error) }
                    )
                },
                onClearSiteData = {
                    clearBrowsingDataForHost(Uri.parse(url).host.orEmpty(), originForUrl(url))
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
                            if (tabs.any { it === tab } && tab.url == url) tab.sitePermissions = updated.orEmpty().toList()
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
            openPrivacyCenter = { showPrivacyCenter() },
            manageUBlockOrigin = { showUBlockOriginSettings() },
            openAutofillSettings = { openAutofillServiceSettings() }
        ).show()
    }


    private fun restoreInitialBrowserTabs(startupIntent: Intent?) {
        if (initialBrowserTabsRestored || isFinishing || isDestroyed) return
        initialBrowserTabsRestored = true
        lifecycleScope.launch {
            val snapshot = runCatching { storageRuntime.restoreOpenTabs() }
                .onFailure { error ->
                    android.util.Log.e("BrowseX", "Room tab restore failed; using legacy snapshot", error)
                }
                .getOrElse { store.restoreOpenTabs().let { legacy ->
                    com.browsex.app.storage.TabSessionSnapshot(legacy.urls, legacy.activeIndex, 0L)
                } }
            if (isFinishing || isDestroyed) return@launch
            val externalUrl = startupIntent?.data?.let(::safeExternalUrl)
            tabController.restoreInitialTabs(snapshot.urls, snapshot.activeIndex, externalUrl)
            handleSiteNotificationIntent(startupIntent)
            syncUBlockButton()
        }
    }

    private fun openAutofillServiceSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast(tr("Password autofill requires Android 8 or newer"))
            return
        }
        val request = Intent(
            Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(request) }
            .onFailure {
                android.util.Log.w("BrowseX", "Could not open Android Autofill settings", it)
                toast(tr("Could not open Android Autofill settings"))
            }
    }

    private fun showUBlockOriginSettings() = uBlockSettings.show()

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
            clearData = { flags, clearHistory -> clearBrowsingData(flags, clearHistory) }
        ).show()
    }

    private fun clearBrowsingData(flags: Long, clearHistory: Boolean) =
        browsingDataCoordinator.clear(flags, clearHistory, host = null, mediaOrigin = null)

    private fun clearBrowsingDataForHost(host: String, mediaOrigin: String) {
        val canonicalHost = if (host.contains(':')) host else
            runCatching { java.net.IDN.toASCII(host, java.net.IDN.USE_STD3_ASCII_RULES) }.getOrDefault(host)
        browsingDataCoordinator.clear(
            org.mozilla.geckoview.StorageController.ClearFlags.SITE_DATA,
            clearHistory = false,
            host = canonicalHost,
            mediaOrigin = mediaOrigin
        )
    }

    private fun setBrowsingDataBusy(busy: Boolean) {
        if (busy) {
            dataClearDialog = AlertDialog.Builder(this)
                .setTitle(tr("Privacy center"))
                .setMessage(tr("Clearing selected browsing data…"))
                .setView(ProgressBar(this))
                .setCancelable(false)
                .create()
            dataClearDialog?.show()
        } else {
            dataClearDialog?.dismiss()
            dataClearDialog = null
        }
    }

    private fun showRecall() {
        RecallPage(
            activity = this,
            store = store,
            openTabs = tabs.filterNot { it.privateMode }.map { it.title to it.url },
            onOpen = { url ->
                val existing = tabs.firstOrNull { !it.privateMode && it.url == url }
                if (existing != null) activateTab(existing) else createTab(url)
            }
        ).show()
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
        DownloadsPage(this, store) { downloadId ->
            streamDownloadCoordinator.cancel(downloadId)
        }.show()
    }

    /** Routes GeckoView responses into the dedicated download request coordinator. */
    private fun enqueueDownload(response: WebResponse) {
        downloadRequestCoordinator.enqueue(response)
    }

    /** Starts a response stream after validation and any required Android permission prompt. */
    private fun startStreamDownload(response: WebResponse) {
        val body = response.body ?: run {
            toast("This download response is not supported")
            return
        }
        val started = streamDownloadCoordinator.start(
            response = response,
            onCompleted = { result ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) toast("Downloaded: ${result.displayName}")
                }
                android.util.Log.i("BrowseX", "Response stream saved: ${result.displayName}, bytes=${result.bytesWritten}")
            },
            onFailed = { error, cancelled ->
                if (!cancelled) android.util.Log.e("BrowseX", "Response stream download failed", error)
                runOnUiThread {
                    if (!cancelled && !isFinishing && !isDestroyed) {
                        toast("Could not save download: ${error?.message ?: "unknown error"}")
                    }
                }
            }
        )
        if (!started) {
            body.close()
            toast("This download response is not supported")
        }
    }


    private fun toast(message: String) {
        android.widget.Toast.makeText(this, tr(message), android.widget.Toast.LENGTH_SHORT).show()
    }

}
