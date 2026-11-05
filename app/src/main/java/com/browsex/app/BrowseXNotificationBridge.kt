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

/** Adapts Gecko web notifications to Android's notification tray without leaking private tabs. */
internal class BrowseXNotificationBridge(
    private val activity: MainActivity,
    private val requestRuntimePermission: ((Boolean) -> Unit) -> Unit
) : WebNotificationDelegate {
    private val main = Handler(Looper.getMainLooper())
    private val manager by lazy { NotificationManagerCompat.from(activity) }

    override fun onShowNotification(notification: WebNotification) {
        main.post {
            val source = notification.source
            if (notification.privateBrowsing || !isSafeWebOrigin(notification.origin) ||
                (source != null && !isSafeWebOrigin(source))
            ) {
                notification.dismiss()
                return@post
            }
            activity.lifecycleScope.launch {
                val globallyEnabled = websiteNotificationsEnabled()
                if (!globallyEnabled) {
                    notification.dismiss()
                    return@launch
                }
                val siteAllows = runCatching {
                    activity.storageRuntime.awaitReady().sitePermissions.decision(
                        notification.origin, "notifications"
                    ) != "deny"
                }.onFailure {
                    android.util.Log.e("BrowseX", "Could not verify website notification permission", it)
                }.getOrDefault(false)
                if (!siteAllows || activity.isFinishing || activity.isDestroyed) {
                    notification.dismiss()
                    return@launch
                }
                requestRuntimePermission { granted ->
                    main.post {
                        if (!granted || activity.isFinishing || activity.isDestroyed ||
                            notification.privateBrowsing
                        ) {
                            notification.dismiss()
                        } else {
                            activity.lifecycleScope.launch {
                                val globallyEnabled = websiteNotificationsEnabled()
                                val stillAllowed = globallyEnabled && runCatching {
                                    activity.storageRuntime.awaitReady().sitePermissions.decision(
                                        notification.origin, "notifications"
                                    ) != "deny"
                                }.getOrDefault(false)
                                if (stillAllowed && !activity.isFinishing && !activity.isDestroyed) {
                                    showInAndroid(notification)
                                } else notification.dismiss()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCloseNotification(notification: WebNotification) {
        main.post {
            manager.cancel(notificationId(notification))
            notification.dismiss()
        }
    }

    /** DataStore is the single source of truth for the global website-notification switch. */
    private suspend fun websiteNotificationsEnabled(): Boolean =
        runCatching { activity.storageRuntime.settings.snapshot().siteNotificationsEnabled }
            .onFailure { android.util.Log.e("BrowseX", "Could not read website notification setting", it) }
            .getOrDefault(false)

    private fun showInAndroid(notification: WebNotification) {
        try {
            ensureChannel()
            val id = notificationId(notification)
            val tapIntent = Intent(activity, MainActivity::class.java).apply {
                action = ACTION_SITE_NOTIFICATION_CLICK
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("browsex-notification://$id")
                putExtra(EXTRA_WEB_NOTIFICATION, notification)
            }
            val tap = PendingIntent.getActivity(
                activity, id, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val dismissIntent = Intent(activity, SiteNotificationActionReceiver::class.java).apply {
                action = ACTION_SITE_NOTIFICATION_DISMISS
                data = Uri.parse("browsex-dismiss://$id")
                putExtra(EXTRA_NOTIFICATION_ID, id)
                putExtra(EXTRA_WEB_NOTIFICATION, notification)
            }
            val delete = PendingIntent.getBroadcast(
                activity, id, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(activity, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_browsex)
                .setContentTitle(notification.title?.takeIf { it.isNotBlank() } ?: Uri.parse(notification.origin).host.orEmpty())
                .setContentText(notification.text.orEmpty())
                .setStyle(NotificationCompat.BigTextStyle().bigText(notification.text.orEmpty()))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(tap)
                .setDeleteIntent(delete)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
            if (notification.silent) builder.setSilent(true)
            manager.notify(id, builder.build())
            notification.show()
        } catch (_: SecurityException) {
            notification.dismiss()
        } catch (_: RuntimeException) {
            notification.dismiss()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, activity.getString(R.string.website_notification_channel), NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = activity.getString(R.string.website_notification_channel_description) }
        activity.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notificationId(notification: WebNotification): Int =
        ("${notification.origin}|${notification.tag}").hashCode().let { if (it == 0) 1 else it }

    private fun isSafeWebOrigin(raw: String): Boolean = try {
        val uri = Uri.parse(raw)
        (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }

    companion object {
        const val CHANNEL_ID = "browsex.website"
        const val ACTION_SITE_NOTIFICATION_CLICK = "com.browsex.app.SITE_NOTIFICATION_CLICK"
        const val ACTION_SITE_NOTIFICATION_DISMISS = "com.browsex.app.SITE_NOTIFICATION_DISMISS"
        const val EXTRA_WEB_NOTIFICATION = "browsex.extra.WEB_NOTIFICATION"
        const val EXTRA_NOTIFICATION_ID = "browsex.extra.NOTIFICATION_ID"
    }
}
