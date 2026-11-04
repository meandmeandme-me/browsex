package com.browsex.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import org.mozilla.geckoview.WebNotification

/** Completes the WebNotification lifecycle when the user dismisses its Android notification. */
class SiteNotificationActionReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BrowseXNotificationBridge.ACTION_SITE_NOTIFICATION_DISMISS) return
        val id = intent.getIntExtra(BrowseXNotificationBridge.EXTRA_NOTIFICATION_ID, 0)
        if (id != 0) NotificationManagerCompat.from(context).cancel(id)
        val notification = intent.getParcelableExtra<WebNotification>(BrowseXNotificationBridge.EXTRA_WEB_NOTIFICATION)
        notification?.dismiss()
    }
}
