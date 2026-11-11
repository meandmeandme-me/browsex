package com.browsex.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.browsex.app.storage.SitePermissionDecisionEntity
import org.mozilla.geckoview.StorageController

internal data class BrowseXPrivacySummary(
    val uBlockStatus: String,
    val regularTrackingProtection: Boolean,
    val privateTrackingProtection: Boolean
)

@Composable
internal fun BrowseXPrivacySheet(
    summary: BrowseXPrivacySummary,
    onDismiss: () -> Unit,
    onManageProtection: () -> Unit,
    onClearData: (Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en) = BrowseXStrings.text(context, en, ar)
    var clearHistory by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var clearPermissions by remember { mutableStateOf(false) }
    var clearAuth by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                Text(
                    text = t("Privacy center", "مركز الخصوصية"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = t("Protection status", "حالة الحماية"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp)
                )
                Text(
                    text = summary.uBlockStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = t("Built-in tracking protection — regular tabs", "حماية التتبع المدمجة — التبويبات العادية") +
                        ": " + t(if (summary.regularTrackingProtection) "On" else "Off", if (summary.regularTrackingProtection) "مفعّلة" else "متوقفة"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = t("Built-in tracking protection — private tabs", "حماية التتبع المدمجة — التبويبات الخاصة") +
                        ": " + t(if (summary.privateTrackingProtection) "On" else "Off", if (summary.privateTrackingProtection) "مفعّلة" else "متوقفة"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = t(
                        "Blocking totals are not shown because reliable per-page counts are unavailable.",
                        "لا نعرض أرقام الحظر لأن الإحصاءات الموثوقة لكل صفحة غير متاحة."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp)
                )
                TextButton(onClick = onManageProtection, modifier = Modifier.align(Alignment.Start)) {
                    Text(t("Manage protection", "إدارة الحماية"))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = t("Choose what to remove", "اختر البيانات التي تريد حذفها"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                PrivacyCheckRow(t("Browsing history", "سجل التصفح"), clearHistory) { clearHistory = it }
                PrivacyCheckRow(t("Cookies and site storage", "ملفات الارتباط وتخزين المواقع"), clearCookies) { clearCookies = it }
                PrivacyCheckRow(t("Cached files", "الملفات المخزنة مؤقتاً"), clearCache) { clearCache = it }
                PrivacyCheckRow(t("Site permissions", "أذونات المواقع"), clearPermissions) { clearPermissions = it }
                PrivacyCheckRow(t("Authentication sessions", "جلسات تسجيل الدخول"), clearAuth) { clearAuth = it }
                Text(
                    text = t(
                        "Clearing cookies or site storage may sign you out of websites. Downloads and bookmarks are not deleted.",
                        "قد يؤدي حذف ملفات الارتباط أو تخزين المواقع إلى تسجيل خروجك. لن تُحذف التنزيلات أو المفضلة."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text(t("Cancel", "إلغاء")) }
                    TextButton(
                        onClick = { confirmClear = true },
                        enabled = clearHistory || clearCookies || clearCache || clearPermissions || clearAuth
                    ) { Text(t("Clear selected", "مسح المحدد")) }
                }
            }
        }
    }

    if (confirmClear) {
        val flags = buildList {
            if (clearCookies) add(StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES)
            if (clearCache) add(StorageController.ClearFlags.ALL_CACHES)
            if (clearPermissions) add(StorageController.ClearFlags.PERMISSIONS)
            if (clearAuth) add(StorageController.ClearFlags.AUTH_SESSIONS)
        }.fold(0L) { total, flag -> total or flag }
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(t("Clear selected data?", "مسح البيانات المحددة؟")) },
            text = { Text(t("This action cannot be undone.", "لا يمكن التراجع عن هذه العملية.")) },
            confirmButton = {
                TextButton(onClick = {
                    onClearData(flags, clearHistory)
                    confirmClear = false
                    onDismiss()
                }) { Text(t("Clear", "مسح")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }
}

@Composable
private fun PrivacyCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun BrowseXSitePermissionsSheet(
    entries: List<SitePermissionDecisionEntity>,
    onDismiss: () -> Unit,
    onSetDecision: (String, String, String) -> Unit,
    onResetAll: () -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en) = BrowseXStrings.text(context, en, ar)
    var confirmReset by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t("Site permissions", "أذونات المواقع"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${entries.size} saved decisions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text(t("Done", "تم")) }
                }
                if (entries.isEmpty()) {
                    Text(
                        t("No saved website permission decisions yet.", "لا توجد قرارات أذونات محفوظة للمواقع حتى الآن."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(entries, key = { "${it.origin}:${it.feature}" }) { entry ->
                            SitePermissionRow(
                                entry = entry,
                                onSetDecision = { decision ->
                                    onSetDecision(entry.origin, entry.feature, decision)
                                }
                            )
                        }
                    }
                    TextButton(
                        onClick = { confirmReset = true },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(t("Reset all", "إعادة ضبط الكل")) }
                }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(t("Reset all site permissions?", "إعادة ضبط جميع أذونات المواقع؟")) },
            text = { Text(t("Every saved choice will return to Ask. Websites may request permission again.", "ستعود جميع القرارات المحفوظة إلى خيار اسأل. قد تطلب المواقع الإذن من جديد.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onResetAll()
                }) { Text(t("Reset", "إعادة ضبط")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }
}

@Composable
private fun SitePermissionRow(
    entry: SitePermissionDecisionEntity,
    onSetDecision: (String) -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en) = BrowseXStrings.text(context, en, ar)
    var expanded by remember { mutableStateOf(false) }
    val feature = when (entry.feature) {
        "camera" -> t("Camera", "الكاميرا")
        "microphone" -> t("Microphone", "الميكروفون")
        "notifications" -> t("Notifications", "الإشعارات")
        "geolocation" -> t("Location", "الموقع")
        else -> entry.feature
    }
    val decision = when (entry.decision) {
        "allow" -> t("Allowed", "مسموح")
        "deny" -> t("Blocked", "محظور")
        else -> t("Ask every time", "اسأل في كل مرة")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.origin, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$feature · $decision",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Box {
                TextButton(onClick = { expanded = true }) { Text(t("Change", "تغيير")) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(t("Ask every time", "اسأل في كل مرة")) }, onClick = { expanded = false; onSetDecision("ask") })
                    DropdownMenuItem(text = { Text(t("Allow", "سماح")) }, onClick = { expanded = false; onSetDecision("allow") })
                    DropdownMenuItem(text = { Text(t("Block", "حظر")) }, onClick = { expanded = false; onSetDecision("deny") })
                }
            }
        }
    }
}
