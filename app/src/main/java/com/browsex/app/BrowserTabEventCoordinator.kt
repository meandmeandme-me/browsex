package com.browsex.app

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebResponse

/**
 * Converts GeckoSession callbacks into browser-domain state changes and injected host actions.
 *
 * It owns page progress/history/tab event handling but has no Activity, View, persistence-store
 * or GeckoRuntime ownership. The composition root supplies the few platform-facing actions.
 */
internal class BrowserTabEventCoordinator(
    private val isActiveTab: (BrowserTab) -> Boolean,
    private val updateChrome: () -> Unit,
    private val recordHistory: (String, String) -> Unit,
    private val openPopupTab: (BrowserTab) -> GeckoResult<GeckoSession>?,
    private val setFullscreen: (Boolean) -> Unit,
    private val closeTab: (BrowserTab) -> Unit,
    private val enqueueDownload: (WebResponse) -> Unit,
    private val recoverContentProcess: (BrowserTab, GeckoSession) -> Unit
) : BrowserSessionDelegateCallbacks {
    override fun onPageStart(tab: BrowserTab) {
        tab.progress = 1
        if (isActiveTab(tab)) updateChrome()
    }

    override fun onProgressChange(tab: BrowserTab, progress: Int) {
        tab.progress = progress
        if (isActiveTab(tab)) updateChrome()
    }

    override fun onPageStop(tab: BrowserTab, success: Boolean) {
        tab.progress = 100
        if (success) {
            if (!tab.privateMode) {
                val historyTitle = tab.title.takeIf { it.isNotBlank() && it != "New tab" }
                    ?: android.net.Uri.parse(tab.url).host.orEmpty()
                recordHistory(tab.url, historyTitle)
            }
            tab.recoveryAttempts = 0
            tab.crashed = false
        }
        if (isActiveTab(tab)) updateChrome()
    }

    override fun onLocationChange(
        tab: BrowserTab,
        url: String?,
        permissions: List<GeckoSession.PermissionDelegate.ContentPermission>
    ) {
        tab.url = url.orEmpty()
        tab.sitePermissions = permissions.toList()
        if (isActiveTab(tab)) updateChrome()
    }

    override fun onCanGoBack(tab: BrowserTab, canGoBack: Boolean) {
        tab.canGoBack = canGoBack
        if (isActiveTab(tab)) updateChrome()
    }

    override fun onCanGoForward(tab: BrowserTab, canGoForward: Boolean) {
        tab.canGoForward = canGoForward
        if (isActiveTab(tab)) updateChrome()
    }

    override fun onNewSession(parent: BrowserTab, uri: String): GeckoResult<GeckoSession>? =
        openPopupTab(parent)

    override fun onTitleChange(tab: BrowserTab, title: String?) {
        tab.title = title?.takeIf { it.isNotBlank() } ?: "New tab"
        if (isActiveTab(tab)) updateChrome()
    }

    override fun onFullscreen(tab: BrowserTab, enabled: Boolean) {
        if (isActiveTab(tab)) setFullscreen(enabled)
    }

    override fun onCloseRequest(tab: BrowserTab) = closeTab(tab)

    override fun onExternalResponse(response: WebResponse) = enqueueDownload(response)

    override fun onContentProcessCrash(tab: BrowserTab, session: GeckoSession) =
        recoverContentProcess(tab, session)
}
