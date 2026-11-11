package com.browsex.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal data class BrowseXUBlockState(
    val installed: Boolean = false,
    val enabled: Boolean = false,
    val allowedInPrivateBrowsing: Boolean = false,
    val siteControlsAvailable: Boolean = false
)

@Composable
internal fun BrowseXUBlockSettingsScreen(
    state: BrowseXUBlockState,
    onDismiss: () -> Unit,
    onInstall: (Boolean) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onSiteControls: () -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en) = BrowseXStrings.text(context, en, ar)
    var allowPrivate by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }

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
            Column(modifier = Modifier.fillMaxSize().padding(19.dp)) {
                Text(
                    "uBlock Origin",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    t(
                        "Open-source content blocker provided by Mozilla Add-ons. It can read and modify website content and requests to block ads and trackers.",
                        "مانع محتوى مفتوح المصدر من إضافات Mozilla. يمكنه قراءة محتوى المواقع وتعديل الطلبات لمنع الإعلانات والمتتبعات."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 9.dp)
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    shape = RoundedCornerShape(17.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(15.dp)) {
                        Text(
                            when {
                                !state.installed -> t("Not installed", "غير مثبت")
                                state.enabled -> t("Protection is active", "الحماية مفعّلة")
                                else -> t("Installed but disabled", "مثبت لكنه متوقف")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (state.installed) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t("Enable uBlock Origin", "تفعيل uBlock Origin"), color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        t("Block ads, trackers, and known anti-blocker scripts", "منع الإعلانات والمتتبعات ونصوص مكافحة الحظر المعروفة"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = state.enabled, onCheckedChange = onToggleEnabled)
                            }
                            Text(
                                if (state.allowedInPrivateBrowsing) t("Private browsing access was approved during installation.", "تمت الموافقة على استخدامه في التصفح الخاص أثناء التثبيت.")
                                else t("Private browsing access was not granted. Reinstall with consent to enable it.", "لم يُمنح إذن التصفح الخاص. أعد التثبيت مع الموافقة لتفعيله."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t("Allow in private browsing", "السماح في التصفح الخاص"), color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        t("Optional. This gives the extension access in private tabs too.", "اختياري. يمنح الإضافة صلاحية العمل في التبويبات الخاصة أيضاً."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = allowPrivate, onCheckedChange = { allowPrivate = it })
                            }
                        }
                    }
                }
                if (state.installed) {
                    TextButton(
                        onClick = onSiteControls,
                        enabled = state.enabled && state.siteControlsAvailable,
                        modifier = Modifier.align(Alignment.Start).padding(top = 8.dp)
                    ) { Text(t("Open site controls", "فتح إعدادات الموقع")) }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text(t("Done", "تم")) }
                    if (state.installed) {
                        TextButton(onClick = { confirmUninstall = true }) {
                            Text(t("Remove", "إزالة"), color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(onClick = { onInstall(allowPrivate) }) {
                            Text(t("Install", "تثبيت"))
                        }
                    }
                }
            }
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text(t("Remove uBlock Origin?", "إزالة uBlock Origin؟")) },
            text = {
                Text(
                    t(
                        "uBlock Origin will be removed. BrowseX will fall back to GeckoView tracking protection.",
                        "ستتم إزالة uBlock Origin وسيعود BrowseX إلى حماية التتبع المدمجة في GeckoView."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    onUninstall()
                }) { Text(t("Remove", "إزالة")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }
}
