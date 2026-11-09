package com.browsex.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/** Separates Android runtime permissions from Gecko's per-origin content permissions. */
internal class BrowseXPermissionDelegate(
    private val activity: MainActivity,
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
        val isSiteNotification =
            perm.permission == GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION
        if (isSiteNotification && !activity.websiteNotificationsEnabled()) {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
            return result
        }
        if (perm.privateMode) {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT)
            return result
        }
        val persistedFeature = when {
            isSiteNotification -> "notifications"
            perm.permission == GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "geolocation"
            else -> null
        }
        activity.lifecycleScope.launch {
            val savedDecision = persistedFeature?.let { readDecision(origin, it) }
            if (!isActiveSession(session) || activity.isFinishing || activity.isDestroyed) {
                result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT)
                return@launch
            }
            when (savedDecision) {
                "allow" -> {
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                    return@launch
                }
                "deny" -> {
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                    return@launch
                }
            }
            if (perm.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW ||
                perm.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
            ) {
                result.complete(perm.value)
                return@launch
            }
            showContentPermissionPrompt(session, origin, persistedFeature, isSiteNotification, result)
        }
        return result
    }

    private fun showContentPermissionPrompt(
        session: GeckoSession,
        origin: String,
        persistedFeature: String?,
        isSiteNotification: Boolean,
        result: GeckoResult<Int>
    ) {
        val permission = when (persistedFeature) {
            "geolocation" -> "your location"
            "notifications" -> "notifications"
            else -> "a browser feature"
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(BrowseXStrings.text(activity, "Allow this website?", "السماح لهذا الموقع؟"))
            .setMessage(activity.getString(R.string.ui_site_permission_request_message, origin, BrowseXStrings.text(activity, permission, if (permission == "your location") "موقعك" else permission)))
            .setPositiveButton(BrowseXStrings.text(activity, "Allow", "سماح")) { _, _ ->
                if (isSiteNotification) {
                    activity.requestSiteNotificationPermission { granted ->
                        activity.lifecycleScope.launch {
                            val allowed = granted && activity.websiteNotificationsEnabled() && isActiveSession(session)
                            if (allowed) persistDecision(origin, "notifications", "allow")
                            result.complete(
                                if (allowed) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                                else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                            )
                        }
                    }
                } else {
                    activity.lifecycleScope.launch {
                        if (persistedFeature != null) persistDecision(origin, persistedFeature, "allow")
                        result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                    }
                }
            }
            .setNegativeButton(BrowseXStrings.text(activity, "Block", "حظر")) { _, _ ->
                activity.lifecycleScope.launch {
                    if (persistedFeature != null) persistDecision(origin, persistedFeature, "deny")
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                }
            }
            .setNeutralButton(BrowseXStrings.text(activity, "Not now", "ليس الآن")) { _, _ ->
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
        if (origin == null || !isActiveSession(session) || (!wantsCamera && !wantsMicrophone)) {
            callback.reject()
            return
        }

        activity.lifecycleScope.launch {
            // Resolve persisted site-level blocks before asking Android for device access.
            val cameraDecision = if (!wantsCamera || privateMode) null else readDecision(origin, "camera")
            val microphoneDecision = if (!wantsMicrophone || privateMode) null else readDecision(origin, "microphone")
            if (!isActiveSession(session) || cameraDecision == "deny" || microphoneDecision == "deny") {
                callback.reject()
                return@launch
            }

            val requiredAndroidPermissions = buildList {
                if (wantsCamera) add(Manifest.permission.CAMERA)
                if (wantsMicrophone) add(Manifest.permission.RECORD_AUDIO)
            }.filter { ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }

            if (requiredAndroidPermissions.isEmpty()) {
                requestSiteMediaDecision(origin, session, video, audio, wantsCamera, wantsMicrophone, callback)
                return@launch
            }

            activity.requestAndroidPermissions(
                session,
                requiredAndroidPermissions.toTypedArray(),
                object : GeckoSession.PermissionDelegate.Callback {
                    override fun grant() {
                        if (!isActiveSession(session)) {
                            callback.reject()
                            return
                        }
                        activity.lifecycleScope.launch {
                            requestSiteMediaDecision(origin, session, video, audio, wantsCamera, wantsMicrophone, callback)
                        }
                    }

                    override fun reject() {
                        callback.reject()
                    }
                }
            )
        }
    }

    private suspend fun requestSiteMediaDecision(
        origin: String,
        session: GeckoSession,
        video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        wantsCamera: Boolean,
        wantsMicrophone: Boolean,
        callback: GeckoSession.PermissionDelegate.MediaCallback
    ) {
        if (!isActiveSession(session) || activity.isFinishing || activity.isDestroyed) {
            callback.reject()
            return
        }
        val camera = if (!wantsCamera) "allow" else if (privateMode) null else readDecision(origin, "camera")
        val microphone = if (!wantsMicrophone) "allow" else if (privateMode) null else readDecision(origin, "microphone")
        if (camera == "deny" || microphone == "deny") {
            callback.reject()
            return
        }
        if ((!wantsCamera || camera == "allow") && (!wantsMicrophone || microphone == "allow")) {
            callback.grant(video?.firstOrNull(), audio?.firstOrNull())
            return
        }
        val features = buildList {
            if (wantsCamera) add(BrowseXStrings.text(activity, "Camera", "الكاميرا"))
            if (wantsMicrophone) add(BrowseXStrings.text(activity, "Microphone", "الميكروفون"))
        }.joinToString(" " + BrowseXStrings.text(activity, "and", "و") + " ")
        val dialog = AlertDialog.Builder(activity)
            .setTitle(BrowseXStrings.text(activity, "Allow device access?", "السماح بالوصول إلى الجهاز؟"))
            .setMessage(activity.getString(R.string.ui_device_permission_request_message, origin, features))
            .setPositiveButton(BrowseXStrings.text(activity, "Allow", "سماح")) { _, _ ->
                activity.lifecycleScope.launch {
                    if (!privateMode && wantsCamera) persistDecision(origin, "camera", "allow")
                    if (!privateMode && wantsMicrophone) persistDecision(origin, "microphone", "allow")
                    if (isActiveSession(session)) callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                    else callback.reject()
                }
            }
            .setNegativeButton("Block") { _, _ ->
                activity.lifecycleScope.launch {
                    if (!privateMode && wantsCamera) persistDecision(origin, "camera", "deny")
                    if (!privateMode && wantsMicrophone) persistDecision(origin, "microphone", "deny")
                    callback.reject()
                }
            }
            .setNeutralButton("Not now") { _, _ -> callback.reject() }
            .create()
        dialogs.add(dialog)
        dialog.setOnCancelListener { callback.reject(); dialogs.remove(dialog) }
        dialog.setOnDismissListener { dialogs.remove(dialog) }
        dialog.show()
    }

    private suspend fun readDecision(origin: String, feature: String): String? =
        runCatching { activity.storageRuntime.awaitReady().sitePermissions.decision(origin, feature) }
            .onFailure { android.util.Log.e("BrowseX", "Could not read saved site permission", it) }
            .getOrNull()

    private suspend fun persistDecision(origin: String, feature: String, decision: String) {
        runCatching { activity.storageRuntime.awaitReady().sitePermissions.setDecision(origin, feature, decision) }
            .onFailure { android.util.Log.e("BrowseX", "Could not save site permission", it) }
    }

    fun cancelPending() {
        dialogs.toList().forEach { if (it.isShowing) it.cancel() }
        dialogs.clear()
    }

    private fun safeOrigin(raw: String): String? =
        BrowserSiteOriginPolicy.canonicalOrigin(raw)
}
