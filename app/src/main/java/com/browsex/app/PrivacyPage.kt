package com.browsex.app

import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.mozilla.geckoview.StorageController

/** User-controlled privacy status and explicit clearing of GeckoView browsing data. */
internal class PrivacyPage(
    private val activity: MainActivity,
    private val clearData: (flags: Long, clearHistory: Boolean) -> Unit,
    private val manageProtection: () -> Unit
) {
    private val isTvRemote get() = WrenAdaptiveUi.isTelevision(activity)
    private fun dp(value: Int) = WrenAdaptiveUi.dp(activity, value)
    private fun t(en: String, ar: String): String =
        if (BrowseXColors.isArabic(activity.currentBrowserSettings)) ar else en

    fun show() {
        val settings = activity.currentBrowserSettings
        val labels = arrayOf(
            t("Browsing history", "سجل التصفح"),
            t("Cookies and site storage", "ملفات الارتباط وتخزين المواقع"),
            t("Cached files", "الملفات المخزنة مؤقتاً"),
            t("Site permissions", "أذونات المواقع"),
            t("Authentication sessions", "جلسات تسجيل الدخول")
        )
        val selected = booleanArrayOf(true, true, true, false, false)
        val geckoRegular = !settings.adBlockEnabled
        val geckoPrivate = !settings.adBlockEnabled || !settings.adBlockPrivateEnabled
        val uBlockStatus = when {
            !settings.adBlockInstalled -> t("uBlock Origin: not installed", "uBlock Origin: غير مثبت")
            settings.adBlockEnabled -> t("uBlock Origin: enabled", "uBlock Origin: مفعّل")
            else -> t("uBlock Origin: installed, currently off", "uBlock Origin: مثبت لكنه متوقف")
        }
        val regularStatus = if (geckoRegular) t("On", "مفعّلة") else t("Off", "متوقفة")
        val privateStatus = if (geckoPrivate) t("On", "مفعّلة") else t("Off", "متوقفة")
        val protectionSummary = listOf(
            t("Protection status", "حالة الحماية"),
            uBlockStatus,
            t(
                "Built-in tracking protection — regular tabs",
                "حماية التتبع المدمجة — التبويبات العادية"
            ) + ": " + regularStatus,
            t(
                "Built-in tracking protection — private tabs",
                "حماية التتبع المدمجة — التبويبات الخاصة"
            ) + ": " + privateStatus,
            t(
                "Blocking totals are not reported here because reliable per-page counts are not available.",
                "لا نعرض أرقامًا للحظر لأن إحصاءات موثوقة لكل صفحة غير متاحة."
            )
        ).joinToString("\n")

        val dialog = AlertDialog.Builder(activity)
            .setTitle(t("Privacy center", "مركز الخصوصية"))
            .setMessage(
                "$protectionSummary\n\n" + t(
                    "Choose what to remove. Clearing cookies or site storage may sign you out of websites. Downloads and bookmarks are not deleted.",
                    "اختر البيانات التي تريد حذفها. قد يؤدي حذف ملفات الارتباط أو تخزين المواقع إلى تسجيل خروجك من المواقع. لن تُحذف التنزيلات أو المفضلة."
                )
            )
            .setMultiChoiceItems(labels, selected) { _, which, checked -> selected[which] = checked }
            .setNeutralButton(t("Manage protection", "إدارة الحماية")) { _, _ -> manageProtection() }
            .setNegativeButton(t("Cancel", "إلغاء"), null)
            .setPositiveButton(t("Clear selected", "مسح المحدد")) { _, _ ->
                val clearHistory = selected[0]
                var flags = 0L
                if (selected[1]) {
                    flags = flags or StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES
                }
                if (selected[2]) flags = flags or StorageController.ClearFlags.ALL_CACHES
                if (selected[3]) flags = flags or StorageController.ClearFlags.PERMISSIONS
                if (selected[4]) flags = flags or StorageController.ClearFlags.AUTH_SESSIONS
                if (!clearHistory && flags == 0L) {
                    Toast.makeText(activity, t("Select at least one data type", "اختر نوعاً واحداً على الأقل"), Toast.LENGTH_SHORT).show()
                } else {
                    clearData(flags, clearHistory)
                }
            }
            .create()
        dialog.setOnShowListener {
            if (isTvRemote) {
                val width = (activity.resources.displayMetrics.widthPixels - dp(96)).coerceAtLeast(dp(560))
                dialog.window?.setLayout(minOf(dp(980), width), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                dialog.findViewById<TextView>(android.R.id.message)?.textSize = 18f
                listOf(
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL),
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ).forEach { button ->
                    button.minHeight = dp(60)
                    button.textSize = 17f
                }
                val list = dialog.listView
                for (index in 0 until list.childCount) {
                    val row = list.getChildAt(index)
                    row.minimumHeight = dp(68)
                    row.findViewById<TextView>(android.R.id.text1)?.textSize = 18f
                    row.isFocusable = true
                    row.isFocusableInTouchMode = false
                }
            }
        }
        dialog.show()
        WrenDialogStyle.apply(dialog, activity, settings)
    }
}
