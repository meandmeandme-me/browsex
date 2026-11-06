package com.browsex.app

import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebResponse

/** App-owned events emitted by GeckoSession delegates. */
internal interface BrowserSessionDelegateCallbacks {
    fun onPageStart(tab: BrowserTab)
    fun onProgressChange(tab: BrowserTab, progress: Int)
    fun onPageStop(tab: BrowserTab, success: Boolean)
    fun onLocationChange(
        tab: BrowserTab,
        url: String?,
        permissions: List<GeckoSession.PermissionDelegate.ContentPermission>
    )
    fun onCanGoBack(tab: BrowserTab, canGoBack: Boolean)
    fun onCanGoForward(tab: BrowserTab, canGoForward: Boolean)
    fun onNewSession(parent: BrowserTab, uri: String): GeckoResult<GeckoSession>?
    fun onTitleChange(tab: BrowserTab, title: String?)
    fun onFullscreen(tab: BrowserTab, enabled: Boolean)
    fun onCloseRequest(tab: BrowserTab)
    fun onExternalResponse(response: WebResponse)
    fun onContentProcessCrash(tab: BrowserTab, session: GeckoSession)
}

/**
 * Adapts GeckoView callbacks into BrowseX-owned events.
 * The adapter has no direct Activity, widget or persistence API dependency; the host owns side effects.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalGeckoViewApi::class])
internal class BrowserSessionDelegateFactory(
    private val callbacks: BrowserSessionDelegateCallbacks
) {
    fun attach(tab: BrowserTab) {
        val session = tab.session
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) =
                callbacks.onPageStart(tab)

            override fun onProgressChange(session: GeckoSession, progressValue: Int) =
                callbacks.onProgressChange(tab, progressValue)

            override fun onPageStop(session: GeckoSession, success: Boolean) =
                callbacks.onPageStop(tab, success)
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) = callbacks.onLocationChange(tab, url, perms.toList())

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) =
                callbacks.onCanGoBack(tab, canGoBack)

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) =
                callbacks.onCanGoForward(tab, canGoForward)

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? =
                callbacks.onNewSession(tab, uri)
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) =
                callbacks.onTitleChange(tab, title)

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) =
                callbacks.onFullscreen(tab, fullScreen)

            override fun onCloseRequest(session: GeckoSession) =
                callbacks.onCloseRequest(tab)

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) =
                callbacks.onExternalResponse(response)

            override fun onCrash(session: GeckoSession) =
                callbacks.onContentProcessCrash(tab, session)
        }
    }
}
