package com.browsex.app

import android.Manifest
import android.os.PowerManager
import android.app.DownloadManager
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.CookieManager
import android.os.Environment

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.setContent
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.metrics.performance.JankStats
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException
import java.util.ArrayDeque
import java.util.WeakHashMap
import com.browsex.app.storage.BrowserStorageRuntime
import com.browsex.app.storage.BrowserSettings
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
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
            attachSessionToView = { session ->
                activePageScrollY = pageScrollOffsets[session] ?: 0
                lastFocusedNavigationControl = null
                browserView.setSession(session)
            },
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
    private val isTelevisionDevice by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
                android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
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
    private var browserSettingsSnapshot = BrowserSettings()
    internal val currentBrowserSettings: BrowserSettings get() = browserSettingsSnapshot
    internal val currentAdaptivePresentation: AdaptivePresentationConfig? get() = adaptivePresentationConfig
    private var browserSettingsLoaded = false
    internal val storageRuntime: BrowserStorageRuntime
        get() = (application as BrowseXApplication).storageRuntime
    internal fun updateBrowserSettings(
        transform: (BrowserSettings) -> BrowserSettings,
        onUpdated: (BrowserSettings) -> Unit = {}
    ) {
        lifecycleScope.launch {
            runCatching {
                storageRuntime.settings.update(transform)
                storageRuntime.settings.snapshot()
            }.onSuccess { updated ->
                mirrorBrowserSettings(updated)
                onUpdated(updated)
            }.onFailure { error ->
                android.util.Log.e("BrowseX", "Could not update browser settings", error)
                toast(BrowseXStrings.text(this@MainActivity, "Could not save settings", "تعذر حفظ الإعدادات"))
            }
        }
    }

    private suspend fun clearStoredHistory() {
        val result = runCatching {
            val storage = storageRuntime.awaitReady()
            storage.history.clear()
            storage.sessions.clearRecentlyClosed()
        }
        result.getOrThrow()
    }

    private fun mirrorBrowserSettings(settings: BrowserSettings) {
        val previous = browserSettingsSnapshot
        val wasLoaded = browserSettingsLoaded
        val homeContentChanged = wasLoaded && (
            previous.themeMode != settings.themeMode ||
                previous.languageMode != settings.languageMode ||
                previous.homeShowQuickLinks != settings.homeShowQuickLinks ||
                previous.homeShowDiscovery != settings.homeShowDiscovery ||
                previous.homeLayout != settings.homeLayout
            )
        val adaptiveAppearanceChanged = wasLoaded && (
            previous.reduceTransparency != settings.reduceTransparency ||
                previous.highContrast != settings.highContrast ||
                previous.reduceMotion != settings.reduceMotion
            )
        browserSettingsSnapshot = settings
        browserSettingsLoaded = true
        AppCompatDelegate.setDefaultNightMode(when (settings.themeMode) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(if (settings.languageMode == "system") "" else settings.languageMode)
        )
        BrowserContentProtectionPolicy.configure(settings.adBlockEnabled, settings.adBlockPrivateEnabled)
        // Keep Gecko's engine-level theme and language in sync when DataStore loads
        // after the runtime has already been created, not only during Activity startup.
        browserRuntime?.settings?.setPreferredColorScheme(when (settings.themeMode) {
            "dark" -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
            "light" -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            else -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
        })
        val engineLocales = if (settings.languageMode == "system") {
            resources.configuration.locales.toLanguageTags().split(',').filter { it.isNotBlank() }.toTypedArray()
        } else {
            arrayOf(settings.languageMode)
        }
        browserRuntime?.settings?.setLocales(engineLocales)
        if (::browserRoot.isInitialized) refreshCurrentAdaptivePresentation(force = true)
        // Settings that affect policy are refreshed by AdaptivePresentationPolicy above.
        // Content-only home preferences (theme, language, shortcut sections/layout) use
        // the same safe URL refresh without duplicating an accessibility/performance reload.
        if (homeContentChanged && !adaptiveAppearanceChanged) {
            reloadActiveHomeForPresentation()
        } else if (homeContentChanged && !::browserRoot.isInitialized) {
            reloadActiveHomeForPresentation()
        } else if (adaptiveAppearanceChanged && !::browserRoot.isInitialized) {
            reloadActiveHomeForPresentation()
        }
        renderComposeChrome()
    }

    internal fun websiteNotificationsEnabled(): Boolean = browserSettingsSnapshot.siteNotificationsEnabled

    internal fun wasPlatformPermissionDenied(permission: String): Boolean =
        permission in browserSettingsSnapshot.deniedPlatformPermissions

    internal fun markPlatformPermissionDenied(permission: String) {
        if (browserSettingsLoaded) {
            browserSettingsSnapshot = browserSettingsSnapshot.copy(
                deniedPlatformPermissions = browserSettingsSnapshot.deniedPlatformPermissions + permission
            )
        }
        updateBrowserSettings(transform = {
            it.copy(deniedPlatformPermissions = it.deniedPlatformPermissions + permission)
        })
    }

    internal fun clearPlatformPermissionDenial(permission: String) {
        if (browserSettingsLoaded) {
            browserSettingsSnapshot = browserSettingsSnapshot.copy(
                deniedPlatformPermissions = browserSettingsSnapshot.deniedPlatformPermissions - permission
            )
        }
        updateBrowserSettings(transform = {
            it.copy(deniedPlatformPermissions = it.deniedPlatformPermissions - permission)
        })
    }

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
    private lateinit var browserRoot: FrameLayout
    private lateinit var composeChromeView: ComposeView
    private val composeChromeState = mutableStateOf(BrowseXChromeState())
    private val composeMenuOpen = mutableStateOf(false)
    private var adaptivePresentationConfig: AdaptivePresentationConfig? = null
    private var lastPresentationWidthPx = 0
    private var lastPresentationHeightPx = 0
    private var initialTvFocusRequested = false
    private var imeVisible = false
    private var foldPosture = WrenFoldPosture.NONE
    private var windowLayoutJob: Job? = null
    @Volatile private var appMemoryTrimPressure = false
    private var activePageScrollY = 0
    private var lastFocusedNavigationControl: View? = null
    private val pageScrollOffsets = WeakHashMap<GeckoSession, Int>()
    private var jankStats: JankStats? = null
    private val recentJankFrames = ArrayDeque<Boolean>(60)
    private var recentJankFrameCount = 0
    @Volatile private var measuredJankPressure = false
    private var powerSaveReceiverRegistered = false
    private val powerSaveModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            refreshCurrentAdaptivePresentation(force = true)
        }
    }
    private var thermalStatusListenerRegistered = false
    private val thermalStatusListener = PowerManager.OnThermalStatusChangedListener {
        refreshCurrentAdaptivePresentation(force = true)
    }
    private lateinit var address: EditText
    private var addressSuggestionsController: AddressSuggestionsController? = null
    private lateinit var progress: ProgressBar
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var navigationBar: LinearLayout
    private lateinit var addressRow: LinearLayout
    private lateinit var pageIdentity: TextView
    private lateinit var siteInfoButton: TextView
    private lateinit var reloadButton: TextView
    private lateinit var tabsButton: TextView
    private lateinit var uBlockButton: TextView
    private var fullscreen = false
    private var shuttingDown = false
    private var initialBrowserTabsRestoreStarted = false
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
            clearHistory = { onComplete ->
                lifecycleScope.launch {
                    val success = runCatching { clearStoredHistory() }
                        .onFailure { error ->
                            android.util.Log.e("BrowseX", "Could not clear browsing history", error)
                        }.isSuccess
                    onComplete(success)
                }
            },
            clearSiteMediaDecisions = { origin, onComplete ->
                lifecycleScope.launch {
                    val success = runCatching {
                        val permissions = storageRuntime.awaitReady().sitePermissions
                        if (origin == null) permissions.clearAll() else permissions.clearOrigin(origin)
                    }.onFailure { error ->
                        android.util.Log.e("BrowseX", "Could not clear site permission decisions", error)
                    }.isSuccess
                    onComplete(success)
                }
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
        // Keep system-bar handling consistent on Android 8–16; root insets below protect controls.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launch {
            runCatching { storageRuntime.settings.snapshot() }
                .onSuccess(::mirrorBrowserSettings)
                .onFailure { error -> android.util.Log.e("BrowseX", "Could not load browser settings", error) }
        }
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
        streamDownloadCoordinator = StreamDownloadCoordinator(storageRuntime, DownloadResponseWriter(applicationContext))
        bookmarkExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
            if (uri != null) lifecycleScope.launch {
                runCatching {
                    val payload = storageRuntime.awaitReady().bookmarks.exportBookmarksHtml()
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
                                    .onSuccess { result -> toast(getString(R.string.ui_imported_bookmarks, result.importedBookmarks)) }
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
                                            .onSuccess { result -> toast(getString(R.string.ui_imported_bookmarks, result.importedBookmarks)) }
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
            wasDenied = ::wasPlatformPermissionDenied,
            clearDenial = ::clearPlatformPermissionDenial,
            markDenied = ::markPlatformPermissionDenied,
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
            featureEnabled = ::websiteNotificationsEnabled,
            permissionGranted = {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            },
            notificationsEnabled = { NotificationManagerCompat.from(this).areNotificationsEnabled() },
            previouslyDenied = { wasPlatformPermissionDenied(Manifest.permission.POST_NOTIFICATIONS) },
            clearDenial = { clearPlatformPermissionDenial(Manifest.permission.POST_NOTIFICATIONS) },
            markDenied = { markPlatformPermissionDenied(Manifest.permission.POST_NOTIFICATIONS) },
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
        browserRuntime?.settings?.setPreferredColorScheme(when (browserSettingsSnapshot.themeMode) {
            "dark" -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
            "light" -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            else -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
        })
        val requestedLocales = if (browserSettingsSnapshot.languageMode == "system") {
            resources.configuration.locales.toLanguageTags().split(',').filter { it.isNotBlank() }.toTypedArray()
        } else {
            arrayOf(browserSettingsSnapshot.languageMode)
        }
        browserRuntime?.settings?.setLocales(requestedLocales)
        notificationBridge = BrowseXNotificationBridge(this, ::requestSiteNotificationPermission)
        browserRuntime?.setWebNotificationDelegate(notificationBridge)
        // Attach the browser engine inside the native glass chrome. The GeckoView remains
        // the flexible content surface; browser controls stay outside the web rendering layer.
        browserView = GeckoView(this)
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

        val extensionRuntime = browserRuntime
        if (extensionRuntime == null) {
            BrowserContentProtectionPolicy.configure(browserSettingsSnapshot.adBlockEnabled, browserSettingsSnapshot.adBlockPrivateEnabled)
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
                },
                onExtensionStateChanged = { updated ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        val enabled = updated?.metaData?.enabled == true
                        val privateAllowed = enabled && updated?.metaData?.allowedInPrivateBrowsing == true
                        BrowserContentProtectionPolicy.configure(enabled, privateAllowed)
                        updateBrowserSettings({
                            it.copy(
                                adBlockInstalled = updated != null,
                                adBlockEnabled = enabled,
                                adBlockPrivateEnabled = privateAllowed
                            )
                        })
                    }
                }
            ).also { manager ->
                manager.refresh(
                    onComplete = { installed ->
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            val enabled = installed?.metaData?.enabled == true
                            val privateAllowed = enabled && installed?.metaData?.allowedInPrivateBrowsing == true
                            BrowserContentProtectionPolicy.configure(enabled, privateAllowed)
                            // Reconcile persisted preferences with Gecko's actual extension state.
                            updateBrowserSettings({
                                it.copy(
                                    adBlockInstalled = installed != null,
                                    adBlockEnabled = enabled,
                                    adBlockPrivateEnabled = privateAllowed
                                )
                            })
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (
                BrowserKeyboardShortcutPolicy.resolve(
                    keyCode = event.keyCode,
                    controlPressed = event.isCtrlPressed,
                    metaPressed = event.isMetaPressed,
                    shiftPressed = event.isShiftPressed,
                    altPressed = event.isAltPressed,
                    repeatCount = event.repeatCount,
                    imeVisible = imeVisible
                )
            ) {
                BrowserKeyboardAction.FOCUS_ADDRESS -> {
                    address.requestFocus()
                    address.selectAll()
                    return true
                }
                BrowserKeyboardAction.NEW_TAB -> {
                    createTab(homePageUrl())
                    return true
                }
                BrowserKeyboardAction.NEW_PRIVATE_TAB -> {
                    createTab(homePageUrl(), privateMode = true)
                    return true
                }
                BrowserKeyboardAction.CLOSE_TAB -> {
                    val current = activeTab ?: return super.dispatchKeyEvent(event)
                    closeTab(current)
                    return true
                }
                BrowserKeyboardAction.RELOAD -> {
                    val current = activeTab ?: return super.dispatchKeyEvent(event)
                    current.session.reload()
                    return true
                }
                BrowserKeyboardAction.FIND_IN_PAGE -> {
                    pageActions.findInPage()
                    return true
                }
                BrowserKeyboardAction.NEXT_TAB -> {
                    cycleTabs(1)
                    return true
                }
                BrowserKeyboardAction.PREVIOUS_TAB -> {
                    cycleTabs(-1)
                    return true
                }
                BrowserKeyboardAction.TOGGLE_BOOKMARK -> {
                    val current = activeTab ?: return super.dispatchKeyEvent(event)
                    if (current.url.startsWith("http://") || current.url.startsWith("https://")) {
                        toggleBookmarkInRoom(current.url, current.title)
                    }
                    return true
                }
                BrowserKeyboardAction.OPEN_DOWNLOADS -> {
                    openDownloads()
                    return true
                }
                BrowserKeyboardAction.OPEN_HISTORY -> {
                    showSavedPages("History", emptyList())
                    return true
                }
                BrowserKeyboardAction.OPEN_BOOKMARKS -> {
                    showSavedPages("Bookmarks", emptyList())
                    return true
                }
                BrowserKeyboardAction.GO_BACK -> {
                    val current = activeTab
                    if (current?.canGoBack == true) {
                        current.session.goBack()
                        return true
                    }
                }
                BrowserKeyboardAction.GO_FORWARD -> {
                    val current = activeTab
                    if (current?.canGoForward == true) {
                        current.session.goForward()
                        return true
                    }
                }
                null -> Unit
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun cycleTabs(direction: Int) {
        val currentTabs = tabs
        if (currentTabs.size < 2) return
        val currentIndex = currentTabs.indexOf(activeTab).takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + direction + currentTabs.size) % currentTabs.size
        activateTab(currentTabs[nextIndex])
    }

    override fun onResume() {
        super.onResume()
        ensureJankStats()
        jankStats?.isTrackingEnabled = true
        // Re-check the global memory state after returning to foreground; a prior
        // app-specific trim hint should not force minimal chrome forever.
        val memoryInfo = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memoryInfo)
        if (!memoryInfo.lowMemory) appMemoryTrimPressure = false
        refreshCurrentAdaptivePresentation(force = true)
    }

    override fun onPause() {
        jankStats?.isTrackingEnabled = false
        super.onPause()
    }

    private fun ensureJankStats() {
        if (jankStats != null) return
        runCatching {
            jankStats = JankStats.createAndTrack(
                window,
                JankStats.OnFrameListener { frameData -> onFramePerformanceSample(frameData.isJank) }
            )
        }.onFailure { error ->
            android.util.Log.w("Wren", "Frame performance monitoring is unavailable", error)
        }
    }

    /** Keep only a rolling local window; never persist or transmit frame or page data. */
    private fun onFramePerformanceSample(isJank: Boolean) {
        var pressureChanged: Boolean? = null
        synchronized(recentJankFrames) {
            if (recentJankFrames.size == 60) {
                if (recentJankFrames.removeFirst()) recentJankFrameCount--
            }
            recentJankFrames.addLast(isJank)
            if (isJank) recentJankFrameCount++

            if (recentJankFrames.size == 60) {
                val jankRatio = recentJankFrameCount.toFloat() / recentJankFrames.size
                pressureChanged = when {
                    !measuredJankPressure && jankRatio >= 0.20f -> true
                    measuredJankPressure && jankRatio <= 0.10f -> false
                    else -> null
                }
            }
        }
        val nextPressure = pressureChanged ?: return
        if (nextPressure == measuredJankPressure) return
        measuredJankPressure = nextPressure
        runOnUiThread {
            if (!isFinishing && !isDestroyed && ::browserRoot.isInitialized) {
                refreshAdaptivePresentation(
                    browserRoot.width,
                    browserRoot.height,
                    browserRoot.isHardwareAccelerated,
                    force = true
                )
            }
        }
    }

    private fun observeWindowFolding() {
        if (windowLayoutJob?.isActive == true) return
        windowLayoutJob = lifecycleScope.launch {
            try {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { layoutInfo ->
                        val feature = layoutInfo.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .firstOrNull { it.isSeparating || it.state == FoldingFeature.State.HALF_OPENED }
                        val nextPosture = feature?.let {
                            WrenFoldPosturePolicy.resolve(
                                isHalfOpened = it.state == FoldingFeature.State.HALF_OPENED,
                                orientation = if (it.orientation == FoldingFeature.Orientation.VERTICAL) {
                                    WrenFoldOrientation.VERTICAL
                                } else {
                                    WrenFoldOrientation.HORIZONTAL
                                },
                                isSeparating = it.isSeparating
                            )
                        } ?: WrenFoldPosture.NONE
                        if (nextPosture != foldPosture) {
                            foldPosture = nextPosture
                            refreshCurrentAdaptivePresentation(force = true)
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("Wren", "Fold posture observation is unavailable", error)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        observeWindowFolding()
        if (!powerSaveReceiverRegistered) {
            runCatching {
                ContextCompat.registerReceiver(
                    this,
                    powerSaveModeReceiver,
                    IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                powerSaveReceiverRegistered = true
            }.onFailure {
                android.util.Log.w("Wren", "Could not observe power-saver changes", it)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !thermalStatusListenerRegistered) {
            runCatching {
                getSystemService(PowerManager::class.java)
                    .addThermalStatusListener(mainExecutor, thermalStatusListener)
                thermalStatusListenerRegistered = true
            }.onFailure {
                android.util.Log.w("Wren", "Could not observe thermal-state changes", it)
            }
        }
        hostLifecycleCoordinator.onStart()
    }

    override fun onStop() {
        windowLayoutJob?.cancel()
        windowLayoutJob = null
        // Extension action popups own a separate GeckoSession; release it as soon as the host stops.
        uBlockOriginManager?.closePopup()
        if (::credentialVault.isInitialized) credentialVault.lock()
        hostLifecycleCoordinator.onStop()
        if (powerSaveReceiverRegistered) {
            runCatching { unregisterReceiver(powerSaveModeReceiver) }
            powerSaveReceiverRegistered = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatusListenerRegistered) {
            runCatching {
                getSystemService(PowerManager::class.java)
                    .removeThermalStatusListener(thermalStatusListener)
            }
            thermalStatusListenerRegistered = false
        }
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        hostLifecycleCoordinator.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW &&
            level <= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            // Android 10–13 can signal pressure before MemoryInfo.lowMemory flips.
            // Android 14+ no longer sends these deprecated running-level callbacks.
            appMemoryTrimPressure = true
            refreshCurrentAdaptivePresentation(force = true)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        hostLifecycleCoordinator.onLowMemory()
        appMemoryTrimPressure = true
        refreshCurrentAdaptivePresentation(force = true)
    }

    /** GeckoSession resource limits and background release are owned by the lifecycle coordinator. */
    private fun enforceLiveSessionBudget() {
        sessionLifecycleCoordinator.enforceLiveSessionBudget()
    }

    private fun persistTabs() {
        if (!initialBrowserTabsRestored) return
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
        addressSuggestionsController?.destroy()
        addressSuggestionsController = null
        shuttingDown = true
        if (::platformPermissionCoordinator.isInitialized) platformPermissionCoordinator.rejectAll()
        if (::notificationPermissionCoordinator.isInitialized) notificationPermissionCoordinator.rejectAll()
        if (::credentialVault.isInitialized) {
            // Lock immediately, but keep the process-wide private marker set until every
            // private GeckoSession has been closed and removed from the tab registry.
            credentialVault.lock()
        }
        if (::downloadRequestCoordinator.isInitialized) downloadRequestCoordinator.cancelPending()
        if (::streamDownloadCoordinator.isInitialized) streamDownloadCoordinator.shutdown()
        uBlockOriginManager?.destroy()
        uBlockOriginManager = null
        notificationBridge?.destroy()
        notificationBridge = null
        dataClearDialog?.dismiss()
        dataClearDialog = null
        browserRuntime?.setWebNotificationDelegate(null)
        if (::browserView.isInitialized) browserView.releaseSession()
        sessionLifecycleCoordinator.closeAllSessions()
        tabController.clear()
        // External Autofill runs in a separate Activity and must remain blocked throughout teardown.
        if (::credentialVault.isInitialized) credentialVault.setPrivateTabsOpen(false)
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

    private val colors get() = BrowseXColors.resolve(this, browserSettingsSnapshot)

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
            currentSearchEngine = { browserSettingsSnapshot.searchEngine },
            updateSearchEngine = { engine ->
                updateBrowserSettings({ it.copy(searchEngine = engine) }) { updated ->
                    toast(getString(R.string.ui_search_engine_changed, updated.searchEngine))
                }
            },
            showMessage = ::toast,
            mutedColor = { colors.muted },
            dp = ::dp
        )
    }


    private fun tr(value: String): String = BrowseXStrings.text(this@MainActivity, value)

    private fun homePageUrl(): String {
        val presentation = adaptivePresentationConfig
        val performanceMinimal = presentation?.materialMode == WrenMaterialMode.PERFORMANCE_MINIMAL ||
            presentation?.reasonCodes?.any {
                it == "constrained-memory" || it == "battery-saver" || it == "thermal-pressure"
            } == true
        val opaque = browserSettingsSnapshot.reduceTransparency ||
            browserSettingsSnapshot.highContrast ||
            performanceMinimal ||
            presentation?.materialMode == WrenMaterialMode.MATTE
        val highContrast = browserSettingsSnapshot.highContrast ||
            presentation?.materialMode == WrenMaterialMode.HIGH_CONTRAST
        val reduceMotion = browserSettingsSnapshot.reduceMotion || presentation?.reduceMotion == true

        return "$HOME_URL?bxlang=${browserSettingsSnapshot.languageMode}&bxtheme=${browserSettingsSnapshot.themeMode}" +
            "&bxquick=${browserSettingsSnapshot.homeShowQuickLinks}&bxdiscovery=${browserSettingsSnapshot.homeShowDiscovery}&bxlayout=${browserSettingsSnapshot.homeLayout}" +
            "&bxengine=${java.net.URLEncoder.encode(browserSettingsSnapshot.searchEngine, "UTF-8")}" +
            "&bxtv=$isTelevisionDevice&bxfold=${foldPosture.name.lowercase()}&bxopaque=$opaque&bxcontrast=$highContrast" +
            "&bxmotion=$reduceMotion&bxminimal=$performanceMinimal&bxaccent=${browserSettingsSnapshot.accentColor}"
    }

    private fun reloadActiveHomeForPresentation() {
        val tab = activeTab ?: return
        if (!tab.sessionOpened || !tab.url.startsWith(HOME_URL)) return
        val targetUrl = homePageUrl()
        if (tab.url == targetUrl) return
        runCatching { tab.session.loadUri(targetUrl) }.onFailure {
            android.util.Log.w("Wren", "Could not refresh home-page performance presentation", it)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun refreshCurrentAdaptivePresentation(force: Boolean) {
        if (!::browserRoot.isInitialized) return
        refreshAdaptivePresentation(
            browserRoot.width,
            browserRoot.height,
            browserRoot.isHardwareAccelerated,
            force = force
        )
    }

    private fun refreshAdaptivePresentation(
        widthPx: Int,
        heightPx: Int,
        hardwareAccelerated: Boolean,
        force: Boolean = false
    ) {
        if (widthPx <= 0 || heightPx <= 0 || isFinishing || isDestroyed) return
        if (!force && widthPx == lastPresentationWidthPx && heightPx == lastPresentationHeightPx) return
        lastPresentationWidthPx = widthPx
        lastPresentationHeightPx = heightPx

        val previousPresentation = adaptivePresentationConfig
        val probe = AdaptivePresentationProbe(this)
        val pressure = DeviceCapabilityProbe(this).runtimePressure()
        val next = AdaptivePresentationPolicy.resolve(
            probe.snapshot(
                windowWidthPx = widthPx,
                windowHeightPx = heightPx,
                density = resources.displayMetrics.density,
                isHardwareAccelerated = hardwareAccelerated,
                deviceProfile = deviceCapabilityProfile,
                pressure = pressure,
                measuredJankPressure = measuredJankPressure,
                imeVisible = imeVisible,
                addressBarPreference = when (
                    BrowserPreferencePolicy.normalizeAddressBarPosition(browserSettingsSnapshot.addressBarPosition)
                ) {
                    "top" -> AddressBarPreference.TOP
                    "bottom" -> AddressBarPreference.BOTTOM
                    else -> AddressBarPreference.AUTO
                },
                reduceTransparency = browserSettingsSnapshot.reduceTransparency,
                reduceMotionPreference = browserSettingsSnapshot.reduceMotion,
                highContrastPreference = browserSettingsSnapshot.highContrast,
                appMemoryTrimPressure = appMemoryTrimPressure,
                foldPosture = foldPosture
            )
        )
        adaptivePresentationConfig = next
        applyAdaptivePresentation(next, widthPx)
        val homeEffectsChanged = previousPresentation == null ||
            previousPresentation.materialMode != next.materialMode ||
            previousPresentation.reduceMotion != next.reduceMotion ||
            previousPresentation.allowSpeculativeVisualWork != next.allowSpeculativeVisualWork
        if (homeEffectsChanged) reloadActiveHomeForPresentation()
    }

    /** Apply the resolved presentation without changing browser-engine or security behavior. */
    private fun applyAdaptivePresentation(config: AdaptivePresentationConfig, windowWidthPx: Int) {
        if (!::bottomBar.isInitialized || !::topBar.isInitialized ||
            !::addressRow.isInitialized || !::navigationBar.isInitialized
        ) return

        val opaque = config.materialMode == WrenMaterialMode.MATTE ||
            config.materialMode == WrenMaterialMode.HIGH_CONTRAST ||
            config.materialMode == WrenMaterialMode.PERFORMANCE_MINIMAL
        // BrowseXColors.card has a slight alpha for glass layering; Matte and performance
        // modes must be truly opaque rather than merely less translucent.
        val opaqueSurface = if (colors.isDark) Color.rgb(30, 45, 70) else Color.WHITE
        val opaqueField = if (colors.isDark) Color.rgb(13, 25, 41) else Color.rgb(244, 247, 252)
        val fill = if (opaque) opaqueSurface else colors.panel
        val fieldFill = if (opaque) opaqueField else colors.field
        val stroke = if (config.materialMode == WrenMaterialMode.HIGH_CONTRAST) colors.ink else colors.line

        val borderWidth = if (config.materialMode == WrenMaterialMode.HIGH_CONTRAST) 2 else 1
        topBar.background = glassSurface(20, fill, stroke, borderWidth)
        bottomBar.background = glassSurface(28, fill, stroke, borderWidth)
        addressRow.background = glassSurface(22, fieldFill, stroke, borderWidth)
        bottomBar.elevation = when (config.materialMode) {
            WrenMaterialMode.GLASS_BLUR, WrenMaterialMode.GLASS_TINTED -> dp(8).toFloat()
            WrenMaterialMode.MATTE -> dp(3).toFloat()
            WrenMaterialMode.HIGH_CONTRAST, WrenMaterialMode.PERFORMANCE_MINIMAL -> 0f
        }

        navigationBar.visibility = if (config.showNavigationActions) View.VISIBLE else View.GONE
        val rtl = BrowseXColors.isRtl(browserSettingsSnapshot)
        val sideRail = config.addressPlacement == WrenAddressPlacement.SIDE
        val tvRemote = config.addressPlacement == WrenAddressPlacement.TV_REMOTE
        val topDock = config.addressPlacement == WrenAddressPlacement.TOP || tvRemote

        // TV is a 10-foot remote experience: enlarge the brand, address field, targets and focus.
        pageIdentity.textSize = if (tvRemote) 24f else 17f
        address.textSize = if (tvRemote) 23f else 15f
        siteInfoButton.textSize = if (tvRemote) 28f else 21f
        reloadButton.textSize = if (tvRemote) 29f else 22f
        siteInfoButton.layoutParams = LinearLayout.LayoutParams(
            dp(if (tvRemote) 60 else 48), dp(if (tvRemote) 54 else 48)
        )
        reloadButton.layoutParams = LinearLayout.LayoutParams(
            dp(if (tvRemote) 60 else 48), dp(if (tvRemote) 54 else 48)
        )
        val brand = topBar.getChildAt(0) as? ImageView
        brand?.layoutParams = LinearLayout.LayoutParams(
            dp(if (tvRemote) 54 else 34),
            dp(if (tvRemote) 54 else 34)
        ).apply { marginEnd = dp(if (tvRemote) 14 else 8) }
        val topParams = topBar.layoutParams as? FrameLayout.LayoutParams
        topParams?.let {
            it.height = dp(if (tvRemote) 70 else 46)
            it.topMargin = dp(if (tvRemote) 28 else 5)
            it.leftMargin = dp(if (tvRemote) 48 else 13)
            it.rightMargin = dp(if (tvRemote) 48 else 13)
            topBar.layoutParams = it
        }
        val progressParams = progress.layoutParams as? FrameLayout.LayoutParams
        progressParams?.let {
            it.topMargin = dp(if (tvRemote) 90 else 51)
            progress.layoutParams = it
        }

        // Re-parenting the existing rows preserves all click listeners and browser actions.
        bottomBar.removeAllViews()
        if (sideRail) {
            bottomBar.orientation = LinearLayout.HORIZONTAL
            bottomBar.gravity = Gravity.CENTER_VERTICAL
            bottomBar.setPadding(dp(7), dp(8), dp(7), dp(8))
            navigationBar.orientation = LinearLayout.VERTICAL
            navigationBar.gravity = Gravity.CENTER
            navigationBar.setPadding(0, 0, 0, 0)
            val railButtonSize = dp(48)
            val railButtonWidth = dp(52)
            for (index in 0 until navigationBar.childCount) {
                val child = navigationBar.getChildAt(index)
                child.layoutParams = LinearLayout.LayoutParams(railButtonWidth, railButtonSize).apply {
                    bottomMargin = dp(2)
                }
            }
            bottomBar.addView(addressRow, LinearLayout.LayoutParams(dp(250), dp(48)))
            bottomBar.addView(navigationBar, LinearLayout.LayoutParams(railButtonWidth, -2).apply {
                marginStart = dp(4)
                marginEnd = dp(2)
            })
        } else {
            bottomBar.orientation = LinearLayout.VERTICAL
            bottomBar.gravity = Gravity.CENTER
            bottomBar.setPadding(
                dp(if (tvRemote) 14 else 8),
                dp(if (tvRemote) 12 else 8),
                dp(if (tvRemote) 14 else 8),
                dp(if (tvRemote) 10 else 7)
            )
            navigationBar.orientation = LinearLayout.HORIZONTAL
            navigationBar.gravity = Gravity.CENTER
            navigationBar.setPadding(dp(1), dp(if (tvRemote) 8 else 5), dp(1), 0)
            val buttonHeight = dp(if (tvRemote) 60 else 48)
            for (index in 0 until navigationBar.childCount) {
                val child = navigationBar.getChildAt(index)
                child.minimumHeight = buttonHeight
                child.layoutParams = LinearLayout.LayoutParams(0, buttonHeight, 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                }
            }
            bottomBar.addView(addressRow, LinearLayout.LayoutParams(-1, dp(if (tvRemote) 54 else 48)))
            bottomBar.addView(navigationBar, LinearLayout.LayoutParams(-1, buttonHeight + dp(3)).apply {
                topMargin = dp(3)
            })
        }

        val params = bottomBar.layoutParams as? FrameLayout.LayoutParams ?: return
        when {
            sideRail -> {
                val foldSideRail = foldPosture == WrenFoldPosture.BOOK ||
                    foldPosture == WrenFoldPosture.SEPARATING_VERTICAL
                params.width = dp(
                    when {
                        foldSideRail && config.showNavigationActions -> 280
                        foldSideRail -> 236
                        config.showNavigationActions -> 324
                        else -> 280
                    }
                )
                params.height = -2
                params.gravity = (if (rtl) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
                params.topMargin = dp(12)
                params.bottomMargin = dp(12)
                params.leftMargin = dp(12)
                params.rightMargin = dp(12)
            }
            tvRemote -> {
                params.width = minOf(dp(1200), (windowWidthPx - dp(96)).coerceAtLeast(dp(640)))
                params.height = -2
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.topMargin = dp(100)
                params.bottomMargin = 0
                params.leftMargin = dp(24)
                params.rightMargin = dp(24)
            }
            topDock -> {
                params.width = if (windowWidthPx / resources.displayMetrics.density < 600f) {
                    -1
                } else {
                    minOf(dp(720), (windowWidthPx - dp(32)).coerceAtLeast(dp(280)))
                }
                params.height = if (config.showNavigationActions) dp(118) else -2
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.topMargin = dp(58)
                params.bottomMargin = 0
                params.leftMargin = dp(10)
                params.rightMargin = dp(10)
            }
            else -> {
                params.width = if (windowWidthPx / resources.displayMetrics.density < 600f) {
                    -1
                } else {
                    minOf(dp(720), (windowWidthPx - dp(32)).coerceAtLeast(dp(280)))
                }
                params.height = if (config.showNavigationActions) dp(118) else -2
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                params.topMargin = 0
                params.bottomMargin = dp(7)
                params.leftMargin = dp(10)
                params.rightMargin = dp(10)
            }
        }
        bottomBar.layoutParams = params
        BrowseXColors.applyDirection(bottomBar, browserSettingsSnapshot)
        BrowseXColors.applyDirection(navigationBar, browserSettingsSnapshot)
        // Request an initial search focus only once. Performance/power refreshes must
        // never steal focus after the viewer has moved to page content or TV controls.
        if (tvRemote && !initialTvFocusRequested) {
            initialTvFocusRequested = true
            address.post {
                if (!isFinishing && !isDestroyed && !address.hasFocus()) address.requestFocus()
            }
        }
    }

    private fun createBrowserUi(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(colors.canvas)
            clipChildren = false
            clipToPadding = false
            BrowseXColors.applyDirection(this, browserSettingsSnapshot)
        }
        browserRoot = root
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeBars = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val bars = insets.getInsets(safeBars)
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !colors.isDark
                isAppearanceLightNavigationBars = !colors.isDark
            }
            val nextImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeVisible != nextImeVisible) {
                imeVisible = nextImeVisible
                if (adaptivePresentationConfig != null) {
                    refreshCurrentAdaptivePresentation(force = true)
                }
            }
            insets
        }

        // Quiet page identity at the top; all everyday browser controls stay within thumb reach.
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(2), dp(5), dp(2))
            background = glassSurface(20, colors.panel, colors.line)
            isClickable = true
        }
        val brandMark = ImageView(this).apply {
            setImageResource(R.drawable.ic_browsex)
            contentDescription = tr("Wren browser")
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        topBar.addView(brandMark, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            marginEnd = dp(8)
        })
        pageIdentity = TextView(this).apply {
            text = "Wren"
            textSize = 17f
            setTextColor(colors.ink)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(pageIdentity, LinearLayout.LayoutParams(0, -1, 1f))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(colors.accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(colors.line)
            visibility = View.GONE
        }
        browserView = GeckoView(this).apply { setBackgroundColor(colors.canvas) }

        // A compact two-row glass dock: address/search first, navigation second.
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(7))
            background = glassSurface(28, colors.panel, colors.line)
            elevation = dp(8).toFloat()
            isClickable = true
        }
        addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(2), dp(3), dp(2))
            background = glassSurface(22, colors.field, colors.line)
        }
        siteInfoButton = actionButton("ⓘ", "Site information") {
            if (::address.isInitialized && address.hasFocus()) {
                address.text?.clear()
                address.requestFocus()
            } else {
                showSiteInformation()
            }
        }.apply {
            textSize = 21f
            background = rounded(android.graphics.Color.TRANSPARENT, 16)
        }
        addressRow.addView(siteInfoButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        address = EditText(this).apply {
            // URLs stay readable LTR; ordinary queries can follow Arabic or another RTL locale.
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            hint = tr("Search or enter URL")
            setHintTextColor(colors.muted)
            setTextColor(colors.ink)
            textSize = 15f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setPadding(dp(8), 0, dp(8), 0)
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            foreground = WrenFocusStyle.overlay(this@MainActivity, 14, colors.accent)
            elevation = 0f
            contentDescription = tr("Search or enter URL")
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE ||
                    action == EditorInfo.IME_ACTION_SEARCH) {
                    navigate(address.text.toString())
                    true
                } else false
            }
            setOnFocusChangeListener { _, focused ->
                if (!focused) {
                    if (addressSuggestionsController?.hasFocusInside() != true) {
                        updateAddress()
                        addressSuggestionsController?.hide()
                    }
                } else {
                    addressSuggestionsController?.onQueryChanged(address.text?.toString().orEmpty())
                }
                if (::siteInfoButton.isInitialized) {
                    siteInfoButton.text = if (focused) "×" else "ⓘ"
                    siteInfoButton.contentDescription = tr(if (focused) "Clear" else "Site information")
                }
            }
        }
        addressRow.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        address.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                addressSuggestionsController?.onQueryChanged(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        // TV focus order is address/search -> navigation row -> web document.
        // Never skip visible browser controls when the user presses D-pad down.
        address.setOnKeyListener { _, keyCode, event ->
            if (!isTelevisionDevice || event.action != KeyEvent.ACTION_DOWN ||
                keyCode != KeyEvent.KEYCODE_DPAD_DOWN
            ) {
                false
            } else if (addressSuggestionsController?.focusFirstSuggestion() == true) {
                true
            } else {
                when (
                    TvRemoteNavigationPolicy.moveDownFromAddress(
                        hasVisibleNavigationControls = ::navigationBar.isInitialized &&
                            navigationBar.visibility == View.VISIBLE &&
                            (0 until navigationBar.childCount).any { index ->
                                val child = navigationBar.getChildAt(index)
                                child.isShown && child.isFocusable && child.isEnabled
                            },
                        imeVisible = imeVisible
                    )
                ) {
                    TvFocusTarget.NAVIGATION_CONTROLS -> focusFirstNavigationControl()
                    TvFocusTarget.WEB_CONTENT -> browserView.requestFocus()
                    TvFocusTarget.ADDRESS, TvFocusTarget.NONE -> false
                }
            }
        }
        browserView.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (isTelevisionDevice &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                    TvRemoteNavigationPolicy.shouldReturnToChromeFromWeb(activePageScrollY, imeVisible)
                ) {
                    val target = lastFocusedNavigationControl
                        ?.takeIf { it.isShown && it.isFocusable && it.isEnabled }
                        ?: address
                    target.requestFocus()
                } else {
                    false
                }
            }
        }

        reloadButton = actionButton("↻", "Reload page") {
            activeTab?.session?.reload()
        }.apply {
            textSize = 22f
            background = rounded(android.graphics.Color.TRANSPARENT, 16)
        }
        addressRow.addView(reloadButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        navigationBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(1), dp(5), dp(1), 0)
        }
        navigationBar.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        bottomButton(if (BrowseXColors.isRtl(browserSettingsSnapshot)) "›" else "‹", "Go back") {
            activeTab?.takeIf { it.canGoBack }?.session?.goBack()
        }
        bottomButton(if (BrowseXColors.isRtl(browserSettingsSnapshot)) "‹" else "›", "Go forward") {
            activeTab?.takeIf { it.canGoForward }?.session?.goForward()
        }
        bottomButton("⌂", "Home") {
            activeTab?.session?.loadUri(homePageUrl())
        }
        bottomButton("☆", "Bookmark page") {
            activeTab?.url?.takeIf { it.startsWith("http") }?.let { url ->
                toggleBookmarkInRoom(
                    url,
                    activeTab?.title.orEmpty().takeUnless { title -> title == "New tab" }.orEmpty()
                )
            }
        }
        tabsButton = actionButton("1", "Open tabs") { showTabs() }.apply {
            textSize = 14f
            background = rounded(0x18FFFFFF, 14)
        }
        prepareTvNavigationControl(tabsButton)
        navigationBar.addView(tabsButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginStart = dp(1)
            marginEnd = dp(1)
        })
        bottomButton("⋯", "Browser menu") { showBrowserMenu() }
        uBlockButton = actionButton("⛨", "uBlock Origin controls") {
            uBlockOriginManager?.clickBrowserAction(activeTab?.session)
        }.apply {
            textSize = 18f
            visibility = View.GONE
        }
        prepareTvNavigationControl(uBlockButton)
        navigationBar.addView(uBlockButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginStart = dp(1)
            marginEnd = dp(1)
        })

        bottomBar.addView(addressRow, LinearLayout.LayoutParams(-1, dp(48)))
        bottomBar.addView(navigationBar, LinearLayout.LayoutParams(-1, dp(49)).apply {
            topMargin = dp(3)
        })

        // The page remains full-height behind the translucent chrome, like a floating glass dock.
        root.addView(browserView, FrameLayout.LayoutParams(-1, -1))
        root.addView(topBar, FrameLayout.LayoutParams(-1, dp(46), Gravity.TOP).apply {
            setMargins(dp(13), dp(5), dp(13), dp(3))
        })
        root.addView(progress, FrameLayout.LayoutParams(-1, dp(2), Gravity.TOP).apply {
            topMargin = dp(51)
            leftMargin = dp(13)
            rightMargin = dp(13)
        })
        val dockWidth = if (resources.configuration.screenWidthDp >= 600) {
            dp(720).coerceAtMost(
                (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(280))
            )
        } else -1
        root.addView(bottomBar, FrameLayout.LayoutParams(
            dockWidth, dp(118), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = dp(7)
            leftMargin = dp(10)
            rightMargin = dp(10)
        })
        addressSuggestionsController = AddressSuggestionsController(
            activity = this,
            root = root,
            dock = bottomBar,
            address = address,
            loadSuggestions = ::loadAddressSuggestions,
            onSelect = { suggestion ->
                addressSuggestionsController?.hide()
                when (suggestion.kind) {
                    AddressSuggestionKind.OPEN_TAB -> {
                        val match = tabs.firstOrNull { !it.privateMode && it.url == suggestion.value }
                        if (match != null) {
                            address.clearFocus()
                            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                                .hideSoftInputFromWindow(address.windowToken, 0)
                            activateTab(match)
                            browserView.requestFocus()
                        } else {
                            navigate(suggestion.value)
                        }
                    }
                    AddressSuggestionKind.BOOKMARK,
                    AddressSuggestionKind.HISTORY,
                    AddressSuggestionKind.SEARCH -> navigate(suggestion.value)
                }
            }
        )
        if (address.hasFocus()) addressSuggestionsController?.onQueryChanged(address.text?.toString().orEmpty())
        BrowseXColors.applyDirection(root, browserSettingsSnapshot)
        root.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val width = right - left
            val height = bottom - top
            if (width != oldRight - oldLeft || height != oldBottom - oldTop) {
                refreshAdaptivePresentation(width, height, view.isHardwareAccelerated)
            }
        }

        // New browser chrome is a Compose adaptation of the MIT-licensed
        // Kingfisher Browser address/navigation components. GeckoView and all
        // BrowseX controllers remain owned by this Activity.
        composeChromeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BrowseXBrowserChrome(
                    state = composeChromeState.value,
                    onNavigate = ::navigate,
                    onBack = { activeTab?.takeIf { it.canGoBack }?.session?.goBack() },
                    onForward = { activeTab?.takeIf { it.canGoForward }?.session?.goForward() },
                    onReload = { activeTab?.session?.reload() },
                    onHome = { activeTab?.session?.loadUri(homePageUrl()) },
                    onSiteInfo = ::showSiteInformation,
                    onBookmark = {
                        activeTab?.url?.takeIf { it.startsWith("http") }?.let { url ->
                            toggleBookmarkInRoom(
                                url,
                                activeTab?.title.orEmpty().takeUnless { it == "New tab" }.orEmpty()
                            )
                        }
                    },
                    onNewTab = { createTab(homePageUrl()) },
                    onOpenTabs = ::showTabs,
                    onMenu = { composeMenuOpen.value = true },
                    onProtection = {
                        val session = activeTab?.session
                        if (uBlockOriginManager?.hasBrowserAction(session) == true) {
                            uBlockOriginManager?.clickBrowserAction(session)
                        } else {
                            showPrivacyCenter()
                        }
                    },
                    loadSuggestions = ::loadAddressSuggestions,
                    onSelectSuggestion = ::selectComposeSuggestion,
                    menuOpen = composeMenuOpen.value,
                    onDismissMenu = { composeMenuOpen.value = false },
                    onMenuAction = { action ->
                        composeMenuOpen.value = false
                        performBrowserMenuAction(action)
                    }
                )
            }
        }
        root.addView(composeChromeView, FrameLayout.LayoutParams(-1, -1))

        // Keep the legacy view objects alive temporarily because the existing
        // adaptive/focus and suggestion controllers still reference them. They
        // are no longer part of the visible browser interface.
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        progress.visibility = View.GONE
        renderComposeChrome()
        return root
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun glassSurface(radius: Int, fill: Int, stroke: Int, strokeWidthDp: Int = 1) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(strokeWidthDp), stroke)
    }

    private fun actionButton(label: String, description: String, action: () -> Unit) =
        TextView(this).apply {
            text = tr(label)
            contentDescription = tr(description)
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(colors.ink)
            minimumWidth = dp(0)
            minimumHeight = if (adaptivePresentationConfig?.windowLayout == WrenWindowLayout.TV) dp(60) else dp(48)
            isFocusable = true
            isFocusableInTouchMode = false
            foreground = WrenFocusStyle.overlay(this@MainActivity, 15, colors.accent)
            setOnClickListener { action() }
        }

    private fun focusFirstNavigationControl(): Boolean {
        if (!::navigationBar.isInitialized || navigationBar.visibility != View.VISIBLE) return false
        val target = (0 until navigationBar.childCount)
            .map(navigationBar::getChildAt)
            .firstOrNull { it.isShown && it.isFocusable && it.isEnabled }
            ?: return false
        return target.requestFocus()
    }

    private fun prepareTvNavigationControl(control: View) {
        control.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) lastFocusedNavigationControl = view
        }
        control.setOnKeyListener { _, keyCode, event ->
            if (!isTelevisionDevice || event.action != KeyEvent.ACTION_DOWN) {
                false
            } else {
                when (TvRemoteNavigationPolicy.moveFromNavigation(keyCode, imeVisible)) {
                    TvFocusTarget.ADDRESS -> address.requestFocus()
                    TvFocusTarget.WEB_CONTENT -> browserView.requestFocus()
                    TvFocusTarget.NAVIGATION_CONTROLS, TvFocusTarget.NONE -> false
                }
            }
        }
    }

    private fun bottomButton(label: String, description: String, action: () -> Unit) =
        actionButton(label, description, action).also {
            it.textSize = 22f
            it.setTextColor(colors.ink)
            prepareTvNavigationControl(it)
            navigationBar.addView(it, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            })
        }

    private fun updateChrome() {
        if (::credentialVault.isInitialized) {
            credentialVault.setPrivateTabsOpen(tabs.any { it.privateMode })
        }
        if (!::browserView.isInitialized) return

        val tab = activeTab
        updateAddress()
        if (::tabsButton.isInitialized) {
            tabsButton.text = tabs.size.toString()
            tabsButton.textSize = 14f
            tabsButton.contentDescription = tr("Open tabs") + ", " + tabs.size
        }
        if (::progress.isInitialized) {
            val value = tab?.progress?.coerceIn(0, 100) ?: 0
            progress.progress = value
            progress.visibility = if (value in 1..99) View.VISIBLE else View.GONE
        }
        navigationBar.getChildAt(0)?.let { button ->
            button.isEnabled = tab?.canGoBack == true
            button.alpha = if (button.isEnabled) 1f else 0.38f
        }
        navigationBar.getChildAt(1)?.let { button ->
            button.isEnabled = tab?.canGoForward == true
            button.alpha = if (button.isEnabled) 1f else 0.38f
        }
        if (::pageIdentity.isInitialized) {
            val currentUrl = tab?.url.orEmpty()
            val host = runCatching { Uri.parse(currentUrl).host?.removePrefix("www.") }
                .getOrNull()?.takeIf { it.isNotBlank() }
            val identity = host ?: "Wren"
            pageIdentity.text = if (tab?.privateMode == true) "${tr("Private")} · $identity" else identity
        }
        if (::siteInfoButton.isInitialized) {
            val editingAddress = ::address.isInitialized && address.hasFocus()
            val hasSite = tab?.url?.startsWith("http") == true
            siteInfoButton.text = if (editingAddress) "×" else "ⓘ"
            siteInfoButton.contentDescription = tr(if (editingAddress) "Clear" else "Site information")
            siteInfoButton.isEnabled = editingAddress || hasSite
            siteInfoButton.alpha = if (siteInfoButton.isEnabled) 1f else 0.55f
        }
        if (::reloadButton.isInitialized) reloadButton.isEnabled = tab != null
        syncUBlockButton()
        renderComposeChrome(tab)
    }

    private fun renderComposeChrome(tab: BrowserTab? = activeTab) {
        if (!::composeChromeView.isInitialized) return
        val palette = colors
        val presentation = adaptivePresentationConfig
        val pageUrl = tab?.url.orEmpty()
        val host = runCatching { Uri.parse(pageUrl).host?.removePrefix("www.") }
            .getOrNull()?.takeIf { it.isNotBlank() }
        composeChromeState.value = BrowseXChromeState(
            url = pageUrl.takeUnless { it.startsWith(HOME_URL) }.orEmpty(),
            title = host ?: if (tab == null || pageUrl.startsWith(HOME_URL)) "New tab" else tab.title,
            progress = tab?.progress?.coerceIn(0, 100) ?: 0,
            tabCount = tabs.size,
            canGoBack = tab?.canGoBack == true,
            canGoForward = tab?.canGoForward == true,
            isPrivate = tab?.privateMode == true,
            isDark = palette.isDark,
            uBlockAvailable = uBlockOriginManager?.hasBrowserAction(tab?.session) == true,
            showNavigationActions = presentation?.showNavigationActions ?: true,
            dockAtTop = presentation?.addressPlacement == WrenAddressPlacement.TOP ||
                presentation?.addressPlacement == WrenAddressPlacement.TV_REMOTE,
            reduceTransparency = browserSettingsSnapshot.reduceTransparency,
            highContrast = browserSettingsSnapshot.highContrast,
            canvasColor = palette.canvas,
            panelColor = palette.panel,
            fieldColor = palette.field,
            inkColor = palette.ink,
            mutedColor = palette.muted,
            accentColor = palette.accent,
            lineColor = palette.line
        )
    }

    private fun syncUBlockButton() {
        if (!::uBlockButton.isInitialized) return
        val available = uBlockOriginManager?.hasBrowserAction(activeTab?.session) == true
        uBlockButton.visibility = if (available) View.VISIBLE else View.GONE
        uBlockButton.isEnabled = available
        uBlockButton.alpha = if (available) 1f else 0f
    }

    private fun updateAddress() {
        if (!::address.isInitialized || address.hasFocus()) return
        val currentUrl = activeTab?.url.orEmpty()
        val displayUrl = if (currentUrl.startsWith(HOME_URL)) "" else currentUrl
        if (address.text.toString() != displayUrl) {
            address.setText(displayUrl)
            address.setSelection(address.text.length)
        }
    }

    private fun normalizeInput(raw: String): String =
        if (raw.isBlank()) homePageUrl() else NavigationInput.normalize(raw, browserSettingsSnapshot.searchEngine, HOME_URL)

    private fun safeExternalUrl(uri: Uri): String? = NavigationInput.externalWebUrl(uri.toString())

    private suspend fun loadAddressSuggestions(query: String): List<AddressSuggestion> {
        val needle = query.trim().lowercase(java.util.Locale.ROOT)
        fun matches(title: String, url: String): Boolean =
            needle.isBlank() || title.lowercase(java.util.Locale.ROOT).contains(needle) ||
                url.lowercase(java.util.Locale.ROOT).contains(needle)

        val privateMode = activeTab?.privateMode == true
        val openTabs = tabs.asSequence()
            .filter { it.privateMode == privateMode && it.url.startsWith("http", ignoreCase = true) }
            .filter { matches(it.title, it.url) }
            .map { tab ->
                AddressSuggestion(
                    kind = AddressSuggestionKind.OPEN_TAB,
                    title = tab.title.ifBlank { android.net.Uri.parse(tab.url).host.orEmpty() },
                    subtitle = tab.url,
                    value = tab.url
                )
            }
            .toList()
        val storage = storageRuntime.awaitReady()
        val bookmarks = storage.bookmarks.all().asSequence()
            .filter { matches(it.title, it.url) }
            .map { item ->
                AddressSuggestion(
                    kind = AddressSuggestionKind.BOOKMARK,
                    title = item.title.ifBlank { android.net.Uri.parse(item.url).host.orEmpty() },
                    subtitle = item.url,
                    value = item.url
                )
            }
            .toList()
        val history = if (privateMode) {
            emptyList()
        } else {
            storage.history.recent(80).asSequence()
                .filter { matches(it.title, it.url) }
                .map { item ->
                    AddressSuggestion(
                        kind = AddressSuggestionKind.HISTORY,
                        title = item.title.ifBlank { android.net.Uri.parse(item.url).host.orEmpty() },
                        subtitle = item.url,
                        value = item.url
                    )
                }
                .toList()
        }
        return (openTabs + bookmarks + history)
            .distinctBy { it.value }
            .take(5)
    }

    private fun selectComposeSuggestion(suggestion: AddressSuggestion) {
        when (suggestion.kind) {
            AddressSuggestionKind.OPEN_TAB -> {
                val privateMode = activeTab?.privateMode == true
                val match = tabs.firstOrNull {
                    it.privateMode == privateMode && it.url == suggestion.value
                }
                if (match != null) {
                    activateTab(match)
                } else {
                    navigate(suggestion.value)
                }
            }
            AddressSuggestionKind.BOOKMARK,
            AddressSuggestionKind.HISTORY,
            AddressSuggestionKind.SEARCH -> navigate(suggestion.value)
        }
    }

    private fun navigate(raw: String) {
        addressSuggestionsController?.hide()
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
            { requested ->
                requested === activeTab?.session && tabs.any { it.session === requested } && !shuttingDown
            },
            tab.privateMode
        )
        tab.session.permissionDelegate = tab.permissionDelegate
        tab.session.setScrollDelegate(object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                pageScrollOffsets[session] = scrollY
                if (session === activeTab?.session) activePageScrollY = scrollY
            }
        })
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
            },
            translate = ::tr
        )
    }

    private fun setFullscreen(enabled: Boolean) {
        fullscreen = enabled
        if (::composeChromeView.isInitialized) {
            // The imported Compose chrome owns the visible controls; legacy views
            // stay detached from presentation even when fullscreen is exited.
            topBar.visibility = View.GONE
            bottomBar.visibility = View.GONE
            progress.visibility = View.GONE
            composeChromeView.visibility = if (enabled) View.GONE else View.VISIBLE
        } else {
            topBar.visibility = if (enabled) View.GONE else View.VISIBLE
            bottomBar.visibility = if (enabled) View.GONE else View.VISIBLE
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else controller.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun showTabs() {
        TabsPage(
            activity = this,
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
        composeMenuOpen.value = true
    }

    private fun performBrowserMenuAction(action: BrowseXMenuAction) {
        when (action) {
            BrowseXMenuAction.BOOKMARK_TOGGLE -> activeTab?.url?.takeIf { it.startsWith("http") }?.let { url ->
                toggleBookmarkInRoom(
                    url,
                    activeTab?.title.orEmpty().takeUnless { it == "New tab" }.orEmpty()
                )
            }
            BrowseXMenuAction.BOOKMARKS -> showSavedPages("Bookmarks", emptyList())
            BrowseXMenuAction.HISTORY -> showSavedPages("History", emptyList())
            BrowseXMenuAction.DOWNLOADS -> openDownloads()
            BrowseXMenuAction.SHARE -> pageActions.shareCurrentPage()
            BrowseXMenuAction.COPY_ADDRESS -> pageActions.copyCurrentAddress()
            BrowseXMenuAction.FIND_IN_PAGE -> pageActions.findInPage()
            BrowseXMenuAction.READER -> openReaderMode()
            BrowseXMenuAction.SITE_INFO -> showSiteInformation()
            BrowseXMenuAction.SEARCH_ENGINE -> pageActions.chooseSearchEngine()
            BrowseXMenuAction.CLEAR_HISTORY -> AlertDialog.Builder(this)
                .setMessage(tr("Delete all browsing history?"))
                .setNegativeButton(tr("Cancel"), null)
                .setPositiveButton(tr("Clear")) { _, _ ->
                    lifecycleScope.launch {
                        runCatching { clearStoredHistory() }.onSuccess { toast("History cleared") }
                            .onFailure {
                                toast(BrowseXStrings.text(this@MainActivity, "Could not clear history", "تعذر مسح السجل"))
                            }
                    }
                }.show()
            BrowseXMenuAction.PRIVACY_CENTER -> showPrivacyCenter()
            BrowseXMenuAction.SETTINGS -> showSettings()
            BrowseXMenuAction.PRIVATE_TAB -> createTab(homePageUrl(), privateMode = true)
            BrowseXMenuAction.CLOSE_PRIVATE_TABS -> {
                val privateTabs = tabs.filter { it.privateMode }
                if (privateTabs.isEmpty()) toast("No private tabs are open")
                else AlertDialog.Builder(this)
                    .setMessage(tr("Close all private tabs? Their browsing data will be discarded."))
                    .setNegativeButton(tr("Cancel"), null)
                    .setPositiveButton(tr("Close")) { _, _ -> privateTabs.forEach(::closeTab) }
                    .show()
            }
            BrowseXMenuAction.RECALL -> showRecall()
            BrowseXMenuAction.PASSWORDS -> passwordManager.show()
            BrowseXMenuAction.SAVED_SESSIONS -> showSavedSessions()
        }
    }

    private fun showSavedSessions() {
        val regularTabs = tabs.filterNot { it.privateMode }
        val urls = regularTabs.map { it.url }
        val selectedIndex = regularTabs.indexOfFirst { it === activeTab }.coerceAtLeast(0)
        SavedSessionsPage(
            activity = this,
            currentUrls = urls,
            activeIndex = selectedIndex,
            onRestore = { savedUrls, savedIndex ->
                // Keep private tabs isolated; only replace the regular browsing workspace.
                tabs.filterNot { it.privateMode }.toList().forEach(::closeTab)
                val restored = mutableListOf<BrowserTab>()
                savedUrls.forEach { savedUrl ->
                    val before = tabs.size
                    createTab(savedUrl)
                    if (tabs.size > before) tabs.lastOrNull()?.let(restored::add)
                }
                restored.getOrNull(savedIndex.coerceIn(0, (restored.size - 1).coerceAtLeast(0)))?.let(::activateTab)
                if (restored.size < savedUrls.size) {
                    toast(BrowseXStrings.text(
                        this,
                        "Some tabs could not be restored because the tab limit was reached",
                        "تعذرت استعادة بعض التبويبات بسبب بلوغ الحد الأقصى"
                    ))
                }
            },
            onOpenAlongside = { savedUrls, savedIndex ->
                val opened = mutableListOf<BrowserTab>()
                savedUrls.forEach { savedUrl ->
                    val before = tabs.size
                    createTab(savedUrl)
                    if (tabs.size > before) tabs.lastOrNull()?.let(opened::add)
                }
                opened.getOrNull(savedIndex.coerceIn(0, (opened.size - 1).coerceAtLeast(0)))?.let(::activateTab)
                if (opened.size < savedUrls.size) {
                    toast(BrowseXStrings.text(
                        this,
                        "Some tabs could not be opened because the tab limit was reached",
                        "تعذر فتح بعض التبويبات بسبب بلوغ الحد الأقصى"
                    ))
                }
            }
        ).show()
    }

    private fun toggleBookmarkInRoom(url: String, title: String) {
        lifecycleScope.launch {
            runCatching {
                val bookmarks = storageRuntime.awaitReady().bookmarks
                if (bookmarks.find(url) != null) {
                    bookmarks.remove(url)
                    false
                } else {
                    bookmarks.add(url, title)
                }
            }.onSuccess { added ->
                toast(if (added) "Bookmark saved" else "Bookmark removed")
            }.onFailure { error ->
                android.util.Log.e("BrowseX", "Could not update bookmark", error)
                toast(BrowseXStrings.text(this@MainActivity, "Could not update bookmark", "تعذر تحديث المفضلة"))
            }
        }
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
                        toast(BrowseXStrings.text(this@MainActivity, "This page does not contain readable text", "هذه الصفحة لا تحتوي على نص قابل للقراءة"))
                    } else {
                        ReaderPage(this, tab.title, sourceUrl, content).show()
                    }
                }
            },
            { error ->
                runOnUiThread {
                    if (loading.isShowing) loading.dismiss()
                    android.util.Log.w("BrowseX", "Reader extraction failed", error)
                    if (!isFinishing && !isDestroyed) toast(BrowseXStrings.text(this@MainActivity, "Could not prepare reader view", "تعذر تجهيز وضع القراءة"))
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
                permissions = if (tab.privateMode) emptyList() else tab.sitePermissions,
                onPermissionChange = { permission, value ->
                    if (!tab.privateMode) {
                        val storage = browserRuntime?.storageController
                        storage?.setPermission(permission, value)
                        storage?.getPermissions(url, false)?.accept(
                            { updated -> runOnUiThread {
                                if (tabs.any { it === tab } && tab.url == url) tab.sitePermissions = updated.orEmpty().toList()
                            } },
                            { error -> android.util.Log.w("BrowseX", "Could not refresh site permissions", error) }
                        )
                    }
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
                    lifecycleScope.launch {
                        val cleared = runCatching {
                            storageRuntime.awaitReady().sitePermissions.clearOrigin(origin)
                            true
                        }.onFailure { error ->
                            android.util.Log.e("BrowseX", "Could not reset saved site permissions", error)
                        }.getOrDefault(false)
                        if (cleared && activeTab === tab && tab.url == url) {
                            // Clear persisted camera/microphone grants before reloading so
                            // Gecko cannot immediately reuse a stale allow decision.
                            tab.session.reload()
                        } else if (!cleared) {
                            toast(BrowseXStrings.text(this@MainActivity, "Could not reset saved site permissions", "تعذر إعادة ضبط أذونات الموقع"))
                        }
                    }
                    tab.sitePermissions = emptyList()
                    storage?.getPermissions(url, false)?.accept(
                        { updated -> runOnUiThread {
                            if (tabs.any { it === tab } && tab.url == url) tab.sitePermissions = updated.orEmpty().toList()
                        } },
                        { error -> android.util.Log.w("BrowseX", "Could not refresh reset permissions", error) }
                    )
                    toast("Site permissions reset")
                },
                privateMode = tab.privateMode,
                onMediaPermissionChange = { _, decision ->
                    // Reloading ends any live getUserMedia tracks before the new Ask/Block
                    // decision is evaluated by Gecko on the next page load.
                    if (decision != "allow" && activeTab === tab && tab.url == url) {
                        tab.session.reload()
                    }
                }
            ).show()
        }
        val storage = browserRuntime?.storageController
        // Gecko's non-private permission store is shared across sessions. Never query it
        // while showing site information for a private tab.
        if (tab.privateMode || storage == null) {
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
            openBookmarks = { showSavedPages("Bookmarks", emptyList()) },
            openHistory = { showSavedPages("History", emptyList()) },
            openDownloads = { openDownloads() },
            clearHistory = {
                lifecycleScope.launch {
                    runCatching { clearStoredHistory() }
                        .onSuccess { toast("History cleared") }
                        .onFailure { toast(BrowseXStrings.text(this@MainActivity, "Could not clear history", "تعذر مسح السجل")) }
                }
            },
            setWebsiteNotifications = { enabled ->
                updateBrowserSettings(transform = { it.copy(siteNotificationsEnabled = enabled) })
                if (!enabled) NotificationManagerCompat.from(this).cancelAll()
            },
            importBookmarks = { bookmarkImportLauncher.launch(arrayOf("text/html", "application/json", "text/json", "text/plain")) },
            exportBookmarks = { bookmarkExportLauncher.launch("wren-bookmarks.html") },
            showHomePreferences = { showHomePreferences() },
            openPrivacyCenter = { showPrivacyCenter() },
            openSitePermissions = { SitePermissionsPage(this) { clearBrowsingData(org.mozilla.geckoview.StorageController.ClearFlags.PERMISSIONS, clearHistory = false) }.show() },
            manageUBlockOrigin = { showUBlockOriginSettings() },
            openAutofillSettings = { openAutofillServiceSettings() }
        ).show()
    }


    private fun restoreInitialBrowserTabs(startupIntent: Intent?) {
        if (initialBrowserTabsRestoreStarted || isFinishing || isDestroyed) return
        initialBrowserTabsRestoreStarted = true
        lifecycleScope.launch {
            val restoreResult = runCatching { storageRuntime.restoreOpenTabs() }
                .onFailure { error ->
                    android.util.Log.e("BrowseX", "Room tab restore failed; saved browser data was not modified", error)
                }
            val storageRestored = restoreResult.isSuccess
            val snapshot = restoreResult.getOrElse {
                // Never resurrect stale SharedPreferences tabs or persist a clean snapshot
                // over data that could not be read. A later app launch can retry after repair.
                com.browsex.app.storage.TabSessionSnapshot(emptyList(), 0, 0L)
            }
            if (isFinishing || isDestroyed) return@launch
            val externalUrl = startupIntent?.data?.let(::safeExternalUrl)
            tabController.restoreInitialTabs(snapshot.urls, snapshot.activeIndex, externalUrl)
            // Keep persistence disabled when opening the database failed. Otherwise the
            // fallback home tab could overwrite a valid snapshot after a transient read error.
            initialBrowserTabsRestored = storageRestored
            if (!storageRestored) {
                toast(BrowseXStrings.text(
                    this@MainActivity,
                    "Saved browser data could not be opened. Your data has not been deleted. Do not clear app data.",
                    "تعذر فتح بيانات التصفح المحفوظة. لم يتم حذف بياناتك. لا تمسح بيانات التطبيق."
                ))
            }
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
        lifecycleScope.launch {
            val settings = runCatching { storageRuntime.settings.snapshot() }.getOrElse {
                android.util.Log.e("BrowseX", "Could not load homepage preferences", it)
                toast(BrowseXStrings.text(this@MainActivity, "Could not load homepage settings", "تعذر تحميل إعدادات الصفحة الرئيسية"))
                return@launch
            }
            val labels = arrayOf(
                tr("Show favorite shortcuts"),
                tr("Show discovery card")
            )
            val checked = booleanArrayOf(settings.homeShowQuickLinks, settings.homeShowDiscovery)
            AlertDialog.Builder(this@MainActivity)
                .setTitle(tr("Home page"))
                .setMessage(tr("Choose which sections appear and how shortcuts are arranged."))
                .setMultiChoiceItems(labels, checked) { _, which, enabled ->
                    checked[which] = enabled
                }
                .setPositiveButton(tr("Continue")) { _, _ ->
                    var selectedLayout = if (settings.homeLayout == "list") 1 else 0
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(tr("Shortcut layout"))
                        .setSingleChoiceItems(
                            arrayOf(tr("Grid layout"), tr("List layout")),
                            selectedLayout
                        ) { _, index -> selectedLayout = index }
                        .setPositiveButton(tr("Apply")) { _, _ ->
                            updateBrowserSettings({
                                it.copy(
                                    homeShowQuickLinks = checked[0],
                                    homeShowDiscovery = checked[1],
                                    homeLayout = if (selectedLayout == 1) "list" else "grid"
                                )
                            }) {
                                activeTab?.takeIf { tab -> tab.url.startsWith(HOME_URL) }
                                    ?.session?.loadUri(homePageUrl())
                            }
                        }
                        .setNeutralButton(tr("Reset")) { _, _ ->
                            updateBrowserSettings({
                                it.copy(
                                    homeShowQuickLinks = true,
                                    homeShowDiscovery = true,
                                    homeLayout = "grid"
                                )
                            }) {
                                activeTab?.takeIf { tab -> tab.url.startsWith(HOME_URL) }
                                    ?.session?.loadUri(homePageUrl())
                            }
                        }
                        .setNegativeButton(tr("Cancel"), null)
                        .show()
                }
                .setNegativeButton(tr("Cancel"), null)
                .show()
        }
    }

    private fun showPrivacyCenter() {
        PrivacyPage(
            activity = this,
            clearData = { flags, clearHistory -> clearBrowsingData(flags, clearHistory) },
            manageProtection = { showUBlockOriginSettings() }
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
        lifecycleScope.launch {
            val graph = runCatching { storageRuntime.awaitReady() }.getOrElse {
                android.util.Log.e("BrowseX", "Could not load recall library", it)
                toast(BrowseXStrings.text(this@MainActivity, "Could not open search library", "تعذر فتح مكتبة البحث"))
                return@launch
            }
            val bookmarkRows = graph.bookmarks.all().take(30).map { it.title to it.url }
            val historyRows = graph.history.recent(30).map { it.title to it.url }
            RecallPage(
                activity = this@MainActivity,
                openTabs = tabs.filterNot { it.privateMode }.map { it.title to it.url },
                bookmarkRows = bookmarkRows,
                historyRows = historyRows,
                onOpen = { url ->
                    val existing = tabs.firstOrNull { !it.privateMode && it.url == url }
                    if (existing != null) activateTab(existing) else createTab(url)
                }
            ).show()
        }
    }

    private fun showSavedPages(title: String, pages: List<String>) {
        RoomSavedPagesPage(
            activity = this,
            title = title,
            onOpen = { url -> createTab(url) }
        ).show()
    }

    /** Shows the in-app view over Android's durable DownloadManager queue. */
    private fun openDownloads() {
        DownloadsPage(this) { downloadId ->
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
            toast(BrowseXStrings.text(this@MainActivity, "This download response is not supported", "استجابة التنزيل هذه غير مدعومة"))
            return
        }
        val started = streamDownloadCoordinator.start(
            response = response,
            onCompleted = { result ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) toast(getString(R.string.ui_downloaded_file, result.displayName))
                }
                android.util.Log.i("BrowseX", "Response stream saved: ${result.displayName}, bytes=${result.bytesWritten}")
            },
            onFailed = { error, cancelled ->
                if (!cancelled) android.util.Log.e("BrowseX", "Response stream download failed", error)
                runOnUiThread {
                    if (!cancelled && !isFinishing && !isDestroyed) {
                        toast(getString(R.string.ui_could_not_save_download, error?.message ?: getString(R.string.ui_unknown_status)))
                    }
                }
            }
        )
        if (!started) {
            closeDownloadBodySafely { body.close() }
            toast(BrowseXStrings.text(this@MainActivity, "This download response is not supported", "استجابة التنزيل هذه غير مدعومة"))
        }
    }


    private fun toast(message: String) {
        android.widget.Toast.makeText(this, tr(message), android.widget.Toast.LENGTH_SHORT).show()
    }

}
