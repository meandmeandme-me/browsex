package com.wren.app

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * Coordinates tab membership, selection and popup creation.
 *
 * Platform UI, permission delegate setup and runtime ownership are injected by
 * the Activity. Session teardown is delegated to BrowserSessionLifecycleCoordinator.
 * All public operations are expected on the Android main thread except Gecko's
 * popup callback, whose UI continuation is dispatched by the host.
 */
internal class BrowserTabController(
    private val registry: BrowserTabRegistry<BrowserTab>,
    private val sessionLifecycle: BrowserSessionLifecycleCoordinator,
    private val homeUrl: () -> String,
    private val isHostUnavailable: () -> Boolean,
    private val attachDelegates: (BrowserTab) -> Unit,
    private val runtime: () -> GeckoRuntime,
    private val attachSessionToView: (GeckoSession) -> Unit,
    private val releaseViewSession: () -> Unit,
    private val enforceSessionBudget: () -> Unit,
    private val updateChrome: () -> Unit,
    private val showMessage: (String) -> Unit,
    private val recordRecentlyClosed: (BrowserTab) -> Unit,
    private val dispatchToMain: (() -> Unit) -> Unit,
    private val setExtensionTabActive: (GeckoSession, Boolean) -> Unit = { _, _ -> },
    private val maxTabs: Int = TabSessionState.MAX_TABS
) {
    val tabs: List<BrowserTab> get() = registry.tabs
    val activeTab: BrowserTab? get() = registry.activeTab

    /** Restores validated regular tabs and opens the selected tab (or the home tab). */
    fun restoreInitialTabs(urls: List<String>, activeIndex: Int, externalUrl: String?) {
        // Revalidate at the controller boundary as well as at storage/import boundaries.
        // This keeps callers from accidentally restoring unsafe, oversized or excessive tab lists.
        val snapshot = TabSessionState.normalize(urls, activeIndex, homeUrl())
        registry.replaceAll(snapshot.urls.map { url -> BrowserTab(url = url) })
        val restoredActive = tabs.getOrNull(snapshot.activeIndex)
        if (externalUrl != null) {
            if (tabs.size >= maxTabs) {
                val discardIndex = tabs.indices.firstOrNull { tabs[it] !== restoredActive } ?: 0
                if (tabs.isNotEmpty()) registry.removeAt(discardIndex)
            }
            val externalTab = BrowserTab(url = externalUrl)
            registry.add(externalTab)
            activateTab(externalTab)
        } else {
            val initialTab = restoredActive ?: BrowserTab(url = homeUrl()).also(registry::add)
            activateTab(initialTab)
        }
    }

    /** Replaces tab records after a storage operation while preserving the caller's selected index. */
    fun replaceTabs(tabsToRestore: List<BrowserTab>, activeIndex: Int) {
        registry.replaceAll(tabsToRestore)
        registry.select(tabsToRestore.getOrNull(activeIndex))
    }

    fun activateRestoredSelection(activeIndex: Int) {
        if (tabs.isEmpty()) registry.add(BrowserTab(url = homeUrl()))
        val restored = tabs.getOrNull(activeIndex) ?: tabs.first()
        activateTab(restored)
    }

    fun clear() = registry.clear()

    fun createTab(url: String, privateMode: Boolean = false) {
        if (isHostUnavailable()) return
        if (tabs.size >= maxTabs) {
            showMessage("You can keep up to $maxTabs tabs open")
            return
        }
        val targetUrl = if (url == HOME_URL) homeUrl() else url
        val tab = BrowserTab(privateMode = privateMode, url = targetUrl)
        registry.add(tab)
        activateTab(tab)
    }

    fun activateTab(tab: BrowserTab) {
        if (tabs.none { it === tab }) return
        val previous = activeTab
        if (previous !== tab) {
            previous?.takeIf { it.sessionOpened }?.let {
                runCatching {
                    it.session.setFocused(false)
                    it.session.setActive(false)
                    setExtensionTabActive(it.session, false)
                }
            }
        }
        registry.select(tab)
        tab.lastActiveAt = android.os.SystemClock.elapsedRealtime()
        try {
            if (tab.crashed) {
                tab.recoveryAttempts = 0
                tab.crashed = false
            }
            if (!tab.sessionOpened) {
                attachDelegates(tab)
                // Mark ownership before opening so a partially failed Gecko open is still cleaned up.
                tab.sessionOpened = true
                tab.session.open(runtime())
                tab.session.loadUri(tab.url.ifBlank { homeUrl() })
            }
            tab.session.setActive(true)
            tab.session.setFocused(true)
            setExtensionTabActive(tab.session, true)
            attachSessionToView(tab.session)
            enforceSessionBudget()
            updateChrome()
        } catch (error: RuntimeException) {
            // A load/focus/view failure can happen after Gecko has opened the session.
            // Reset through the lifecycle owner to avoid leaking a native session.
            runCatching { sessionLifecycle.resetSession(tab) }
                .onFailure { cleanupError -> error.addSuppressed(cleanupError) }
            tab.sessionOpened = false
            tab.crashed = true
            android.util.Log.e("Wren", "Could not activate tab session", error)
            showMessage("This tab could not be opened. Try again.")
            updateChrome()
        }
    }

    fun closeTab(
        tab: BrowserTab,
        ensureNonEmpty: Boolean = true,
        recordRecentlyClosedTab: Boolean = true
    ) {
        val index = tabs.indexOfFirst { it === tab }
        if (index < 0) return
        val wasActive = activeTab === tab
        if (!tab.privateMode && recordRecentlyClosedTab) {
            runCatching { recordRecentlyClosed(tab) }
                .onFailure { android.util.Log.w("Wren", "Could not record recently closed tab", it) }
        }
        // Extension and view callbacks are best-effort: neither may prevent the
        // lifecycle owner from closing the native GeckoSession and removing the tab.
        if (tab.sessionOpened) {
            runCatching { setExtensionTabActive(tab.session, false) }
                .onFailure { android.util.Log.w("Wren", "Could not deactivate extension tab", it) }
        }
        if (wasActive) {
            runCatching { releaseViewSession() }
                .onFailure { android.util.Log.w("Wren", "Could not release active GeckoView session", it) }
        }
        sessionLifecycle.closeAndDiscard(tab)
        registry.removeAt(index)
        if (tabs.isEmpty()) {
            if (ensureNonEmpty) createTab(homeUrl())
        } else if (wasActive) activateTab(tabs[minOf(index, tabs.lastIndex)])
        else updateChrome()
    }

    fun onNewSession(parent: BrowserTab): GeckoResult<GeckoSession>? {
        if (isHostUnavailable() || tabs.size >= maxTabs) {
            dispatchToMain {
                if (!isHostUnavailable()) showMessage("Tab limit reached")
            }
            return null
        }
        val child = BrowserTab(privateMode = parent.privateMode)
        try {
            attachDelegates(child)
            // Set the flag first so lifecycle cleanup also covers a partially failed open.
            child.sessionOpened = true
            child.session.open(runtime())
            if (!registry.add(child)) {
                sessionLifecycle.closeAndDiscard(child)
                return null
            }
            dispatchToMain {
                if (!isHostUnavailable() && tabs.any { it === child }) activateTab(child)
                else if (tabs.any { it === child }) closeTab(child)
            }
            return GeckoResult.fromValue(child.session)
        } catch (error: RuntimeException) {
            runCatching { sessionLifecycle.closeAndDiscard(child) }
                .onFailure { cleanupError -> error.addSuppressed(cleanupError) }
            android.util.Log.e("Wren", "Could not create popup tab session", error)
            dispatchToMain {
                if (!isHostUnavailable()) showMessage("Could not open the new tab. Try again.")
            }
            return null
        }
    }
}
