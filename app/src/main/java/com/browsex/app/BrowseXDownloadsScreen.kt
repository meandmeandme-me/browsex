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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.browsex.app.storage.DownloadRecordEntity
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

internal data class BrowseXPlatformDownload(
    val id: Long,
    val title: String,
    val status: Int,
    val reason: Int,
    val totalBytes: Long,
    val bytesDownloaded: Long,
    val modifiedAt: Long
)

internal data class BrowseXDownloadsSnapshot(
    val streamRecords: List<DownloadRecordEntity>,
    val platformRecords: List<BrowseXPlatformDownload>,
    val missingPlatformIds: Set<Long>,
    val querySucceeded: Boolean
) {
    val hasActiveDownloads: Boolean
        get() = streamRecords.any { it.status == "queued" || it.status == "running" } ||
            platformRecords.any {
                it.status == android.app.DownloadManager.STATUS_PENDING ||
                    it.status == android.app.DownloadManager.STATUS_RUNNING ||
                    it.status == android.app.DownloadManager.STATUS_PAUSED
            }
}

private enum class DownloadFilter { ALL, ACTIVE, COMPLETED, ISSUES }

private data class DownloadConfirmation(
    val title: String,
    val message: String,
    val confirm: String,
    val action: () -> Unit
)

@Composable
internal fun BrowseXDownloadsScreen(
    initialSnapshot: BrowseXDownloadsSnapshot,
    onRefresh: suspend () -> BrowseXDownloadsSnapshot,
    onOpenStream: (DownloadRecordEntity) -> Unit,
    onShareStream: (DownloadRecordEntity) -> Unit,
    onCancelStream: (DownloadRecordEntity) -> Unit,
    onRemoveStreamRecord: (DownloadRecordEntity) -> Unit,
    onDeleteStreamFile: (DownloadRecordEntity) -> Unit,
    onOpenPlatform: (BrowseXPlatformDownload) -> Unit,
    onSharePlatform: (BrowseXPlatformDownload) -> Unit,
    onManagePlatform: (BrowseXPlatformDownload) -> Unit,
    onDeletePlatform: (BrowseXPlatformDownload) -> Unit,
    onRemoveMissingPlatform: (Long) -> Unit,
    text: (String, String) -> String
) {
    var snapshot by remember { mutableStateOf(initialSnapshot) }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(DownloadFilter.ALL) }
    var confirmation by remember { mutableStateOf<DownloadConfirmation?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            if (snapshot.hasActiveDownloads) {
                delay(1400L)
                runCatching { onRefresh() }.onSuccess { snapshot = it }
            } else {
                delay(5000L)
            }
        }
    }

    val normalizedQuery = BrowserTextNormalizer.normalize(search.take(256))
    val filteredStreams = snapshot.streamRecords.filter { record ->
        val matchesQuery = normalizedQuery.isBlank() ||
            BrowserTextNormalizer.normalize(record.displayName).contains(normalizedQuery)
        val matchesFilter = when (filter) {
            DownloadFilter.ALL -> true
            DownloadFilter.ACTIVE -> record.status == "queued" || record.status == "running"
            DownloadFilter.COMPLETED -> record.status !in setOf("queued", "running", "failed", "cancelled")
            DownloadFilter.ISSUES -> record.status == "failed" || record.status == "cancelled"
        }
        matchesQuery && matchesFilter
    }
    val filteredPlatform = snapshot.platformRecords.filter { item ->
        val matchesQuery = normalizedQuery.isBlank() ||
            BrowserTextNormalizer.normalize(item.title).contains(normalizedQuery)
        val active = item.status == android.app.DownloadManager.STATUS_PENDING ||
            item.status == android.app.DownloadManager.STATUS_RUNNING ||
            item.status == android.app.DownloadManager.STATUS_PAUSED
        val completed = item.status == android.app.DownloadManager.STATUS_SUCCESSFUL
        val matchesFilter = when (filter) {
            DownloadFilter.ALL -> true
            DownloadFilter.ACTIVE -> active
            DownloadFilter.COMPLETED -> completed
            DownloadFilter.ISSUES -> !active && !completed
        }
        matchesQuery && matchesFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text("Downloads", "التنزيلات"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = text("Your BrowseX download library", "مكتبة تنزيلات BrowseX"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { },
                enabled = false,
                modifier = Modifier.size(1.dp)
            ) { }
            Button(
                onClick = {
                    // The refresh coroutine is also used by the active-download poller.
                }
            ) {
                Text(text("Refresh", "تحديث"))
            }
        }
        Text(
            text = text(
                "BrowseX keeps website-response downloads separate from Android-managed downloads.",
                "يفصل BrowseX بين التنزيلات المحفوظة من استجابة الموقع والتنزيلات التي يديرها أندرويد."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.filterNot(Char::isISOControl).take(256) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text("Search downloads", "البحث في التنزيلات")) },
            shape = RoundedCornerShape(16.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
        ) {
            listOf(
                DownloadFilter.ALL to text("All", "الكل"),
                DownloadFilter.ACTIVE to text("Active", "قيد التنزيل"),
                DownloadFilter.COMPLETED to text("Completed", "مكتملة"),
                DownloadFilter.ISSUES to text("Issues", "مشكلات")
            ).forEach { (value, label) ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = { Text(label) }
                )
            }
        }
        if (!snapshot.querySucceeded) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = text(
                        "Android could not read its managed download list. Your BrowseX download records were kept.",
                        "تعذرت قراءة قائمة التنزيلات التي يديرها أندرويد. احتفظ BrowseX بسجلات تنزيلاته."
                    ),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredStreams, key = { "stream:${it.id}" }) { record ->
                val active = record.status == "queued" || record.status == "running"
                val failed = record.status == "failed"
                val cancelled = record.status == "cancelled"
                val completed = !active && !failed && !cancelled
                val percent = if (record.totalBytes > 0L) {
                    ((record.bytesWritten.coerceAtMost(record.totalBytes) * 100L) / record.totalBytes).toInt().coerceIn(0, 100)
                } else null
                val date = if (record.createdAt > 0L) {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.createdAt))
                } else ""
                val state = when {
                    record.status == "queued" -> text("Waiting in queue", "بانتظار دوره")
                    active -> text("Downloading", "جارٍ التنزيل")
                    failed -> text("Failed", "فشل")
                    cancelled -> text("Cancelled", "تم الإلغاء")
                    else -> text("Downloaded", "تم التنزيل")
                }
                DownloadCard(
                    title = record.displayName,
                    subtitle = buildString {
                        append(state)
                        if (record.error.isNotBlank()) append(" · ").append(record.error)
                        if (active || completed) {
                            append(" · ").append(record.bytesWritten / 1024).append(" KB")
                            record.totalBytes.takeIf { it > 0L }?.let { append(" / ").append(it / 1024).append(" KB") }
                            percent?.let { append(" · ").append(it).append('%') }
                        }
                        if (date.isNotBlank()) append(" · ").append(date)
                    },
                    progress = percent?.div(100f),
                    indeterminate = active && percent == null,
                    stateColor = when {
                        failed -> MaterialTheme.colorScheme.error
                        active -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    actions = {
                        if (active) {
                            TextButton(onClick = {
                                confirmation = DownloadConfirmation(
                                    text("Cancel download", "إلغاء التنزيل"),
                                    text("Cancel this download? Its incomplete file will be discarded.", "إلغاء هذا التنزيل؟ سيتم التخلص من الملف غير المكتمل."),
                                    text("Cancel", "إلغاء")
                                ) { onCancelStream(record) }
                            }) { Text(text("Cancel", "إلغاء")) }
                        } else if (completed) {
                            TextButton(onClick = { onOpenStream(record) }) { Text(text("Open", "فتح")) }
                            TextButton(onClick = { onShareStream(record) }) { Text(text("Share", "مشاركة")) }
                            TextButton(onClick = {
                                confirmation = DownloadConfirmation(
                                    text("Delete downloaded file", "حذف الملف المنزّل"),
                                    text("Delete this file from your device and remove its record?", "حذف الملف من جهازك وإزالة سجله؟"),
                                    text("Delete", "حذف")
                                ) { onDeleteStreamFile(record) }
                            }) { Text(text("Delete", "حذف")) }
                        } else {
                            TextButton(onClick = { onRemoveStreamRecord(record) }) { Text(text("Remove", "إزالة")) }
                        }
                    }
                )
            }

            items(filteredPlatform, key = { "platform:${it.id}" }) { item ->
                val active = item.status == android.app.DownloadManager.STATUS_PENDING ||
                    item.status == android.app.DownloadManager.STATUS_RUNNING ||
                    item.status == android.app.DownloadManager.STATUS_PAUSED
                val completed = item.status == android.app.DownloadManager.STATUS_SUCCESSFUL
                val statusLabel = when (item.status) {
                    android.app.DownloadManager.STATUS_PENDING -> text("Waiting", "في الانتظار")
                    android.app.DownloadManager.STATUS_RUNNING -> text("Downloading", "جارٍ التنزيل")
                    android.app.DownloadManager.STATUS_PAUSED -> text("Paused", "متوقف مؤقتًا")
                    android.app.DownloadManager.STATUS_SUCCESSFUL -> text("Downloaded", "تم التنزيل")
                    android.app.DownloadManager.STATUS_FAILED -> text("Failed", "فشل")
                    else -> text("Unknown status", "حالة غير معروفة")
                }
                val percent = if (item.totalBytes > 0L) {
                    ((item.bytesDownloaded.coerceAtMost(item.totalBytes) * 100L) / item.totalBytes).toInt().coerceIn(0, 100)
                } else null
                val date = if (item.modifiedAt > 0L) {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.modifiedAt))
                } else ""
                DownloadCard(
                    title = item.title,
                    subtitle = buildString {
                        append(statusLabel)
                        append(" · ").append(item.bytesDownloaded / 1024).append(" KB")
                        if (item.totalBytes > 0L) append(" / ").append(item.totalBytes / 1024).append(" KB")
                        percent?.let { append(" · ").append(it).append('%') }
                        if (date.isNotBlank()) append(" · ").append(date)
                        if (item.reason != 0 && !completed) append(" · ").append(platformReason(item.status, item.reason, text))
                    },
                    progress = percent?.div(100f),
                    indeterminate = active && percent == null,
                    stateColor = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    actions = {
                        if (completed) {
                            TextButton(onClick = { onOpenPlatform(item) }) { Text(text("Open", "فتح")) }
                            TextButton(onClick = { onSharePlatform(item) }) { Text(text("Share", "مشاركة")) }
                            TextButton(onClick = {
                                confirmation = DownloadConfirmation(
                                    text("Delete downloaded file", "حذف الملف المنزّل"),
                                    text("Delete this downloaded file from your device?", "حذف هذا الملف المنزّل من جهازك؟"),
                                    text("Delete", "حذف")
                                ) { onDeletePlatform(item) }
                            }) { Text(text("Delete", "حذف")) }
                        } else {
                            TextButton(onClick = {
                                confirmation = DownloadConfirmation(
                                    text("Manage download", "إدارة التنزيل"),
                                    text("Cancel or remove this Android-managed download?", "إلغاء أو إزالة هذا التنزيل الذي يديره أندرويد؟"),
                                    text("Remove", "إزالة")
                                ) { onManagePlatform(item) }
                            }) { Text(text("Cancel", "إلغاء")) }
                        }
                    }
                )
            }

            items(snapshot.missingPlatformIds.toList(), key = { "missing:$it" }) { id ->
                DownloadCard(
                    title = text("Download no longer exists in Android DownloadManager", "لم يعد التنزيل موجودًا في مدير تنزيلات أندرويد"),
                    subtitle = text("This unavailable entry can be removed from BrowseX.", "يمكن إزالة هذا السجل غير المتاح من BrowseX."),
                    stateColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actions = {
                        TextButton(onClick = { onRemoveMissingPlatform(id) }) { Text(text("Remove", "إزالة")) }
                    }
                )
            }

            if (filteredStreams.isEmpty() && filteredPlatform.isEmpty() && snapshot.missingPlatformIds.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Text("↓", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = when {
                                    normalizedQuery.isNotBlank() -> text("No downloads match your search.", "لا توجد تنزيلات تطابق بحثك.")
                                    filter == DownloadFilter.ACTIVE -> text("No downloads in progress.", "لا توجد تنزيلات قيد التنفيذ.")
                                    filter == DownloadFilter.COMPLETED -> text("No completed downloads yet.", "لا توجد تنزيلات مكتملة بعد.")
                                    filter == DownloadFilter.ISSUES -> text("No downloads need attention.", "لا توجد تنزيلات تحتاج إلى انتباه.")
                                    else -> text("Your downloads will appear here.", "ستظهر تنزيلاتك هنا.")
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    confirmation?.let { request ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(request.title) },
            text = { Text(request.message) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    request.action()
                }) { Text(request.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) { Text(text("Keep", "احتفاظ")) }
            }
        )
    }
}

@Composable
private fun DownloadCard(
    title: String,
    subtitle: String,
    progress: Float? = null,
    indeterminate: Boolean = false,
    stateColor: androidx.compose.ui.graphics.Color,
    actions: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = stateColor,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (indeterminate) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            } else if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) { actions() }
        }
    }
}

private fun platformReason(
    status: Int,
    reason: Int,
    text: (String, String) -> String
): String = when {
    status == android.app.DownloadManager.STATUS_PAUSED -> when (reason) {
        android.app.DownloadManager.PAUSED_QUEUED_FOR_WIFI -> text("Waiting for Wi-Fi", "بانتظار Wi-Fi")
        android.app.DownloadManager.PAUSED_WAITING_FOR_NETWORK -> text("Waiting for network", "بانتظار الشبكة")
        android.app.DownloadManager.PAUSED_WAITING_TO_RETRY -> text("Waiting to retry", "بانتظار إعادة المحاولة")
        else -> text("Paused by Android", "أوقفه أندرويد مؤقتًا")
    }
    status == android.app.DownloadManager.STATUS_FAILED -> when (reason) {
        android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE -> text("Not enough storage", "المساحة غير كافية")
        android.app.DownloadManager.ERROR_DEVICE_NOT_FOUND -> text("Storage unavailable", "وحدة التخزين غير متاحة")
        android.app.DownloadManager.ERROR_FILE_ALREADY_EXISTS -> text("File already exists", "الملف موجود بالفعل")
        android.app.DownloadManager.ERROR_FILE_ERROR -> text("File write error", "خطأ في كتابة الملف")
        android.app.DownloadManager.ERROR_HTTP_DATA_ERROR -> text("Network data error", "خطأ في بيانات الشبكة")
        android.app.DownloadManager.ERROR_TOO_MANY_REDIRECTS -> text("Too many redirects", "عمليات إعادة توجيه كثيرة")
        android.app.DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> text("Unsupported server response", "استجابة الخادم غير مدعومة")
        android.app.DownloadManager.ERROR_CANNOT_RESUME -> text("Cannot resume this download", "لا يمكن استكمال هذا التنزيل")
        else -> text("Android could not finish this download", "تعذّر على أندرويد إكمال التنزيل")
    }
    else -> ""
}
