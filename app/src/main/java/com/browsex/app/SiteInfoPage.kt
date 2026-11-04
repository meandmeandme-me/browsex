package com.browsex.app

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.mozilla.geckoview.GeckoSession

/** Read-only site identity and the content permissions GeckoView reports for the current origin. */
class SiteInfoPage(
    private val activity: MainActivity,
    private val url: String,
    private val title: String,
    private val permissions: List<GeckoSession.PermissionDelegate.ContentPermission>
) {
    private val density = activity.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    fun show() {
        val uri = android.net.Uri.parse(url)
        val host = uri.host.orEmpty()
        val secure = uri.scheme.equals("https", true)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(Color.rgb(245, 248, 252))
        }
        root.addView(TextView(activity).apply {
            text = "SITE INFORMATION"
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(83, 105, 123))
        })
        root.addView(TextView(activity).apply {
            text = host
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(19, 43, 64))
            setPadding(0, dp(12), 0, dp(4))
        })
        root.addView(TextView(activity).apply {
            text = if (secure) "✓  HTTPS connection" else "⚠  Not using HTTPS"
            textSize = 13f
            setTextColor(if (secure) Color.rgb(28, 125, 83) else Color.rgb(177, 102, 30))
            setPadding(0, 0, 0, dp(14))
        })
        root.addView(TextView(activity).apply {
            text = title.ifBlank { "Untitled page" }
            textSize = 14f
            setTextColor(Color.rgb(42, 57, 72))
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(activity).apply {
            text = url
            textSize = 11f
            setTextColor(Color.rgb(83, 105, 123))
            setPadding(0, 0, 0, dp(20))
            setTextIsSelectable(true)
        })
        root.addView(TextView(activity).apply {
            text = "SITE PERMISSIONS"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setTextColor(Color.rgb(83, 105, 123))
            setPadding(0, dp(4), 0, dp(8))
        })

        val permissionNames = mapOf(
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION to "Location",
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION to "Notifications",
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE to "Persistent storage",
            GeckoSession.PermissionDelegate.PERMISSION_XR to "Extended reality",
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE to "Autoplay with sound",
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE to "Silent autoplay",
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS to "Protected media",
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS to "Local network",
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS to "Local device"
        )
        val unique = permissions.distinctBy { it.permission to it.uri }
        if (unique.isEmpty()) {
            root.addView(TextView(activity).apply {
                text = "No site permissions have been requested on this page."
                textSize = 13f
                setTextColor(Color.rgb(83, 105, 123))
                setPadding(dp(12), dp(14), dp(12), dp(14))
                background = cardBackground()
            })
        } else {
            unique.forEach { permission ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(11), dp(12), dp(11))
                    background = cardBackground()
                }
                val name = permissionNames[permission.permission] ?: "Website permission"
                row.addView(TextView(activity).apply {
                    text = name
                    textSize = 14f
                    setTextColor(Color.rgb(31, 48, 65))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val status = when (permission.value) {
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW -> "Allowed"
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY -> "Blocked"
                    else -> "Ask"
                }
                row.addView(TextView(activity).apply {
                    text = status
                    textSize = 12f
                    setTextColor(when (permission.value) {
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW -> Color.rgb(28, 125, 83)
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY -> Color.rgb(177, 75, 75)
                        else -> Color.rgb(83, 105, 123)
                    })
                })
                root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
            }
        }
        root.addView(TextView(activity).apply {
            text = "Permission controls are shown as reported by GeckoView. Changing or revoking them will be added in the next permissions pass."
            textSize = 11f
            setTextColor(Color.rgb(103, 111, 119))
            setPadding(0, dp(12), 0, dp(8))
        })
        val scroll = ScrollView(activity).apply { addView(root) }
        val dialog = AlertDialog.Builder(activity).setView(scroll)
            .setNegativeButton("Close", null).create()
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun cardBackground() = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = dp(13).toFloat()
    }
}
