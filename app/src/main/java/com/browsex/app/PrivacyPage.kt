package com.browsex.app

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.mozilla.geckoview.StorageController

/** User-controlled, explicit clearing of GeckoView browsing data and BrowseX history. */
internal class PrivacyPage(
    private val activity: MainActivity,
    private val store: BrowserStore,
    private val clearData: (flags: Long, clearHistory: Boolean) -> Unit
) {
    private fun t(en: String, ar: String): String =
        if (BrowseXColors.isArabic(activity, store)) ar else en

    fun show() {
        val labels = arrayOf(
            t("Browsing history", "سجل التصفح"),
            t("Cookies and site storage", "ملفات الارتباط وتخزين المواقع"),
            t("Cached files", "الملفات المخزنة مؤقتاً"),
            t("Site permissions", "أذونات المواقع"),
            t("Authentication sessions", "جلسات تسجيل الدخول")
        )
        val selected = booleanArrayOf(true, true, true, false, false)
        AlertDialog.Builder(activity)
            .setTitle(t("Privacy center", "مركز الخصوصية"))
            .setMessage(
                t(
                    "Choose what to remove. Clearing cookies or site storage may sign you out of websites. Downloads and bookmarks are not deleted.",
                    "اختر البيانات التي تريد حذفها. قد يؤدي حذف ملفات الارتباط أو تخزين المواقع إلى تسجيل خروجك من المواقع. لن تُحذف التنزيلات أو المفضلة."
                )
            )
            .setMultiChoiceItems(labels, selected) { _, which, checked -> selected[which] = checked }
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
            .show()
    }
}
