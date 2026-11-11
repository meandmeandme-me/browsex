package com.browsex.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.mozilla.geckoview.WebNotification
import org.mozilla.geckoview.WebNotificationDelegate
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID

/** Adapts Gecko web notifications to Android's notification tray without leaking private tabs. */
internal class BrowseXNotificationBridge(
    private val activity: MainActivity,
    private val requestRuntimePermission: ((Boolean) -> Unit) -> Unit
) : WebNotificationDelegate {
    private val main = Handler(Looper.getMainLooper())
    private val manager by lazy { NotificationManagerCompat.from(activity) }
    // Gecko can close a notification while Android permission UI or storage IO is pending.
    // Identity tracking prevents a late callback from resurrecting a dismissed notification.
    private val trackedNotifications = Collections.newSetFromMap(
        IdentityHashMap<WebNotification, Boolean>()
    )
    private val notificationTags = IdentityHashMap<WebNotification, String>()
    private val currentNotificationsByTag = mutableMapOf<String, WebNotification>()

    override fun onShowNotification(notification: WebNotification) {
        main.post {
            val tag = notificationTag(notification)
            val previous = currentNotificationsByTag.put(tag, notification)
            if (previous != null && previous !== notification) {
                trackedNotifications.remove(previous)
                notificationTags.remove(previous)
                manager.cancel(tag, NOTIFICATION_ID)
                previous.dismiss()
            }
            trackedNotifications.add(notification)
            val source = notification.source
            if (notification.privateBrowsing || !activity.websiteNotificationsEnabled() ||
                !isSafeWebOrigin(notification.origin) ||
                (source != null && (!isSafeWebOrigin(source) || !isSameWebOrigin(notification.origin, source)))
            ) {
                dismiss(notification)
                return@post
            }
            activity.lifecycleScope.launch {
                val siteAllows = runCatching {
                    BrowserSiteNotificationPolicy.isAllowed(
                        activity.storageRuntime.awaitReady().sitePermissions.decision(
                            notification.origin, "notifications"
                        )
                    )
                }.onFailure {
                    android.util.Log.e("BrowseX", "Could not verify website notification permission", it)
                }.getOrDefault(false)
                if (!trackedNotifications.contains(notification) || !siteAllows ||
                    activity.isFinishing || activity.isDestroyed
                ) {
                    dismiss(notification)
                    return@launch
                }
                requestRuntimePermission { granted ->
                    main.post {
                        if (!trackedNotifications.contains(notification) || !granted ||
                            activity.isFinishing || activity.isDestroyed ||
                            notification.privateBrowsing || !activity.websiteNotificationsEnabled()
                        ) {
                            dismiss(notification)
                        } else {
                            activity.lifecycleScope.launch {
                                val stillAllowed = runCatching {
                                    BrowserSiteNotificationPolicy.isAllowed(
                                        activity.storageRuntime.awaitReady().sitePermissions.decision(
                                            notification.origin, "notifications"
                                        )
                                    )
                                }.getOrDefault(false)
                                if (trackedNotifications.contains(notification) && stillAllowed &&
                                    !activity.isFinishing && !activity.isDestroyed
                                ) {
                                    showInAndroid(notification)
                                } else dismiss(notification)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCloseNotification(notification: WebNotification) {
        main.post {
            trackedNotifications.remove(notification)
            val tag = notificationTags.remove(notification)
            if (tag != null && currentNotificationsByTag[tag] === notification) {
                currentNotificationsByTag.remove(tag)
                manager.cancel(tag, NOTIFICATION_ID)
            }
            notification.dismiss()
        }
    }

    private fun dismiss(notification: WebNotification) {
        trackedNotifications.remove(notification)
        val tag = notificationTags.remove(notification)
        if (tag != null && currentNotificationsByTag[tag] === notification) {
            currentNotificationsByTag.remove(tag)
            manager.cancel(tag, NOTIFICATION_ID)
        }
        notification.dismiss()
    }

    private fun showInAndroid(notification: WebNotification) {
        if (!trackedNotifications.contains(notification)) return
        try {
            ensureChannel()
            val tag = notificationTag(notification)
            val id = NOTIFICATION_ID
            val tapIntent = Intent(activity, MainActivity::class.java).apply {
                action = ACTION_SITE_NOTIFICATION_CLICK
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("browsex-notification://$tag")
                putExtra(EXTRA_WEB_NOTIFICATION, notification)
            }
            val tap = PendingIntent.getActivity(
                activity, id, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val dismissIntent = Intent(activity, SiteNotificationActionReceiver::class.java).apply {
                action = ACTION_SITE_NOTIFICATION_DISMISS
                data = Uri.parse("browsex-dismiss://$tag")
                putExtra(EXTRA_NOTIFICATION_ID, id)
                putExtra(EXTRA_NOTIFICATION_TAG, tag)
                putExtra(EXTRA_WEB_NOTIFICATION, notification)
            }
            val delete = PendingIntent.getBroadcast(
                activity, id, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(activity, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_browsex)
                .setContentTitle(
                    notification.title?.takeIf { it.isNotBlank() }?.take(MAX_TITLE_LENGTH)
                        ?: Uri.parse(notification.origin).host.orEmpty()
                )
                .setContentText(notification.text.orEmpty().take(MAX_TEXT_LENGTH))
                .setStyle(NotificationCompat.BigTextStyle().bigText(notification.text.orEmpty().take(MAX_TEXT_LENGTH)))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(tap)
                .setDeleteIntent(delete)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
            if (notification.silent) builder.setSilent(true)
            manager.notify(tag, id, builder.build())
            notification.show()
        } catch (_: SecurityException) {
            dismiss(notification)
        } catch (_: RuntimeException) {
            dismiss(notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, activity.getString(R.string.website_notification_channel), NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = activity.getString(R.string.website_notification_channel_description) }
        activity.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Stable collision-resistant Android notification tag, scoped to origin and web tag. */
    private fun notificationTag(notification: WebNotification): String {
        notificationTags[notification]?.let { return it }
        // The Web Notifications API replaces notifications only when a non-empty tag
        // is reused by the same origin. Untagged notifications must remain independent.
        val webTag = notification.tag?.takeIf { it.isNotBlank() }
        val tag = if (webTag == null) {
            "browsex-web-${UUID.randomUUID()}"
        } else {
            val source = "${notification.origin}|$webTag".toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256").digest(source)
            "browsex-web-" + digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
        notificationTags[notification] = tag
        return tag
    }

    private fun isSafeWebOrigin(raw: String): Boolean = try {
        val uri = Uri.parse(raw)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()?.trimEnd('.')
        val secureLocalHttp = scheme == "http" && host != null &&
            (host == "localhost" || host == "::1" || host == "127.0.0.1")
        (scheme == "https" || secureLocalHttp) && !host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }

    private fun isSameWebOrigin(first: String, second: String): Boolean = try {
        val left = Uri.parse(first)
        val right = Uri.parse(second)
        val leftScheme = left.scheme?.lowercase()
        val rightScheme = right.scheme?.lowercase()
        val leftHost = left.host?.lowercase()?.trimEnd('.')
        val rightHost = right.host?.lowercase()?.trimEnd('.')
        leftScheme == rightScheme && leftHost != null && leftHost == rightHost &&
            (left.port.takeIf { it >= 0 } ?: if (leftScheme == "https") 443 else 80) ==
            (right.port.takeIf { it >= 0 } ?: if (rightScheme == "https") 443 else 80)
    } catch (_: Exception) { false }

    companion object {
        const val CHANNEL_ID = "browsex.website"
        const val ACTION_SITE_NOTIFICATION_CLICK = "com.browsex.app.SITE_NOTIFICATION_CLICK"
        const val ACTION_SITE_NOTIFICATION_DISMISS = "com.browsex.app.SITE_NOTIFICATION_DISMISS"
        const val EXTRA_WEB_NOTIFICATION = "browsex.extra.WEB_NOTIFICATION"
        const val EXTRA_NOTIFICATION_ID = "browsex.extra.NOTIFICATION_ID"
        const val EXTRA_NOTIFICATION_TAG = "browsex.extra.NOTIFICATION_TAG"
        private const val NOTIFICATION_ID = 0
        private const val MAX_TITLE_LENGTH = 200
        private const val MAX_TEXT_LENGTH = 4_000
    }
}
