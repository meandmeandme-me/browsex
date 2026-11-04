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
    private val permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
    private val store: BrowserStore,
    private val onPermissionChange: (GeckoSession.PermissionDelegate.ContentPermission, Int) -> Unit
) {
    private val colors get() = BrowseXColors.resolve(activity, store)
    private val localPermissionValues = mutableMapOf<Pair<Int, String>, Int>()
    private var activePageDialog: AlertDialog? = null
    private fun t(en: String, ar: String) = if (BrowseXColors.isArabic(store)) ar else en
    private fun valueOf(permission: GeckoSession.PermissionDelegate.ContentPermission): Int =
        localPermissionValues[permission.permission to permission.uri] ?: permission.value
    private val density = activity.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    fun show() {
        val uri = android.net.Uri.parse(url)
        val host = uri.host.orEmpty()
        val secure = uri.scheme.equals("https", true)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(colors.canvas)
            BrowseXColors.applyDirection(this, store)
        }
        root.addView(TextView(activity).apply {
            text = t("SITE INFORMATION", "معلومات الموقع")
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.muted)
        })
        root.addView(TextView(activity).apply {
            text = host
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.ink)
            setPadding(0, dp(12), 0, dp(4))
        })
        root.addView(TextView(activity).apply {
            text = if (secure) t("✓  HTTPS connection", "✓ اتصال HTTPS آمن") else t("⚠  Not using HTTPS", "⚠ الاتصال غير مشفّر")
            textSize = 13f
            setTextColor(if (secure) colors.success else colors.danger)
            setPadding(0, 0, 0, dp(14))
        })
        root.addView(TextView(activity).apply {
            text = title.ifBlank { t("Untitled page", "صفحة بلا عنوان") }
            textSize = 14f
            setTextColor(Color.rgb(42, 57, 72))
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(activity).apply {
            text = url
            textSize = 11f
            setTextColor(colors.muted)
            setPadding(0, 0, 0, dp(20))
            setTextIsSelectable(true)
        })
        root.addView(TextView(activity).apply {
            text = t("SITE PERMISSIONS", "أذونات الموقع")
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setTextColor(Color.rgb(83, 105, 123))
            setPadding(0, dp(4), 0, dp(8))
        })

        val permissionNames = mapOf(
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION to t("Location", "الموقع"),
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION to t("Notifications", "الإشعارات"),
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE to t("Persistent storage", "التخزين الدائم"),
            GeckoSession.PermissionDelegate.PERMISSION_XR to t("Extended reality", "الواقع الممتد"),
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE to t("Autoplay with sound", "التشغيل التلقائي بالصوت"),
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE to t("Silent autoplay", "التشغيل التلقائي الصامت"),
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS to t("Protected media", "الوسائط المحمية"),
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS to "Local network",
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS to "Local device"
        )
        val unique = permissions.distinctBy { it.permission to it.uri }
        if (unique.isEmpty()) {
            root.addView(TextView(activity).apply {
                text = t("No site permissions have been requested on this page.", "لم يطلب هذا الموقع أي أذونات حتى الآن.")
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
                    setTextColor(colors.ink)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val effectiveValue = valueOf(permission)
                val status = when (effectiveValue) {
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW -> t("Allowed", "مسموح")
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY -> t("Blocked", "محظور")
                    else -> t("Ask", "اسأل في كل مرة")
                }
                row.addView(TextView(activity).apply {
                    text = status
                    textSize = 12f
                    setTextColor(when (effectiveValue) {
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW -> colors.success
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY -> colors.danger
                        else -> Color.rgb(83, 105, 123)
                    })
                })
                root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
            }
        }
        listOf("camera", "microphone").forEach { feature ->
            val decision = store.siteMediaDecision(originForStore(), feature)
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = cardBackground()
                minimumHeight = dp(54)
                isClickable = true
                isFocusable = true
            }
            val name = if (feature == "camera") t("Camera", "الكاميرا") else t("Microphone", "الميكروفون")
            row.addView(TextView(activity).apply {
                text = name
                textSize = 14f
                setTextColor(colors.ink)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(activity).apply {
                text = when (decision) {
                    "allow" -> t("Allowed", "مسموح")
                    "deny" -> t("Blocked", "محظور")
                    else -> t("Ask", "اسأل")
                } + "  ›"
                textSize = 12f
                setTextColor(colors.muted)
            })
            row.contentDescription = t("Change ", "تغيير إذن ") + name
            row.setOnClickListener { showMediaPermissionChoices(feature, name) }
            root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
        root.addView(TextView(activity).apply {
            text = t(
                "Tap a permission to allow, block or ask again. Persistent storage cannot be revoked after it is granted.",
                "اضغط على الإذن للسماح أو الحظر أو السؤال مجددًا. لا يمكن إلغاء إذن التخزين الدائم بعد منحه."
            )
            textSize = 11f
            setTextColor(colors.muted)
            setPadding(0, dp(12), 0, dp(8))
        })
        val scroll = ScrollView(activity).apply { addView(root) }
        val dialog = AlertDialog.Builder(activity).setView(scroll)
            .setNegativeButton(t("Close", "إغلاق"), null).create()
        activePageDialog = dialog
        dialog.setOnDismissListener { if (activePageDialog === dialog) activePageDialog = null }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showPermissionChoices(
        permission: GeckoSession.PermissionDelegate.ContentPermission,
        name: String
    ) {
        val labels = arrayOf(t("Ask every time", "اسأل في كل مرة"), t("Allow", "سماح"), t("Block", "حظر"))
        val values = intArrayOf(
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT,
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW,
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        )
        val selected = values.indexOf(valueOf(permission)).coerceAtLeast(0)
        val cannotRevoke = permission.permission ==
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE &&
            valueOf(permission) == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
        if (cannotRevoke) {
            AlertDialog.Builder(activity)
                .setTitle(name)
                .setMessage(t(
                    "GeckoView does not support revoking this persistent-storage grant.",
                    "لا يدعم GeckoView إلغاء إذن التخزين الدائم بعد منحه."
                ))
                .setPositiveButton(t("OK", "حسنًا"), null)
                .show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(name)
            .setSingleChoiceItems(labels, selected) { dialog, index ->
                localPermissionValues[permission.permission to permission.uri] = values[index]
                onPermissionChange(permission, values[index])
                dialog.dismiss()
                activePageDialog?.dismiss()
                show()
            }
            .setNegativeButton(t("Cancel", "إلغاء"), null)
            .show()
    }

    private fun showMediaPermissionChoices(feature: String, name: String) {
        val labels = arrayOf(t("Ask every time", "اسأل في كل مرة"), t("Allow", "سماح"), t("Block", "حظر"))
        val selected = when (store.siteMediaDecision(originForStore(), feature)) {
            "allow" -> 1
            "deny" -> 2
            else -> 0
        }
        AlertDialog.Builder(activity)
            .setTitle(name)
            .setSingleChoiceItems(labels, selected) { dialog, index ->
                store.setSiteMediaDecision(originForStore(), feature, arrayOf("ask", "allow", "deny")[index])
                dialog.dismiss()
                activePageDialog?.dismiss()
                show()
            }
            .setNegativeButton(t("Cancel", "إلغاء"), null)
            .show()
    }

    private fun originForStore(): String {
        val uri = android.net.Uri.parse(url)
        return android.net.Uri.Builder().scheme(uri.scheme).encodedAuthority(uri.encodedAuthority).build().toString()
    }

    private fun cardBackground() = GradientDrawable().apply {
        setColor(colors.card)
        cornerRadius = dp(13).toFloat()
    }
}
