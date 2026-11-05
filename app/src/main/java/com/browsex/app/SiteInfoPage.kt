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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession

/** Site identity, live GeckoView content permission controls and per-origin media decisions. */
class SiteInfoPage(
    private val activity: MainActivity,
    private val url: String,
    private val title: String,
    private val permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
    private val store: BrowserStore,
    private val onPermissionChange: (GeckoSession.PermissionDelegate.ContentPermission, Int) -> Unit,
    private val onResetPermissions: () -> Unit,
    private val onClearSiteData: () -> Unit
) {
    private val colors get() = BrowseXColors.resolve(activity, store)
    private val localPermissionValues = mutableMapOf<Pair<Int, String>, Int>()
    private var activePageDialog: AlertDialog? = null
    private fun t(en: String, ar: String) = BrowseXStrings.text(activity, en, ar)
    private fun valueOf(permission: GeckoSession.PermissionDelegate.ContentPermission): Int =
        localPermissionValues[permission.permission to permission.uri] ?: permission.value
    private val density = activity.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    fun show() {
        activity.lifecycleScope.launch {
            val decisions = runCatching {
                activity.storageRuntime.awaitReady().sitePermissions.all()
                    .filter { it.origin == originForStore() }
                    .associate { it.feature to it.decision }
            }.onFailure {
                android.util.Log.e("BrowseX", "Could not load site permission decisions", it)
            }.getOrDefault(emptyMap())
            showWithSiteDecisions(decisions)
        }
    }

    private fun showWithSiteDecisions(siteDecisions: Map<String, String>) {
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
            setTextColor(colors.ink)
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
            setTextColor(colors.muted)
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
        val liveStoredFeatures = unique.mapNotNull { storedFeatureForPermission(it.permission) }.toSet()
        if (unique.isEmpty()) {
            root.addView(TextView(activity).apply {
                text = t("No site permissions have been requested on this page.", "لم يطلب هذا الموقع أي أذونات حتى الآن.")
                textSize = 13f
                setTextColor(colors.muted)
                setPadding(dp(12), dp(14), dp(12), dp(14))
                background = cardBackground()
            })
        } else {
            unique.forEach { permission ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(11), dp(12), dp(11))
                    minimumHeight = dp(54)
                    background = cardBackground()
                    isClickable = true
                    isFocusable = true
                }
                val name = permissionNames[permission.permission] ?: t("Website permission", "إذن الموقع")
                row.addView(TextView(activity).apply {
                    text = name
                    textSize = 14f
                    setTextColor(colors.ink)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val storedDecision = storedFeatureForPermission(permission.permission)?.let(siteDecisions::get)
                val effectiveValue = localPermissionValues[permission.permission to permission.uri]
                    ?: when (storedDecision) {
                        "allow" -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                        "deny" -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                        "ask" -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT
                        else -> permission.value
                    }
                val status = when (effectiveValue) {
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW -> t("Allowed", "مسموح")
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY -> t("Blocked", "محظور")
                    else -> t("Ask", "اسأل في كل مرة")
                }
                row.addView(TextView(activity).apply {
                    text = "$status  ›"
                    textSize = 12f
                    setTextColor(when (effectiveValue) {
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW -> colors.success
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY -> colors.danger
                        else -> colors.muted
                    })
                })
                row.contentDescription = t("Change ", "تغيير إذن ") + name + ", " + status
                row.setOnClickListener { showPermissionChoices(permission, name, siteDecisions) }
                root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
            }
        }
        listOf(
            "geolocation" to t("Location", "الموقع"),
            "notifications" to t("Notifications", "الإشعارات")
        ).filterNot { (feature, _) -> feature in liveStoredFeatures }
            .forEach { (feature, name) -> addStoredPermissionRow(root, feature, name, siteDecisions) }
        listOf("camera", "microphone").forEach { feature ->
            val decision = siteDecisions[feature]
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
            row.setOnClickListener { showMediaPermissionChoices(feature, name, siteDecisions) }
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
        val reset = TextView(activity).apply {
            text = t("Reset site permissions", "إعادة ضبط أذونات الموقع")
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(colors.danger)
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = cardBackground()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                AlertDialog.Builder(activity)
                    .setTitle(t("Reset permissions?", "إعادة ضبط الأذونات؟"))
                    .setMessage(t(
                        "This returns revocable site permissions to Ask. Persistent storage grants cannot be revoked by GeckoView.",
                        "سيعيد هذا الأذونات القابلة للإلغاء إلى حالة السؤال. لا يمكن إلغاء إذن التخزين الدائم بواسطة GeckoView."
                    ))
                    .setNegativeButton(t("Cancel", "إلغاء"), null)
                    .setPositiveButton(t("Reset", "إعادة ضبط")) { _, _ ->
                        onResetPermissions()
                        activePageDialog?.dismiss()
                    }
                    .show()
            }
        }
        root.addView(reset, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val clearSiteData = TextView(activity).apply {
            text = t("Clear this site's data", "مسح بيانات هذا الموقع")
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(colors.danger)
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = cardBackground()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                AlertDialog.Builder(activity)
                    .setTitle(t("Clear site data?", "مسح بيانات الموقع؟"))
                    .setMessage(t(
                        "This removes cookies, site storage, cache and permissions for this host. It may sign you out. Data owned by other third-party hosts is not included.",
                        "سيحذف هذا ملفات الارتباط وتخزين الموقع والذاكرة المؤقتة والأذونات لهذا المضيف. قد يؤدي ذلك إلى تسجيل خروجك. لا يشمل بيانات مضيفي الجهات الخارجية."
                    ))
                    .setNegativeButton(t("Cancel", "إلغاء"), null)
                    .setPositiveButton(t("Clear data", "مسح البيانات")) { _, _ ->
                        onClearSiteData()
                        activePageDialog?.dismiss()
                    }
                    .show()
            }
        }
        root.addView(clearSiteData, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

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
        name: String,
        siteDecisions: Map<String, String>
    ) {
        val labels = arrayOf(t("Ask every time", "اسأل في كل مرة"), t("Allow", "سماح"), t("Block", "حظر"))
        val values = intArrayOf(
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT,
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW,
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        )
        val storedDecision = storedFeatureForPermission(permission.permission)?.let(siteDecisions::get)
        val effectiveValue = localPermissionValues[permission.permission to permission.uri]
            ?: when (storedDecision) {
                "allow" -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                "deny" -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                "ask" -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT
                else -> permission.value
            }
        val selected = values.indexOf(effectiveValue).coerceAtLeast(0)
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
                val storedFeature = storedFeatureForPermission(permission.permission)
                dialog.dismiss()
                activePageDialog?.dismiss()
                if (storedFeature == null) {
                    show()
                } else {
                    activity.lifecycleScope.launch {
                        runCatching {
                            activity.storageRuntime.awaitReady().sitePermissions.setDecision(
                                originForStore(), storedFeature, arrayOf("ask", "allow", "deny")[index]
                            )
                        }.onFailure {
                            android.util.Log.e("BrowseX", "Could not update saved site permission", it)
                        }
                        show()
                    }
                }
            }
            .setNegativeButton(t("Cancel", "إلغاء"), null)
            .show()
    }

    private fun addStoredPermissionRow(
        root: LinearLayout,
        feature: String,
        name: String,
        siteDecisions: Map<String, String>
    ) {
        val decision = siteDecisions[feature]
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = cardBackground()
            minimumHeight = dp(54)
            isClickable = true
            isFocusable = true
        }
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
        row.setOnClickListener { showMediaPermissionChoices(feature, name, siteDecisions) }
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
    }

    private fun storedFeatureForPermission(permission: Int): String? = when (permission) {
        GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "geolocation"
        GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "notifications"
        else -> null
    }

    private fun showMediaPermissionChoices(feature: String, name: String, siteDecisions: Map<String, String>) {
        val labels = arrayOf(t("Ask every time", "اسأل في كل مرة"), t("Allow", "سماح"), t("Block", "حظر"))
        val selected = when (siteDecisions[feature]) {
            "allow" -> 1
            "deny" -> 2
            else -> 0
        }
        AlertDialog.Builder(activity)
            .setTitle(name)
            .setSingleChoiceItems(labels, selected) { dialog, index ->
                activity.lifecycleScope.launch {
                    runCatching {
                        activity.storageRuntime.awaitReady().sitePermissions.setDecision(
                            originForStore(), feature, arrayOf("ask", "allow", "deny")[index]
                        )
                    }.onFailure {
                        android.util.Log.e("BrowseX", "Could not update site permission", it)
                    }
                    dialog.dismiss()
                    activePageDialog?.dismiss()
                    show()
                }
            }
            .setNegativeButton(t("Cancel", "إلغاء"), null)
            .show()
    }

    private fun originForStore(): String {
        val uri = android.net.Uri.parse(url)
        return android.net.Uri.Builder().scheme(uri.scheme).encodedAuthority(uri.encodedAuthority).build().toString()
    }

    private fun cardBackground() = GradientDrawable().apply {
        setColor(this@SiteInfoPage.colors.card)
        cornerRadius = dp(13).toFloat()
    }
}
