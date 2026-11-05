package com.browsex.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/** Separates Android runtime permissions from Gecko's per-origin content permissions. */
internal class BrowseXPermissionDelegate(
    private val activity: MainActivity,
    private val store: BrowserStore,
    private val isActiveSession: (GeckoSession) -> Boolean,
    private val privateMode: Boolean = false
) : GeckoSession.PermissionDelegate {
    private val dialogs = linkedSetOf<AlertDialog>()

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        if (!isActiveSession(session) || permissions.isNullOrEmpty()) {
            callback.reject()
            return
        }
        activity.requestAndroidPermissions(session, permissions, callback)
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int> {
        val result = GeckoResult<Int>()
        val origin = safeOrigin(perm.uri)
        if (origin == null || !isActiveSession(session)) {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT)
            return result
        }
        // The global site-notification switch overrides previously persisted site grants.
        if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION &&
            !store.siteNotificationsEnabled) {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
            return result
        }
        if (perm.privateMode) {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT)
            return result
        }
        if (perm.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW ||
            perm.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) {
            result.complete(perm.value)
            return result
        }
        val feature = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "your location"
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "notifications"
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "persistent storage"
            GeckoSession.PermissionDelegate.PERMISSION_XR -> "extended reality features"
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> "autoplay with sound"
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> "silent autoplay"
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "protected media"
            else -> "a browser feature"
        }
        activity.runOnUiThread {
            if (!isActiveSession(session) || activity.isFinishing || activity.isDestroyed) {
                result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT)
                return@runOnUiThread
            }
            val dialog = AlertDialog.Builder(activity)
                .setTitle("Allow $origin?")
                .setMessage("This website wants to use $feature.")
                .setPositiveButton("Allow") { _, _ ->
                    if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION) {
                        activity.requestSiteNotificationPermission { granted ->
                            result.complete(
                                if (granted && store.siteNotificationsEnabled && isActiveSession(session))
                                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                                else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                            )
                        }
                    } else {
                        result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                    }
                }
                .setNegativeButton("Block") { _, _ ->
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                }
                .setNeutralButton("Not now") { _, _ ->
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT)
                }
                .create()
            dialogs.add(dialog)
            dialog.setOnCancelListener {
                result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT)
                dialogs.remove(dialog)
            }
            dialog.setOnDismissListener { dialogs.remove(dialog) }
            dialog.show()
        }
        return result
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback
    ) {
        val origin = safeOrigin(uri)
        val wantsCamera = !video.isNullOrEmpty()
        val wantsMicrophone = !audio.isNullOrEmpty()
        if (origin == null || !isActiveSession(session) || (!wantsCamera && !wantsMicrophone) ||
            (wantsCamera && ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) ||
            (wantsMicrophone && ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        ) {
            callback.reject()
            return
        }
        // Private sessions never read or persist BrowseX's per-origin media decisions.
        val camera = if (!wantsCamera) "allow" else if (privateMode) null else store.siteMediaDecision(origin, "camera")
        val microphone = if (!wantsMicrophone) "allow" else if (privateMode) null else store.siteMediaDecision(origin, "microphone")
        if (camera == "deny" || microphone == "deny") {
            callback.reject()
            return
        }
        if ((!wantsCamera || camera == "allow") && (!wantsMicrophone || microphone == "allow")) {
            callback.grant(video?.firstOrNull(), audio?.firstOrNull())
            return
        }
        activity.runOnUiThread {
            if (!isActiveSession(session) || activity.isFinishing || activity.isDestroyed) {
                callback.reject()
                return@runOnUiThread
            }
            val features = buildList {
                if (wantsCamera) add("camera")
                if (wantsMicrophone) add("microphone")
            }.joinToString(" and ")
            val dialog = AlertDialog.Builder(activity)
                .setTitle("Allow device access?")
                .setMessage("$origin wants to use your $features.")
                .setPositiveButton("Allow") { _, _ ->
                    if (!privateMode && wantsCamera) store.setSiteMediaDecision(origin, "camera", "allow")
                    if (!privateMode && wantsMicrophone) store.setSiteMediaDecision(origin, "microphone", "allow")
                    if (isActiveSession(session)) callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                    else callback.reject()
                }
                .setNegativeButton("Block") { _, _ ->
                    if (!privateMode && wantsCamera) store.setSiteMediaDecision(origin, "camera", "deny")
                    if (!privateMode && wantsMicrophone) store.setSiteMediaDecision(origin, "microphone", "deny")
                    callback.reject()
                }
                .setNeutralButton("Not now") { _, _ -> callback.reject() }
                .create()
            dialogs.add(dialog)
            dialog.setOnCancelListener { callback.reject(); dialogs.remove(dialog) }
            dialog.setOnDismissListener { dialogs.remove(dialog) }
            dialog.show()
        }
    }

    fun cancelPending() {
        dialogs.toList().forEach { if (it.isShowing) it.cancel() }
        dialogs.clear()
    }

    private fun safeOrigin(raw: String): String? =
        BrowserSiteOriginPolicy.canonicalOrigin(raw)
}
