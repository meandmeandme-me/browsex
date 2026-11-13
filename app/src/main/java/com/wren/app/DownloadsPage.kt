package com.wren.app

import android.app.DownloadManager
import androidx.activity.ComponentDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.wren.app.storage.DownloadRecordEntity
import com.wren.app.storage.RoomDownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Wren Compose download library for both response streams and Android-managed jobs. */
internal class DownloadsPage(
    private val activity: MainActivity,
    private val onCancelStreamDownload: (String) -> Boolean
) {
    private val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private fun t(en: String, ar: String) = WrenStrings.text(activity, en, ar)

    fun show() {
        activity.lifecycleScope.launch {
            val repository = runCatching {
                activity.storageRuntime.awaitReady().downloads
            }.getOrElse { error ->
                android.util.Log.e("Wren", "Could not open Room downloads", error)
                toast("Downloads are temporarily unavailable", "التنزيلات غير متاحة مؤقتًا")
                return@launch
            }
            val initial = runCatching { loadSnapshot(repository) }.getOrElse { error ->
                android.util.Log.e("Wren", "Could not load download library", error)
                toast("Downloads are temporarily unavailable", "التنزيلات غير متاحة مؤقتًا")
                return@launch
            }
            if (activity.isFinishing || activity.isDestroyed) return@launch

            val colors = WrenColors.resolve(activity, activity.currentBrowserSettings)
            val colorScheme = if (colors.isDark) {
                darkColorScheme(
                    primary = ComposeColor(colors.accent),
                    onPrimary = ComposeColor(Color.WHITE),
                    background = ComposeColor(colors.canvas),
                    surface = ComposeColor(colors.panel),
                    onSurface = ComposeColor(colors.ink),
                    onSurfaceVariant = ComposeColor(colors.muted),
                    outline = ComposeColor(colors.line),
                    error = ComposeColor(colors.danger),
                    errorContainer = ComposeColor(colors.danger).copy(alpha = 0.18f),
                    onErrorContainer = ComposeColor(colors.ink),
                    surfaceVariant = ComposeColor(colors.card),
                    outlineVariant = ComposeColor(colors.line)
                )
            } else {
                lightColorScheme(
                    primary = ComposeColor(colors.accent),
                    onPrimary = ComposeColor(Color.WHITE),
                    background = ComposeColor(colors.canvas),
                    surface = ComposeColor(colors.panel),
                    onSurface = ComposeColor(colors.ink),
                    onSurfaceVariant = ComposeColor(colors.muted),
                    outline = ComposeColor(colors.line),
                    error = ComposeColor(colors.danger),
                    errorContainer = ComposeColor(colors.danger).copy(alpha = 0.10f),
                    onErrorContainer = ComposeColor(colors.ink),
                    surfaceVariant = ComposeColor(colors.card),
                    outlineVariant = ComposeColor(colors.line)
                )
            }

            lateinit var dialog: ComponentDialog
            val composeView = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    MaterialTheme(
                        colorScheme = colorScheme,
                        shapes = WrenDesignSystem.shapes
                    ) {
                        CompositionLocalProvider(
                            LocalLayoutDirection provides if (WrenColors.isRtl(activity.currentBrowserSettings)) {
                                LayoutDirection.Rtl
                            } else {
                                LayoutDirection.Ltr
                            }
                        ) {
                            WrenDownloadsScreen(
                                initialSnapshot = initial,
                                onRefresh = { loadSnapshot(repository) },
                                onOpenStream = ::openStream,
                                onShareStream = ::shareStream,
                                onCancelStream = { record ->
                                    if (!onCancelStreamDownload(record.id)) {
                                        toast(
                                            "This download can no longer be cancelled",
                                            "لم يعد بالإمكان إلغاء هذا التنزيل"
                                        )
                                    }
                                },
                                onRemoveStreamRecord = { record ->
                                    activity.lifecycleScope.launch {
                                        runCatching { repository.remove(record.id) }
                                            .onSuccess { removed ->
                                                if (!removed) toast(
                                                    "Download record was already removed",
                                                    "سجل التنزيل محذوف بالفعل"
                                                )
                                            }
                                            .onFailure {
                                                android.util.Log.e("Wren", "Could not remove download record", it)
                                                toast("Could not remove download record", "تعذر حذف سجل التنزيل")
                                            }
                                    }
                                },
                                onDeleteStreamFile = { record ->
                                    activity.lifecycleScope.launch {
                                        val uri = runCatching { Uri.parse(record.uri) }.getOrNull()
                                        val deleted = uri != null && deleteOwnedDownloadFile(uri)
                                        if (deleted) {
                                            runCatching { repository.remove(record.id) }
                                                .onSuccess { removed ->
                                                    if (!removed) toast(
                                                        "File deleted, but its download record was already missing",
                                                        "تم حذف الملف لكن سجل التنزيل غير موجود"
                                                    )
                                                }
                                                .onFailure {
                                                    android.util.Log.e("Wren", "File deleted but download record cleanup failed", it)
                                                    toast(
                                                        "File deleted, but Wren could not remove its record",
                                                        "تم حذف الملف لكن تعذر حذف سجله من Wren"
                                                    )
                                                }
                                        } else {
                                            toast(
                                                "Wren could not verify ownership or delete this file. The record was kept.",
                                                "تعذّر على Wren التحقق من ملكية الملف أو حذفه. تم الاحتفاظ بالسجل."
                                            )
                                        }
                                    }
                                },
                                onOpenPlatform = ::openPlatform,
                                onSharePlatform = ::sharePlatform,
                                onManagePlatform = ::removePlatform,
                                onDeletePlatform = ::removePlatform,
                                onRemoveMissingPlatform = { id ->
                                    activity.lifecycleScope.launch {
                                        runCatching { repository.removeTrackedPlatformDownload(id) }
                                            .onFailure {
                                                android.util.Log.e("Wren", "Could not remove missing platform download record", it)
                                                toast("Could not update the downloads list", "تعذر تحديث قائمة التنزيلات")
                                            }
                                    }
                                },
                                text = ::t,
                                onDismiss = { dialog.dismiss() }
                            )
                        }
                    }
                }
            }
            dialog = ComponentDialog(activity).apply {
                setContentView(composeView)
                setCancelable(true)
                setCanceledOnTouchOutside(false)
            }
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.show()
            dialog.window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setGravity(Gravity.CENTER)
                setDimAmount(0.38f)
                attributes = attributes.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
        }
    }

    private suspend fun loadSnapshot(repository: RoomDownloadRepository): WrenDownloadsSnapshot {
        val records = repository.all()
        val trackedIds = repository.trackedPlatformDownloadIds()
        if (trackedIds.isEmpty()) {
            return WrenDownloadsSnapshot(records, emptyList(), emptySet(), true)
        }
        return withContext(Dispatchers.IO) {
            val found = mutableListOf<WrenPlatformDownload>()
            val seen = mutableSetOf<Long>()
            var succeeded = false
            try {
                manager.query(DownloadManager.Query().setFilterById(*trackedIds.toLongArray()))?.use { cursor ->
                    succeeded = true
                    val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                    val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                    val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val modifiedCol = cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
                    while (cursor.moveToNext()) {
                        if (idCol < 0) continue
                        val id = cursor.getLong(idCol)
                        seen += id
                        val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                        val title = rawTitle.orEmpty().filterNot(Char::isISOControl).take(240)
                            .ifBlank { t("Downloaded file", "ملف تم تنزيله") }
                        found += WrenPlatformDownload(
                            id = id,
                            title = title,
                            status = if (statusCol >= 0) cursor.getInt(statusCol) else -1,
                            reason = if (reasonCol >= 0) cursor.getInt(reasonCol) else 0,
                            totalBytes = if (totalCol >= 0) cursor.getLong(totalCol) else -1L,
                            bytesDownloaded = if (downloadedCol >= 0) cursor.getLong(downloadedCol) else 0L,
                            modifiedAt = if (modifiedCol >= 0) cursor.getLong(modifiedCol) else 0L
                        )
                    }
                }
            } catch (error: SecurityException) {
                android.util.Log.w("Wren", "DownloadManager query permission failure", error)
            } catch (error: RuntimeException) {
                android.util.Log.w("Wren", "DownloadManager query failed", error)
            }
            WrenDownloadsSnapshot(
                streamRecords = records,
                platformRecords = found,
                missingPlatformIds = if (succeeded) trackedIds - seen else emptySet(),
                querySucceeded = succeeded
            )
        }
    }

    private fun openStream(record: DownloadRecordEntity) {
        val uri = runCatching { Uri.parse(record.uri) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("content", "file")) {
            toast("File location is unavailable", "موقع الملف غير متاح")
            return
        }
        openUri(uri, record.mimeType.ifBlank { "*/*" })
    }

    private fun openPlatform(item: WrenPlatformDownload) {
        val uri = runCatching { manager.getUriForDownloadedFile(item.id) }.getOrNull()
        if (uri == null) {
            toast("File location is unavailable", "موقع الملف غير متاح")
            return
        }
        openUri(uri, activity.contentResolver.getType(uri) ?: "*/*")
    }

    private fun openUri(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            toast("No app can open this file", "لا يوجد تطبيق لفتح هذا الملف")
        }
    }

    private fun shareStream(record: DownloadRecordEntity) {
        val uri = runCatching { Uri.parse(record.uri) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("content", "file")) {
            toast("File location is unavailable", "موقع الملف غير متاح")
            return
        }
        shareUri(uri, record.mimeType.ifBlank { "*/*" }, record.displayName)
    }

    private fun sharePlatform(item: WrenPlatformDownload) {
        val uri = runCatching { manager.getUriForDownloadedFile(item.id) }.getOrNull()
        if (uri == null) {
            toast("File location is unavailable", "موقع الملف غير متاح")
            return
        }
        shareUri(uri, activity.contentResolver.getType(uri) ?: "*/*", item.title)
    }

    private fun shareUri(uri: Uri, mimeType: String, title: String) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(activity.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivity(Intent.createChooser(share, t("Share download", "مشاركة التنزيل")))
        } catch (_: Exception) {
            toast("No app can share this file", "لا يوجد تطبيق لمشاركة هذا الملف")
        }
    }

    private fun removePlatform(item: WrenPlatformDownload) {
        val removed = runCatching { manager.remove(item.id) }.getOrDefault(0)
        if (removed > 0) {
            activity.lifecycleScope.launch {
                runCatching { activity.storageRuntime.awaitReady().downloads.removeTrackedPlatformDownload(item.id) }
                    .onFailure {
                        android.util.Log.e("Wren", "Android removed download but Wren tracking cleanup failed", it)
                        toast(
                            "Android removed the download, but Wren could not update its saved list",
                            "أزال أندرويد التنزيل لكن تعذر على Wren تحديث قائمته المحفوظة"
                        )
                    }
            }
        } else {
            toast(
                "Android could not remove this download. It remains in the list.",
                "تعذّر على أندرويد إزالة هذا التنزيل. سيبقى ظاهرًا في القائمة."
            )
        }
    }

    private fun deleteOwnedDownloadFile(uri: Uri): Boolean {
        return try {
            when (uri.scheme?.lowercase()) {
                "content" -> when (uri.authority) {
                    "${activity.packageName}.files" ->
                        activity.contentResolver.delete(uri, null, null) > 0
                    "media" -> {
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                            false
                        } else {
                            val ownerColumn = android.provider.MediaStore.MediaColumns.OWNER_PACKAGE_NAME
                            val owner = activity.contentResolver.query(
                                uri, arrayOf(ownerColumn), null, null, null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(ownerColumn)) else null
                            }
                            owner == activity.packageName && activity.contentResolver.delete(uri, null, null) > 0
                        }
                    }
                    else -> false
                }
                "file" -> {
                    val path = uri.path ?: return false
                    val file = java.io.File(path).canonicalFile
                    val downloads = android.os.Environment
                        .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        .canonicalFile
                    file.parentFile?.canonicalFile == downloads && file.isFile && file.delete()
                }
                else -> false
            }
        } catch (error: Exception) {
            android.util.Log.w("Wren", "Could not delete owned download file", error)
            false
        }
    }

    private fun toast(en: String, ar: String) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            Toast.makeText(activity, t(en, ar), Toast.LENGTH_SHORT).show()
        }
    }
}
