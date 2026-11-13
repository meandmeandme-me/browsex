package com.wren.app

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

/**
 * Keeps GeckoView's built-in tracker protection from overlapping with uBlock Origin.
 * Private sessions retain Gecko tracking protection unless the user explicitly allows
 * uBlock Origin to run in private browsing.
 */
internal object BrowserContentProtectionPolicy {
    @Volatile private var uBlockEnabled: Boolean = false
    @Volatile private var uBlockPrivateAllowed: Boolean = false

    fun configure(enabled: Boolean, privateAllowed: Boolean) {
        uBlockEnabled = enabled
        uBlockPrivateAllowed = enabled && privateAllowed
    }

    fun useGeckoTrackingProtection(privateMode: Boolean): Boolean =
        !uBlockEnabled || (privateMode && !uBlockPrivateAllowed)
}

internal fun newBrowserSession(privateMode: Boolean): GeckoSession =
    GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .useTrackingProtection(BrowserContentProtectionPolicy.useGeckoTrackingProtection(privateMode))
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
    internal var permissionDelegate: WrenPermissionDelegate? = null,
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
