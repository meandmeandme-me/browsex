package com.browsex.app

import org.mozilla.geckoview.GeckoSession

/**
 * One serialized Android runtime-permission request originating from a GeckoSession.
 *
 * The Activity owns the Activity Result launcher; this value keeps the originating
 * session and Gecko callback together so a tab switch or teardown can reject it once.
 */
internal class PendingPlatformPermission(
    val session: GeckoSession,
    val permissions: List<String>,
    val callback: GeckoSession.PermissionDelegate.Callback
) {
    var completed: Boolean = false
}
