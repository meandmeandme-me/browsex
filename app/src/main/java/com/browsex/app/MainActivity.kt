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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.browsex.app.storage.BrowserStorageRuntime
import com.browsex.app.storage.BrowserSettings
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
    private var browserSettingsSnapshot = BrowserSettings()
    internal val currentBrowserSettings: BrowserSettings get() = browserSettingsSnapshot
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
    private lateinit var address: EditText
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
            clearHistory = {
                lifecycleScope.launch {
                    runCatching { clearStoredHistory() }
                        .onFailure { error ->
                            android.util.Log.e("BrowseX", "Could not clear browsing history", error)
                        }
                }
            },
            clearSiteMediaDecisions = { origin ->
                lifecycleScope.launch {
                    runCatching {
                        val permissions = storageRuntime.awaitReady().sitePermissions
                        if (origin == null) permissions.clearAll() else permissions.clearOrigin(origin)
                    }.onFailure { error ->
                        android.util.Log.e("BrowseX", "Could not clear site permission decisions", error)
                    }
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
        store = BrowserStore(this)
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

    override fun onStart() {
        super.onStart()
        hostLifecycleCoordinator.onStart()
    }

    override fun onStop() {
        // Extension action popups own a separate GeckoSession; release it as soon as the host stops.
        uBlockOriginManager?.closePopup()
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

    private fun homePageUrl(): String =
        "$HOME_URL?bxlang=${browserSettingsSnapshot.languageMode}&bxtheme=${browserSettingsSnapshot.themeMode}" +
            "&bxquick=${browserSettingsSnapshot.homeShowQuickLinks}&bxdiscovery=${browserSettingsSnapshot.homeShowDiscovery}&bxlayout=${browserSettingsSnapshot.homeLayout}"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createBrowserUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.canvas)
            BrowseXColors.applyDirection(this, browserSettingsSnapshot)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Quiet page identity at the top; all everyday browser controls stay within thumb reach.
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(2), dp(5), dp(2))
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
        }
        addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(2), dp(3), dp(2))
            background = glassSurface(22, colors.field, colors.line)
        }
        siteInfoButton = actionButton("ⓘ", "Site information") {
            showSiteInformation()
        }.apply {
            textSize = 21f
            background = rounded(android.graphics.Color.TRANSPARENT, 16)
        }
        addressRow.addView(siteInfoButton, LinearLayout.LayoutParams(dp(42), dp(44)))

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
            elevation = 0f
            contentDescription = tr("Search or enter URL")
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE ||
                    action == EditorInfo.IME_ACTION_SEARCH) {
                    navigate(address.text.toString())
                    true
                } else false
            }
            setOnFocusChangeListener { _, focused -> if (!focused) updateAddress() }
        }
        addressRow.addView(address, LinearLayout.LayoutParams(0, dp(44), 1f))

        reloadButton = actionButton("↻", "Reload page") {
            activeTab?.session?.reload()
        }.apply {
            textSize = 22f
            background = rounded(android.graphics.Color.TRANSPARENT, 16)
        }
        addressRow.addView(reloadButton, LinearLayout.LayoutParams(dp(42), dp(44)))

        navigationBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(1), dp(5), dp(1), 0)
        }
        bottomButton("‹", "Go back") {
            activeTab?.takeIf { it.canGoBack }?.session?.goBack()
        }
        bottomButton("›", "Go forward") {
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
        navigationBar.addView(tabsButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(1)
            marginEnd = dp(1)
        })
        bottomButton("⋯", "Browser menu") { showBrowserMenu() }
        uBlockButton = actionButton("盾", "uBlock Origin controls") {
            uBlockOriginManager?.clickBrowserAction(activeTab?.session)
        }.apply {
            textSize = 18f
            visibility = View.GONE
        }
        navigationBar.addView(uBlockButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(1)
            marginEnd = dp(1)
        })

        bottomBar.addView(addressRow, LinearLayout.LayoutParams(-1, dp(48)))
        bottomBar.addView(navigationBar, LinearLayout.LayoutParams(-1, dp(49)).apply {
            topMargin = dp(3)
        })

        root.addView(topBar, LinearLayout.LayoutParams(-1, dp(46)).apply {
            setMargins(dp(13), dp(5), dp(13), dp(3))
        })
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))
        root.addView(browserView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomBar, LinearLayout.LayoutParams(-1, dp(108)).apply {
            setMargins(dp(10), dp(6), dp(10), dp(7))
        })
        BrowseXColors.applyDirection(root, browserSettingsSnapshot)
        return root
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun glassSurface(radius: Int, fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun actionButton(label: String, description: String, action: () -> Unit) =
        TextView(this).apply {
            text = tr(label)
            contentDescription = tr(description)
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(colors.ink)
            minimumWidth = dp(0)
            minimumHeight = dp(44)
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun bottomButton(label: String, description: String, action: () -> Unit) =
        actionButton(label, description, action).also {
            it.textSize = 22f
            it.setTextColor(colors.ink)
            navigationBar.addView(it, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
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
            val hasSite = tab?.url?.startsWith("http") == true
            siteInfoButton.isEnabled = hasSite
            siteInfoButton.alpha = if (hasSite) 1f else 0.55f
        }
        if (::reloadButton.isInitialized) reloadButton.isEnabled = tab != null
        syncUBlockButton()
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
        lifecycleScope.launch {
            val url = activeTab?.url.orEmpty()
            val bookmarked = url.startsWith("http") && runCatching {
                storageRuntime.awaitReady().bookmarks.find(url) != null
            }.getOrDefault(false)
            val actions = arrayOf(
                if (bookmarked) "Remove bookmark" else "Add bookmark",
                "Bookmarks", "History", "Downloads", "Share page", "Copy page address", "Find in page", "Reader mode",
                "Site information", "Search engine", "Clear browsing history", "Settings",
                "New private tab", "Close private tabs", "Find anything", "Passwords"
            ).map(::tr).toTypedArray()
            AlertDialog.Builder(this@MainActivity).setTitle("Wren").setItems(actions) { _, index ->
                when (index) {
                    0 -> activeTab?.url?.takeIf { it.startsWith("http") }?.let { pageUrl ->
                        toggleBookmarkInRoom(pageUrl, activeTab?.title.orEmpty().takeUnless { title -> title == "New tab" }.orEmpty())
                    }
                    1 -> showSavedPages("Bookmarks", emptyList())
                    2 -> showSavedPages("History", emptyList())
                    3 -> openDownloads()
                    4 -> pageActions.shareCurrentPage()
                    5 -> pageActions.copyCurrentAddress()
                    6 -> pageActions.findInPage()
                    7 -> openReaderMode()
                    8 -> showSiteInformation()
                    9 -> pageActions.chooseSearchEngine()
                    10 -> AlertDialog.Builder(this@MainActivity).setMessage(tr("Delete all browsing history?"))
                        .setNegativeButton(tr("Cancel"), null)
                        .setPositiveButton(tr("Clear")) { _, _ ->
                            lifecycleScope.launch {
                                runCatching { clearStoredHistory() }.onSuccess { toast("History cleared") }
                                    .onFailure { toast(BrowseXStrings.text(this@MainActivity, "Could not clear history", "تعذر مسح السجل")) }
                            }
                        }.show()
                    11 -> showSettings()
                    12 -> createTab(homePageUrl(), privateMode = true)
                    13 -> {
                        val privateTabs = tabs.filter { it.privateMode }
                        if (privateTabs.isEmpty()) toast("No private tabs are open")
                        else AlertDialog.Builder(this@MainActivity)
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
                        ReaderPage(this, tab.title, sourceUrl, content, store).show()
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
                store = store,
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
                        runCatching { storageRuntime.awaitReady().sitePermissions.clearOrigin(origin) }
                            .onFailure { error -> android.util.Log.e("BrowseX", "Could not reset saved site permissions", error) }
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
                privateMode = tab.privateMode
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
            store = store,
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
            importBookmarks = { bookmarkImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
            exportBookmarks = { bookmarkExportLauncher.launch("browsex-bookmarks.json") },
            showHomePreferences = { showHomePreferences() },
            openPrivacyCenter = { showPrivacyCenter() },
            manageUBlockOrigin = { showUBlockOriginSettings() },
            openAutofillSettings = { openAutofillServiceSettings() }
        ).show()
    }


    private fun restoreInitialBrowserTabs(startupIntent: Intent?) {
        if (initialBrowserTabsRestoreStarted || isFinishing || isDestroyed) return
        initialBrowserTabsRestoreStarted = true
        lifecycleScope.launch {
            val snapshot = runCatching { storageRuntime.restoreOpenTabs() }
                .onFailure { error ->
                    android.util.Log.e("BrowseX", "Room tab restore failed; starting with a clean tab snapshot", error)
                }
                .getOrElse {
                    // BrowseX has no pre-existing user tab data to migrate. Room is the
                    // sole source of truth; never resurrect stale SharedPreferences tabs.
                    com.browsex.app.storage.TabSessionSnapshot(emptyList(), 0, 0L)
                }
            if (isFinishing || isDestroyed) return@launch
            val externalUrl = startupIntent?.data?.let(::safeExternalUrl)
            tabController.restoreInitialTabs(snapshot.urls, snapshot.activeIndex, externalUrl)
            initialBrowserTabsRestored = true
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
            val labels = arrayOf(tr("Show favorite shortcuts"), tr("Show discovery card"))
            val checked = booleanArrayOf(settings.homeShowQuickLinks, settings.homeShowDiscovery)
            AlertDialog.Builder(this@MainActivity)
                .setTitle(tr("Home page"))
                .setMultiChoiceItems(labels, checked) { _, which, enabled ->
                    updateBrowserSettings({
                        if (which == 0) it.copy(homeShowQuickLinks = enabled)
                        else it.copy(homeShowDiscovery = enabled)
                    })
                }
                .setPositiveButton(tr("Next")) { _, _ ->
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(tr("Shortcut layout"))
                        .setSingleChoiceItems(
                            arrayOf(tr("Grid layout"), tr("List layout")),
                            arrayOf("grid", "list").indexOf(settings.homeLayout).coerceAtLeast(0)
                        ) { choice, index ->
                            updateBrowserSettings({ it.copy(homeLayout = arrayOf("grid", "list")[index]) }) {
                                choice.dismiss()
                                activeTab?.takeIf { tab -> tab.url.startsWith(HOME_URL) }?.session?.loadUri(homePageUrl())
                            }
                        }
                        .setNeutralButton(tr("Reset")) { _, _ ->
                            updateBrowserSettings({
                                it.copy(homeShowQuickLinks = true, homeShowDiscovery = true, homeLayout = "grid")
                            }) {
                                activeTab?.takeIf { tab -> tab.url.startsWith(HOME_URL) }?.session?.loadUri(homePageUrl())
                            }
                        }
                        .setNegativeButton(tr("Close"), null)
                        .show()
                }
                .setNegativeButton(tr("Cancel"), null)
                .show()
        }
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
                store = store,
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
            store = store,
            title = title,
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
