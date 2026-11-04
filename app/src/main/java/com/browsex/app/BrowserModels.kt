package com.browsex.app

import android.content.Context
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/** Owns the single process-wide GeckoRuntime used by every regular and private tab. */
internal object BrowserRuntimeOwner {
    @Volatile private var instance: GeckoRuntime? = null

    fun runtime(context: Context): GeckoRuntime = instance ?: synchronized(this) {
        instance ?: GeckoRuntime.create(context.applicationContext).also { runtime ->
            runtime.settings.contentBlocking.setSafeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            instance = runtime
        }
    }
}

/** Every regular and private session enables GeckoView's built-in tracking protection.
 * This blocks known trackers; it is not represented as a full advertisement blocker. */
internal fun newBrowserSession(privateMode: Boolean): GeckoSession =
    GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .useTrackingProtection(true)
            .build()
    )

data class BrowserTab internal constructor(
    val privateMode: Boolean = false,
    var session: GeckoSession = newBrowserSession(privateMode),
    var sessionOpened: Boolean = false,
    var title: String = "New tab",
    var url: String = "resource://android/assets/home.html",
    var progress: Int = 0,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var sitePermissions: List<GeckoSession.PermissionDelegate.ContentPermission> = emptyList(),
    internal var permissionDelegate: BrowseXPermissionDelegate? = null,
    var recoveryAttempts: Int = 0,
    var crashed: Boolean = false,
    var lastActiveAt: Long = 0L
)

internal data class PendingPlatformPermission(
    val session: GeckoSession,
    val permissions: List<String>,
    val callback: GeckoSession.PermissionDelegate.Callback,
    var completed: Boolean = false
)
