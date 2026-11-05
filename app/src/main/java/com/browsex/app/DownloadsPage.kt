package com.browsex.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.browsex.app.storage.RoomDownloadRepository
import com.browsex.app.storage.DownloadRecordEntity
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Combined library for Android DownloadManager jobs and Gecko response-stream downloads. */
internal class DownloadsPage(
    private val activity: MainActivity,
    private val store: BrowserStore,
    private val onCancelStreamDownload: (String) -> Boolean
) {
    private val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val colors get() = BrowseXColors.resolve(activity, store)
    private fun t(en: String, ar: String) = BrowseXStrings.text(activity, en, ar)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    fun show() {
        activity.lifecycleScope.launch {
            val repository = runCatching { activity.storageRuntime.awaitReady().downloads }.getOrElse { error ->
                android.util.Log.e("BrowseX", "Could not open Room downloads", error)
                android.widget.Toast.makeText(activity, t("Downloads are temporarily unavailable", "التنزيلات غير متاحة مؤقتًا"), android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val records = repository.all()
            val platformIds = repository.trackedPlatformDownloadIds()
            showWithRepository(repository, records, platformIds)
        }
    }

    private fun showWithRepository(repository: RoomDownloadRepository, initialRecords: List<DownloadRecordEntity>, initialPlatformIds: Set<Long>) {
        var streamRecords = initialRecords
        var platformIds = initialPlatformIds
        lateinit var dialog: AlertDialog
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            setBackgroundColor(colors.canvas)
            BrowseXColors.applyDirection(this, store)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(activity).apply {
            text = t("Downloads", "التنزيلات")
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.ink)
        })
        heading.addView(TextView(activity).apply {
            text = t("Your Browse X downloads", "تنزيلات BrowseX")
            textSize = 12f
            setTextColor(colors.muted)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val refresh = TextView(activity).apply {
            text = "↻"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(colors.accent)
            background = rounded(colors.card, 13)
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            contentDescription = t("Refresh downloads", "تحديث التنزيلات")
        }
        header.addView(refresh, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header)
        root.addView(TextView(activity).apply {
            text = t(
                "BrowseX saves the response received from the website directly. Older Android-managed downloads may have different retry and resume limits.",
                "يحفظ BrowseX الاستجابة التي استلمها من الموقع مباشرة. قد تختلف حدود إعادة المحاولة والاستكمال في التنزيلات القديمة التي يديرها أندرويد."
            )
            textSize = 11f
            setTextColor(colors.muted)
            setPadding(dp(2), dp(9), dp(2), dp(2))
        })

        val scroll = ScrollView(activity)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(18) })

        fun statusText(status: Int): String = when (status) {
            DownloadManager.STATUS_PENDING -> t("Waiting", "في الانتظار")
            DownloadManager.STATUS_RUNNING -> t("Downloading", "جارٍ التنزيل")
            DownloadManager.STATUS_PAUSED -> t("Paused", "متوقف مؤقتًا")
            DownloadManager.STATUS_SUCCESSFUL -> t("Downloaded", "تم التنزيل")
            DownloadManager.STATUS_FAILED -> t("Failed", "فشل")
            else -> t("Unknown status", "حالة غير معروفة")
        }

        fun reasonText(status: Int, reason: Int): String = when {
            status == DownloadManager.STATUS_PAUSED -> when (reason) {
                DownloadManager.PAUSED_QUEUED_FOR_WIFI -> t("Waiting for Wi-Fi", "بانتظار Wi-Fi")
                DownloadManager.PAUSED_WAITING_FOR_NETWORK -> t("Waiting for network", "بانتظار الشبكة")
                DownloadManager.PAUSED_WAITING_TO_RETRY -> t("Waiting to retry", "بانتظار إعادة المحاولة")
                else -> t("Paused by Android", "أوقفه أندرويد مؤقتًا")
            }
            status == DownloadManager.STATUS_FAILED -> when (reason) {
                DownloadManager.ERROR_INSUFFICIENT_SPACE -> t("Not enough storage", "المساحة غير كافية")
                DownloadManager.ERROR_DEVICE_NOT_FOUND -> t("Storage unavailable", "وحدة التخزين غير متاحة")
                DownloadManager.ERROR_FILE_ALREADY_EXISTS -> t("File already exists", "الملف موجود بالفعل")
                DownloadManager.ERROR_FILE_ERROR -> t("File write error", "خطأ في كتابة الملف")
                DownloadManager.ERROR_HTTP_DATA_ERROR -> t("Network data error", "خطأ في بيانات الشبكة")
                DownloadManager.ERROR_TOO_MANY_REDIRECTS -> t("Too many redirects", "عمليات إعادة توجيه كثيرة")
                DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> t("Unsupported server response", "استجابة الخادم غير مدعومة")
                DownloadManager.ERROR_CANNOT_RESUME -> t("Cannot resume this download", "لا يمكن استكمال هذا التنزيل")
                else -> t("Android could not finish this download", "تعذّر على أندرويد إكمال التنزيل")
            }
            else -> ""
        }

        lateinit var reloadRoomRecords: () -> Unit
        var platformDownloadsActive = false

        fun render() {
            platformDownloadsActive = false
            list.removeAllViews()
            val ownedIds = platformIds
            val seenIds = mutableSetOf<Long>()
            var querySucceeded = ownedIds.isEmpty()
            // Query only IDs registered by BrowseX; never enumerate other apps' downloads.
            val cursor = try {
                if (ownedIds.isEmpty()) null else manager.query(
                    DownloadManager.Query().setFilterById(*ownedIds.toLongArray())
                ).also { querySucceeded = it != null }
            } catch (error: SecurityException) {
                android.util.Log.w("BrowseX", "Downloads query permission failure", error)
                null
            } catch (error: RuntimeException) {
                android.util.Log.w("BrowseX", "Downloads query failed", error)
                null
            }
            var count = 0
            streamRecords.forEach { record ->
                count++
                val recordIsActive = record.status == "queued" || record.status == "running"
                val recordIsFailed = record.status == "failed"
                val recordIsCancelled = record.status == "cancelled"
                val item = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(13), dp(13), dp(10), dp(13))
                    background = rounded(colors.card, 15)
                }
                val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(TextView(activity).apply {
                    text = record.displayName
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(colors.ink)
                    maxLines = 2
                })
                val date = if (record.createdAt > 0L) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.createdAt)) else ""
                val statusLabel = when {
                    recordIsActive && record.status == "queued" -> t("Waiting in queue", "بانتظار دوره")
                    recordIsActive -> t("Downloading", "جارٍ التنزيل")
                    recordIsCancelled -> t("Cancelled", "تم الإلغاء")
                    recordIsFailed -> t("Failed: ", "فشل: ") + record.error
                    else -> t("Downloaded directly from the website response", "تم حفظ استجابة الموقع مباشرة")
                }
                val progressText = if (record.totalBytes > 0L) {
                    " · ${record.bytesWritten / 1024} / ${record.totalBytes / 1024} KB"
                } else if (recordIsActive) {
                    " · ${record.bytesWritten / 1024} KB"
                } else {
                    " · ${record.bytesWritten / 1024} KB"
                }
                val detail = statusLabel + if (recordIsActive || !recordIsFailed) progressText + " · " + date else ""
                labels.addView(TextView(activity).apply {
                    text = detail
                    textSize = 10f
                    setTextColor(when {
                        recordIsFailed -> colors.danger
                        recordIsCancelled -> colors.muted
                        recordIsActive -> colors.accent
                        else -> colors.muted
                    })
                    setPadding(0, dp(4), 0, 0)
                    maxLines = 3
                })
                if (recordIsActive) labels.addView(ProgressBar(
                    activity, null, android.R.attr.progressBarStyleHorizontal
                ).apply {
                    max = 100
                    isIndeterminate = record.totalBytes <= 0L
                    progress = if (record.totalBytes > 0L) {
                        ((record.bytesWritten.coerceAtMost(record.totalBytes).toDouble() / record.totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                    } else 0
                }, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(7) })
                item.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                val action = TextView(activity).apply {
                    text = when {
                        recordIsActive -> t("Cancel", "إلغاء")
                        recordIsFailed || recordIsCancelled -> t("Remove", "حذف")
                        else -> t("Open", "فتح")
                    }
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(if (recordIsFailed || recordIsCancelled) colors.danger else colors.accent)
                    setPadding(dp(10), dp(8), dp(6), dp(8))
                    minimumWidth = dp(48)
                    minimumHeight = dp(48)
                    isClickable = true
                    isFocusable = true
                    contentDescription = when {
                        recordIsActive -> t("Cancel download", "إلغاء التنزيل")
                        recordIsFailed || recordIsCancelled -> t("Remove download record", "حذف سجل التنزيل")
                        else -> t("Open downloaded file", "فتح الملف المنزّل")
                    }
                    setOnClickListener {
                        when {
                            recordIsActive -> AlertDialog.Builder(activity)
                                .setMessage(t("Cancel this download? The partial file will be discarded.", "إلغاء هذا التنزيل؟ سيتم حذف الملف غير المكتمل."))
                                .setNegativeButton(t("Keep", "متابعة"), null)
                                .setPositiveButton(t("Cancel download", "إلغاء التنزيل")) { _, _ ->
                                    if (!onCancelStreamDownload(record.id)) {
                                        android.widget.Toast.makeText(activity, t("This download can no longer be cancelled", "لم يعد بالإمكان إلغاء هذا التنزيل"), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }.show()
                            recordIsFailed || recordIsCancelled -> {
                                activity.lifecycleScope.launch { repository.remove(record.id); reloadRoomRecords() }
                            }
                            else -> {
                                val uri = runCatching { Uri.parse(record.uri) }.getOrNull()
                                if (uri == null) {
                                    android.widget.Toast.makeText(activity, t("File location is unavailable", "موقع الملف غير متاح"), android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, record.mimeType.ifBlank { "*/*" })
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    try { activity.startActivity(intent) }
                                    catch (_: Exception) { android.widget.Toast.makeText(activity, t("No app can open this file", "لا يوجد تطبيق لفتح هذا الملف"), android.widget.Toast.LENGTH_SHORT).show() }
                                }
                            }
                        }
                    }
                }
                item.addView(action)
                if (!recordIsActive) item.addView(TextView(activity).apply {
                    text = "×"
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(colors.muted)
                    minimumWidth = dp(40)
                    minimumHeight = dp(48)
                    contentDescription = t("Remove download record", "حذف سجل التنزيل")
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        AlertDialog.Builder(activity)
                            .setMessage(t("Remove this download from BrowseX? The saved file will remain on your device.", "إزالة هذا التنزيل من BrowseX؟ سيبقى الملف محفوظًا على جهازك."))
                            .setNegativeButton(t("Keep", "احتفاظ"), null)
                            .setPositiveButton(t("Remove", "حذف")) { _, _ ->
                                activity.lifecycleScope.launch { repository.remove(record.id); reloadRoomRecords() }
                            }.show()
                    }
                })
                list.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
            cursor?.use { c ->
                val idCol = c.getColumnIndex(DownloadManager.COLUMN_ID)
                val titleCol = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val statusCol = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val totalCol = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val soFarCol = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val dateCol = c.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
                val reasonCol = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                while (c.moveToNext()) {
                    if (idCol < 0) continue
                    val id = c.getLong(idCol)
                    seenIds.add(id)
                    count++
                    val title = if (titleCol >= 0) c.getString(titleCol) ?: "Downloaded file" else "Downloaded file"
                    val status = if (statusCol >= 0) c.getInt(statusCol) else -1
                    if (status == DownloadManager.STATUS_PENDING ||
                        status == DownloadManager.STATUS_RUNNING ||
                        status == DownloadManager.STATUS_PAUSED
                    ) platformDownloadsActive = true
                    val total = if (totalCol >= 0) c.getLong(totalCol) else -1L
                    val soFar = if (soFarCol >= 0) c.getLong(soFarCol) else 0L
                    val date = if (dateCol >= 0) c.getLong(dateCol) else 0L
                    val reason = if (reasonCol >= 0) c.getInt(reasonCol) else 0
                    val item = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(13), dp(13), dp(10), dp(13))
                        background = rounded(colors.card, 15)
                    }
                    val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    labels.addView(TextView(activity).apply {
                        text = title
                        textSize = 14f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(colors.ink)
                        maxLines = 2
                    })
                    val size = if (total > 0) "${(soFar / 1024)} KB / ${(total / 1024)} KB"
                        else if (soFar > 0) "${soFar / 1024} KB" else "Size unavailable"
                    val timestamp = if (date > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(date)) else ""
                    val percent = if (total > 0L) " · ${(soFar * 100L / total).coerceIn(0L, 100L)}%" else ""
                    val reasonLabel = reasonText(status, reason)
                    labels.addView(TextView(activity).apply {
                        text = listOf(statusText(status), reasonLabel, size + percent, timestamp).filter { it.isNotBlank() }.joinToString(" · ")
                        textSize = 10f
                        setTextColor(colors.muted)
                        setPadding(0, dp(4), 0, 0)
                    })
                    item.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                    val action = TextView(activity).apply {
                        text = if (status == DownloadManager.STATUS_SUCCESSFUL) t("Open", "فتح") else if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED) t("Cancel", "إلغاء") else t("Remove", "حذف")
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setTextColor(if (status == DownloadManager.STATUS_SUCCESSFUL) colors.accent else colors.danger)
                        setPadding(dp(10), dp(8), dp(6), dp(8))
                        minimumWidth = dp(48)
                        minimumHeight = dp(48)
                        isClickable = true
                        isFocusable = true
                        contentDescription = if (status == DownloadManager.STATUS_SUCCESSFUL) t("Open downloaded file", "فتح الملف المنزّل") else t("Manage download", "إدارة التنزيل")
                        setOnClickListener {
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val uri = manager.getUriForDownloadedFile(id)
                                if (uri != null) {
                                    val mime = activity.contentResolver.getType(uri) ?: "*/*"
                                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    try { activity.startActivity(intent) }
                                    catch (_: Exception) { android.widget.Toast.makeText(activity, t("No app can open this file", "لا يوجد تطبيق لفتح هذا الملف"), android.widget.Toast.LENGTH_SHORT).show() }
                                }
                            } else {
                                val removingActive = status == DownloadManager.STATUS_RUNNING ||
                                    status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
                                AlertDialog.Builder(activity)
                                    .setMessage(if (removingActive) t("Cancel this download?", "إلغاء هذا التنزيل؟") else t("Remove this download record?", "حذف سجل هذا التنزيل؟"))
                                    .setNegativeButton(t("Keep", "احتفاظ"), null)
                                    .setPositiveButton(t("Remove", "حذف")) { _, _ ->
                                        runCatching { manager.remove(id) }
                                        activity.lifecycleScope.launch { repository.removeTrackedPlatformDownload(id); reloadRoomRecords() }
                                    }.show()
                            }
                        }
                    }
                    item.addView(action)
                    list.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                }
            }
            if (!querySucceeded) {
                list.addView(TextView(activity).apply {
                    text = t(
                        "Android could not read the download list. Check app access and refresh; existing downloads were not removed.",
                        "تعذّر على أندرويد قراءة قائمة التنزيلات. تحقق من صلاحيات التطبيق ثم حدّث القائمة؛ لم تُحذف سجلات التنزيل."
                    )
                    textSize = 12f
                    setTextColor(colors.danger)
                    setPadding(dp(14), dp(18), dp(14), dp(18))
                })
                return
            }
            val missing = ownedIds - seenIds
            missing.forEach { id ->
                val stale = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(13), dp(13), dp(10), dp(13))
                    background = rounded(colors.card, 15)
                }
                stale.addView(TextView(activity).apply {
                    text = t("Download no longer exists in Android DownloadManager", "التنزيل لم يعد موجودًا في مدير تنزيلات أندرويد")
                    textSize = 12f
                    setTextColor(colors.muted)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                stale.addView(TextView(activity).apply {
                    text = t("Remove", "حذف")
                    contentDescription = t("Remove unavailable download", "حذف التنزيل غير المتاح")
                    setTextColor(colors.danger)
                    setPadding(dp(12), dp(8), dp(6), dp(8))
                    setOnClickListener { activity.lifecycleScope.launch { repository.removeTrackedPlatformDownload(id); reloadRoomRecords() } }
                })
                list.addView(stale, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                count++
            }
            if (count == 0) list.addView(TextView(activity).apply {
                text = t("No BrowseX downloads yet. Files you download from websites will appear here.", "لا توجد تنزيلات بعد. ستظهر هنا الملفات التي تنزّلها من المواقع.")
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(colors.muted)
                setPadding(dp(22), dp(40), dp(22), dp(40))
            })
        }
        reloadRoomRecords = {
            activity.lifecycleScope.launch {
                val scrollY = scroll.scrollY
                runCatching {
                    streamRecords = repository.all()
                    platformIds = repository.trackedPlatformDownloadIds()
                }
                    .onSuccess {
                        render()
                        scroll.post { scroll.scrollTo(0, scrollY) }
                    }
                    .onFailure { error -> android.util.Log.e("BrowseX", "Could not refresh Room downloads", error) }
            }
        }
        val refreshHandler = Handler(Looper.getMainLooper())
        val refreshRunnable = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                val streamDownloadsActive = streamRecords.any {
                    it.status == "queued" || it.status == "running"
                }
                if (streamDownloadsActive || platformDownloadsActive) {
                    reloadRoomRecords()
                    refreshHandler.postDelayed(this, 900L)
                }
            }
        }
        refresh.setOnClickListener { reloadRoomRecords() }
        render()
        dialog = AlertDialog.Builder(activity).setView(root).create()
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            if (streamRecords.any { it.status == "queued" || it.status == "running" } ||
                platformDownloadsActive
            ) refreshHandler.postDelayed(refreshRunnable, 900L)
        }
        dialog.setOnDismissListener { refreshHandler.removeCallbacks(refreshRunnable) }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}