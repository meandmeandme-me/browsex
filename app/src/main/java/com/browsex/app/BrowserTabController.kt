package com.browsex.app

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
    private val maxTabs: Int = TabSessionState.MAX_TABS
) {
    val tabs: List<BrowserTab> get() = registry.tabs
    val activeTab: BrowserTab? get() = registry.activeTab

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
        if (!tabs.contains(tab)) return
        val previous = activeTab
        if (previous !== tab) {
            previous?.takeIf { it.sessionOpened }?.let {
                runCatching { it.session.setFocused(false); it.session.setActive(false) }
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
                tab.session.open(runtime())
                tab.sessionOpened = true
                tab.session.loadUri(tab.url.ifBlank { homeUrl() })
            }
            tab.session.setActive(true)
            tab.session.setFocused(true)
            attachSessionToView(tab.session)
            enforceSessionBudget()
            updateChrome()
        } catch (error: RuntimeException) {
            tab.sessionOpened = false
            tab.crashed = true
            android.util.Log.e("BrowseX", "Could not activate tab session", error)
            showMessage("This tab could not be opened. Try again.")
            updateChrome()
        }
    }

    fun closeTab(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        val wasActive = activeTab === tab
        if (!tab.privateMode) recordRecentlyClosed(tab)
        if (wasActive) releaseViewSession()
        sessionLifecycle.closeAndDiscard(tab)
        registry.removeAt(index)
        if (tabs.isEmpty()) createTab(homeUrl())
        else if (wasActive) activateTab(tabs[minOf(index, tabs.lastIndex)])
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
        attachDelegates(child)
        child.session.open(runtime())
        child.sessionOpened = true
        registry.add(child)
        dispatchToMain {
            if (!isHostUnavailable() && tabs.contains(child)) activateTab(child)
            else if (tabs.contains(child)) closeTab(child)
        }
        return GeckoResult.fromValue(child.session)
    }
}
