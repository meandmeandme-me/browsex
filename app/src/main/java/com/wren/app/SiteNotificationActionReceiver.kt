package com.wren.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import org.mozilla.geckoview.WebNotification

/** Completes the WebNotification lifecycle when the user dismisses its Android notification. */
class SiteNotificationActionReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WrenNotificationBridge.ACTION_SITE_NOTIFICATION_DISMISS) return
        val id = intent.getIntExtra(WrenNotificationBridge.EXTRA_NOTIFICATION_ID, 0)
        val tag = intent.getStringExtra(WrenNotificationBridge.EXTRA_NOTIFICATION_TAG)
        if (!tag.isNullOrBlank()) NotificationManagerCompat.from(context).cancel(tag, id)
        val notification = intent.getParcelableExtra<WebNotification>(WrenNotificationBridge.EXTRA_WEB_NOTIFICATION)
        notification?.dismiss()
    }
}
