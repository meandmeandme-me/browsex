package com.wren.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import org.mozilla.geckoview.GeckoSession

internal data class WrenSiteInfoState(
    val url: String,
    val title: String,
    val privateMode: Boolean,
    val permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
    val storedDecisions: Map<String, String>
)

@Composable
internal fun WrenSiteInfoScreen(
    state: WrenSiteInfoState,
    onDismiss: () -> Unit,
    onPermissionChange: (GeckoSession.PermissionDelegate.ContentPermission, Int) -> Unit,
    onResetPermissions: () -> Unit,
    onClearSiteData: () -> Unit,
    onMediaPermissionChange: (String, String) -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en) = WrenStrings.text(context, en, ar)
    val uri = remember(state.url) { android.net.Uri.parse(state.url) }
    val host = uri.host.orEmpty()
    val secure = uri.scheme.equals("https", true)
    var permissionTarget by remember { mutableStateOf<GeckoSession.PermissionDelegate.ContentPermission?>(null) }
    var permissionTargetName by remember { mutableStateOf("") }
    var mediaTarget by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val uniquePermissions = state.permissions.distinctBy { it.permission to it.uri }
    val liveStoredFeatures = uniquePermissions.mapNotNull { storedFeatureForSiteInfoPermission(it.permission) }.toSet()
    val permissionNames = uniquePermissions.map { permission ->
        permission to siteInfoPermissionLabel(permission.permission, t = ::t)
    }

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
            Column(modifier = Modifier.fillMaxSize().padding(17.dp)) {
                Text(
                    t("Site information", "معلومات الموقع").uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    host,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 9.dp)
                )
                Text(
                    if (secure) t("Encrypted HTTPS connection", "اتصال HTTPS مشفّر")
                    else t("Connection is not encrypted", "الاتصال غير مشفّر"),
                    color = if (secure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    state.title.ifBlank { t("Untitled page", "صفحة بلا عنوان") },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 9.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.url,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    t("Site permissions", "أذونات الموقع"),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.privateMode) {
                    Text(
                        t(
                            "Private browsing does not read or save site permission choices.",
                            "التصفح الخاص لا يقرأ اختيارات أذونات المواقع ولا يحفظها."
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (uniquePermissions.isEmpty()) {
                        item {
                            SiteInfoHint(
                                t("No site permissions have been requested on this page.", "لم يطلب هذا الموقع أي أذونات حتى الآن.")
                            )
                        }
                    }
                    items(permissionNames, key = { "${it.first.permission}:${it.first.uri}" }) { (permission, name) ->
                        val feature = storedFeatureForSiteInfoPermission(permission.permission)
                        val stored = feature?.let(state.storedDecisions::get)
                        val current = when (stored) {
                            "allow" -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                            "deny" -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                            "ask" -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT
                            else -> permission.value
                        }
                        SiteInfoPermissionRow(
                            title = name,
                            status = permissionValueLabel(current, t = ::t),
                            enabled = !state.privateMode,
                            onClick = {
                                permissionTarget = permission
                                permissionTargetName = name
                            }
                        )
                    }
                    if (!state.privateMode) {
                        listOf("geolocation" to t("Location", "الموقع"), "notifications" to t("Notifications", "الإشعارات"))
                            .filterNot { it.first in liveStoredFeatures }
                            .forEach { (feature, name) ->
                                item(key = "stored:$feature") {
                                    SiteInfoPermissionRow(
                                        title = name,
                                        status = storedDecisionLabel(state.storedDecisions[feature], t = ::t),
                                        enabled = true,
                                        onClick = { mediaTarget = feature }
                                    )
                                }
                            }
                        listOf("camera" to t("Camera", "الكاميرا"), "microphone" to t("Microphone", "الميكروفون"))
                            .forEach { (feature, name) ->
                                item(key = "media:$feature") {
                                    SiteInfoPermissionRow(
                                        title = name,
                                        status = storedDecisionLabel(state.storedDecisions[feature], t = ::t),
                                        enabled = true,
                                        onClick = { mediaTarget = feature }
                                    )
                                }
                            }
                    }
                }
                Text(
                    if (state.privateMode) {
                        t("Permission choices from regular browsing are not used in this private tab.", "لا تُستخدم اختيارات الأذونات من التصفح العادي في هذا التبويب الخاص.")
                    } else {
                        t(
                            "Tap a permission to allow, block, or ask again. Persistent storage grants cannot be revoked by GeckoView.",
                            "اضغط على الإذن للسماح أو الحظر أو السؤال مجددًا. لا يمكن إلغاء إذن التخزين الدائم بواسطة GeckoView."
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                if (!state.privateMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { confirmReset = true }, modifier = Modifier.weight(1f)) {
                            Text(t("Reset permissions", "إعادة ضبط الأذونات"))
                        }
                        TextButton(onClick = { confirmClear = true }, modifier = Modifier.weight(1f)) {
                            Text(t("Clear site data", "مسح بيانات الموقع"))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(t("Close", "إغلاق")) }
                }
            }
        }
    }

    if (permissionTarget != null) {
        val permission = permissionTarget!!
        val current = permission.value
        val persistentAlreadyGranted =
            permission.permission == GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE &&
                current == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
        val choices = listOf(
            "ask" to GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT,
            "allow" to GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW,
            "block" to GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        )
        AlertDialog(
            onDismissRequest = { permissionTarget = null },
            title = { Text(permissionTargetName) },
            text = {
                Column {
                    if (persistentAlreadyGranted) {
                        Text(
                            t("GeckoView does not support revoking this persistent-storage grant.", "لا يدعم GeckoView إلغاء إذن التخزين الدائم بعد منحه."),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        choices.forEach { (decision, value) ->
                            TextButton(
                                onClick = {
                                    onPermissionChange(permission, value)
                                    permissionTarget = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(t(when (decision) {
                                "ask" -> "Ask every time"
                                "allow" -> "Allow"
                                else -> "Block"
                            }, when (decision) {
                                "ask" -> "اسأل في كل مرة"
                                "allow" -> "سماح"
                                else -> "حظر"
                            })) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { permissionTarget = null }) { Text(t("Done", "تم")) }
            }
        )
    }

    if (mediaTarget != null) {
        val feature = mediaTarget!!
        val choices = listOf("ask" to t("Ask every time", "اسأل في كل مرة"), "allow" to t("Allow", "سماح"), "deny" to t("Block", "حظر"))
        AlertDialog(
            onDismissRequest = { mediaTarget = null },
            title = { Text(t(when (feature) {
                "camera" -> "Camera"
                "microphone" -> "Microphone"
                "notifications" -> "Notifications"
                else -> "Location"
            }, when (feature) {
                "camera" -> "الكاميرا"
                "microphone" -> "الميكروفون"
                "notifications" -> "الإشعارات"
                else -> "الموقع"
            })) },
            text = {
                Column {
                    choices.forEach { (decision, label) ->
                        TextButton(
                            onClick = {
                                onMediaPermissionChange(feature, decision)
                                mediaTarget = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { mediaTarget = null }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(t("Reset permissions?", "إعادة ضبط الأذونات؟")) },
            text = { Text(t("This returns revocable site permissions to Ask. Persistent storage grants cannot be revoked by GeckoView.", "سيعيد هذا الأذونات القابلة للإلغاء إلى حالة السؤال. لا يمكن إلغاء إذن التخزين الدائم بواسطة GeckoView.")) },
            confirmButton = {
                TextButton(onClick = {
                    onResetPermissions()
                    confirmReset = false
                    onDismiss()
                }) { Text(t("Reset", "إعادة ضبط")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(t("Clear this site's data?", "مسح بيانات هذا الموقع؟")) },
            text = { Text(t("This removes cookies, site storage, cache, and permissions for this host. It may sign you out.", "سيحذف هذا ملفات الارتباط وتخزين الموقع والذاكرة المؤقتة والأذونات لهذا المضيف. قد يؤدي ذلك إلى تسجيل خروجك.")) },
            confirmButton = {
                TextButton(onClick = {
                    onClearSiteData()
                    confirmClear = false
                    onDismiss()
                }) { Text(t("Clear data", "مسح البيانات")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }
}

@Composable
private fun SiteInfoPermissionRow(
    title: String,
    status: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SiteInfoHint(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 14.dp)
    )
}

private fun storedFeatureForSiteInfoPermission(permission: Int): String? = when (permission) {
    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "geolocation"
    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "notifications"
    else -> null
}

private fun siteInfoPermissionLabel(
    permission: Int,
    t: (String, String) -> String
): String = when (permission) {
    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> t("Location", "الموقع")
    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> t("Notifications", "الإشعارات")
    GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> t("Persistent storage", "التخزين الدائم")
    GeckoSession.PermissionDelegate.PERMISSION_XR -> t("Extended reality", "الواقع الممتد")
    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> t("Autoplay with sound", "التشغيل التلقائي بالصوت")
    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> t("Silent autoplay", "التشغيل التلقائي الصامت")
    GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> t("Protected media", "الوسائط المحمية")
    GeckoSession.PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> t("Local network", "الشبكة المحلية")
    GeckoSession.PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> t("Local device", "الجهاز المحلي")
    else -> t("Website permission", "إذن الموقع")
}

private fun permissionValueLabel(
    value: Int,
    t: (String, String) -> String
): String = when (value) {
    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW -> t("Allowed", "مسموح")
    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY -> t("Blocked", "محظور")
    else -> t("Ask", "اسأل")
}

private fun storedDecisionLabel(
    decision: String?,
    t: (String, String) -> String
): String = when (decision) {
    "allow" -> t("Allowed", "مسموح")
    "deny" -> t("Blocked", "محظور")
    else -> t("Ask", "اسأل")
}
