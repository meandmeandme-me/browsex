package com.browsex.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

internal enum class BrowseXDownloadPrimaryAction {
    CANCEL_STREAM, REMOVE_STREAM_RECORD, OPEN_STREAM,
    CANCEL_PLATFORM, OPEN_PLATFORM, REMOVE_PLATFORM, REMOVE_STALE_PLATFORM
}

internal data class BrowseXDownloadItem(
    val key: String,
    val recordId: String = "",
    val platformId: Long = 0L,
    val displayName: String,
    val mimeType: String = "*/*",
    val uri: String = "",
    val sourceLabel: String,
    val statusLabel: String,
    val detail: String,
    val progressPercent: Int? = null,
    val indeterminate: Boolean = false,
    val isActive: Boolean = false,
    val isCompleted: Boolean = false,
    val canShare: Boolean = false,
    val canDeleteFile: Boolean = false,
    val primaryAction: BrowseXDownloadPrimaryAction,
    val primaryLabel: String
)

internal data class BrowseXDownloadsState(
    val items: List<BrowseXDownloadItem> = emptyList(),
    val querySucceeded: Boolean = true,
    val hasActiveDownloads: Boolean = false
)

@Composable
internal fun BrowseXDownloadsScreen(
    state: BrowseXDownloadsState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onPrimaryAction: (BrowseXDownloadItem) -> Unit,
    onShare: (BrowseXDownloadItem) -> Unit,
    onDeleteFile: (BrowseXDownloadItem) -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en) = BrowseXStrings.text(context, en, ar)
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var confirmPrimary by remember { mutableStateOf<BrowseXDownloadItem?>(null) }
    var confirmDelete by remember { mutableStateOf<BrowseXDownloadItem?>(null) }
    val needle = BrowserTextNormalizer.normalize(query.trim())
    val filtered = state.items.filter { item ->
        val matchesQuery = needle.isBlank() ||
            BrowserTextNormalizer.normalize(item.displayName).contains(needle)
        val matchesFilter = when (filter) {
            1 -> item.isActive
            2 -> item.isCompleted
            3 -> !item.isActive && !item.isCompleted
            else -> true
        }
        matchesQuery && matchesFilter
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
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t("Downloads", "التنزيلات"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            t("Website response streams and Android-managed downloads", "تنزيلات استجابات المواقع والتنزيلات التي يديرها أندرويد"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = t("Refresh downloads", "تحديث التنزيلات"))
                    }
                    TextButton(onClick = onDismiss) { Text(t("Done", "تم")) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.filterNot(Char::isISOControl).take(256) },
                    modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                    singleLine = true,
                    label = { Text(t("Search downloads by file name", "ابحث في التنزيلات باسم الملف")) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text(t("All", "الكل")) })
                    FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text(t("In progress", "قيد التنزيل")) })
                    FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text(t("Completed", "مكتملة")) })
                    FilterChip(selected = filter == 3, onClick = { filter = 3 }, label = { Text(t("Issues", "مشكلات")) })
                }
                if (!state.querySucceeded) {
                    Text(
                        t(
                            "Android could not read its tracked download list. Refresh or check app access; existing download records were kept.",
                            "تعذرت قراءة قائمة التنزيلات التي يتتبعها أندرويد. حدّث القائمة أو تحقق من صلاحيات التطبيق؛ لم تُحذف السجلات."
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                if (filtered.isEmpty()) {
                    Text(
                        when (filter) {
                            1 -> t("No downloads are in progress.", "لا توجد تنزيلات قيد التنفيذ.")
                            2 -> t("No completed downloads yet.", "لا توجد تنزيلات مكتملة بعد.")
                            3 -> t("No downloads need attention.", "لا توجد تنزيلات تحتاج إلى انتباه.")
                            else -> if (needle.isBlank()) t("No BrowseX downloads yet. Files downloaded from websites appear here.", "لا توجد تنزيلات بعد. ستظهر هنا الملفات التي تنزّلها من المواقع.") else t("No downloads match your search.", "لا توجد تنزيلات تطابق بحثك.")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 30.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.key }) { item ->
                            DownloadItemCard(
                                item = item,
                                onPrimary = {
                                    if (item.primaryAction == BrowseXDownloadPrimaryAction.CANCEL_STREAM ||
                                        item.primaryAction == BrowseXDownloadPrimaryAction.CANCEL_PLATFORM ||
                                        item.primaryAction == BrowseXDownloadPrimaryAction.REMOVE_STREAM_RECORD ||
                                        item.primaryAction == BrowseXDownloadPrimaryAction.REMOVE_PLATFORM ||
                                        item.primaryAction == BrowseXDownloadPrimaryAction.REMOVE_STALE_PLATFORM
                                    ) confirmPrimary = item else onPrimaryAction(item)
                                },
                                onShare = { onShare(item) },
                                onDelete = { confirmDelete = item }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmPrimary != null) {
        val item = confirmPrimary!!
        val isCancel = item.primaryAction == BrowseXDownloadPrimaryAction.CANCEL_STREAM ||
            item.primaryAction == BrowseXDownloadPrimaryAction.CANCEL_PLATFORM
        AlertDialog(
            onDismissRequest = { confirmPrimary = null },
            title = { Text(if (isCancel) t("Cancel download?", "إلغاء التنزيل؟") else t("Remove download record?", "حذف سجل التنزيل؟")) },
            text = {
                Text(
                    if (isCancel) t("The incomplete download will be discarded.", "سيتم تجاهل الملف غير المكتمل.")
                    else t("This removes the record from BrowseX. It does not delete unrelated files.", "سيؤدي هذا إلى إزالة السجل من BrowseX دون حذف ملفات أخرى.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onPrimaryAction(item)
                    confirmPrimary = null
                }) { Text(item.primaryLabel) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPrimary = null }) { Text(t("Keep", "احتفاظ")) }
            }
        )
    }

    if (confirmDelete != null) {
        val item = confirmDelete!!
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(t("Delete downloaded file?", "حذف الملف المنزّل؟")) },
            text = {
                Text(
                    t(
                        "BrowseX will delete the file only if it can verify that the file belongs to this app or to its tracked Android download.",
                        "لن يحذف BrowseX الملف إلا إذا أمكن التحقق من أنه تابع للتطبيق أو لتنزيل أندرويد الذي يتتبعه."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFile(item)
                    confirmDelete = null
                }) { Text(t("Delete file", "حذف الملف")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(t("Keep", "احتفاظ")) }
            }
        )
    }
}

@Composable
private fun DownloadItemCard(
    item: BrowseXDownloadItem,
    onPrimary: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en) = BrowseXStrings.text(context, en, ar)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Text(
                item.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.sourceLabel + " · " + item.statusLabel,
                color = if (item.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 3.dp)
            )
            Text(
                item.detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (item.isActive) {
                if (item.indeterminate) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                } else if (item.progressPercent != null) {
                    LinearProgressIndicator(
                        progress = item.progressPercent / 100f,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.canShare) TextButton(onClick = onShare) { Text(t("Share", "مشاركة")) }
                if (item.canDeleteFile) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = t("Delete file", "حذف الملف"))
                    }
                }
                Button(onClick = onPrimary) { Text(item.primaryLabel) }
            }
        }
    }
}
