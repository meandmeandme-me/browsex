package com.browsex.app

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Central manager for persisted website-level permission decisions. */
internal class SitePermissionsPage(
    private val activity: MainActivity,
    private val clearAllPermissions: () -> Unit
) {
    private fun t(en: String, ar: String) = BrowseXStrings.text(activity, en, ar)

    fun show() {
        activity.lifecycleScope.launch {
            val entries = runCatching {
                activity.storageRuntime.awaitReady().sitePermissions.all()
            }.onFailure {
                android.util.Log.e("BrowseX", "Could not load saved site permissions", it)
            }.getOrElse {
                Toast.makeText(activity, t("Could not load site permissions", "تعذر تحميل أذونات المواقع"), Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (entries.isEmpty()) {
                AlertDialog.Builder(activity)
                    .setTitle(t("Site permissions", "أذونات المواقع"))
                    .setMessage(t("No saved website permission decisions yet.", "لا توجد قرارات أذونات محفوظة للمواقع حتى الآن."))
                    .setPositiveButton(t("Done", "تم"), null)
                    .show()
                return@launch
            }
            val featureLabels = entries.map { it.feature }.distinct().associateWith(::featureLabel)
            val status = entries.map { entry ->
                val feature = featureLabels[entry.feature] ?: entry.feature
                val decision = when (entry.decision) {
                    "allow" -> t("Allowed", "مسموح")
                    "deny" -> t("Blocked", "محظور")
                    else -> t("Ask every time", "اسأل في كل مرة")
                }
                "${entry.origin}  ·  $feature  ·  $decision"
            }.toTypedArray()
            AlertDialog.Builder(activity)
                .setTitle(t("Saved site permissions", "أذونات المواقع المحفوظة"))
                .setItems(status) { _, index -> showDecision(entries[index].origin, entries[index].feature) }
                .setNeutralButton(t("Reset all", "إعادة ضبط الكل")) { _, _ ->
                    AlertDialog.Builder(activity)
                        .setTitle(t("Reset all site permissions?", "إعادة ضبط جميع أذونات المواقع؟"))
                        .setMessage(t("Every saved choice will return to Ask. Websites may request permission again.", "ستعود جميع القرارات المحفوظة إلى خيار اسأل. قد تطلب المواقع الإذن من جديد."))
                        .setNegativeButton(t("Cancel", "إلغاء"), null)
                        .setPositiveButton(t("Reset", "إعادة ضبط")) { _, _ ->
                            clearAllPermissions()
                        }.show()
                }
                .setPositiveButton(t("Done", "تم"), null)
                .show()
        }
    }

    private fun featureLabel(feature: String): String = when (feature) {
        "camera" -> t("Camera", "الكاميرا")
        "microphone" -> t("Microphone", "الميكروفون")
        "notifications" -> t("Notifications", "الإشعارات")
        "geolocation" -> t("Location", "الموقع")
        else -> feature
    }

    private fun showDecision(origin: String, feature: String) {
        val choices = arrayOf(t("Ask every time", "اسأل في كل مرة"), t("Allow", "سماح"), t("Block", "حظر"))
        AlertDialog.Builder(activity)
            .setTitle("${origin}\n${featureLabel(feature)}")
            .setItems(choices) { _, which ->
                val decision = when (which) { 1 -> "allow"; 2 -> "deny"; else -> "ask" }
                activity.lifecycleScope.launch {
                    val updated = runCatching {
                        activity.storageRuntime.awaitReady().sitePermissions.setDecision(origin, feature, decision)
                    }.getOrDefault(false)
                    Toast.makeText(
                        activity,
                        if (updated) t("Permission updated", "تم تحديث الإذن") else t("Could not update permission", "تعذر تحديث الإذن"),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (updated && !activity.isFinishing && !activity.isDestroyed) show()
                }
            }
            .setNegativeButton(t("Cancel", "إلغاء"), null)
            .show()
    }
}
