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
    private val onCancelStreamDownload: (String) -> Boolean
) {
    private val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val colors get() = BrowseXColors.resolve(activity, activity.currentBrowserSettings)
    private val isTvRemote get() = WrenAdaptiveUi.isTelevision(activity)
    private fun t(en: String, ar: String) = BrowseXStrings.text(activity, en, ar)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), this@DownloadsPage.colors.line)
    }

    fun show() {
        activity.lifecycleScope.launch {
            val repository = runCatching { activity.storageRuntime.awaitReady().downloads }.getOrElse { error ->
                android.util.Log.e("BrowseX", "Could not open Room downloads", error)
                android.widget.Toast.makeText(activity, t("Downloads are temporarily unavailable", "التنزيلات غير متاحة مؤقتًا"), android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val snapshot = runCatching {
                repository.all() to repository.trackedPlatformDownloadIds()
            }.onFailure { error ->
                android.util.Log.e("BrowseX", "Could not load download history", error)
                android.widget.Toast.makeText(
                    activity,
                    t("Downloads are temporarily unavailable", "التنزيلات غير متاحة مؤقتًا"),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }.getOrElse { return@launch }
            showWithRepository(repository, snapshot.first, snapshot.second)
        }
    }

    /**
     * Deletes only files that BrowseX can prove it created: its own FileProvider URI,
     * or a MediaStore Downloads row owned by this package. Unknown content URIs are
     * deliberately left untouched.
     */
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
                                if (cursor.moveToFirst()) {
                                    cursor.getString(cursor.getColumnIndexOrThrow(ownerColumn))
                                } else null
                            }
                            owner == activity.packageName &&
                                activity.contentResolver.delete(uri, null, null) > 0
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
            android.util.Log.w("BrowseX", "Could not delete owned download file", error)
            false
        }
    }

    private fun showWithRepository(repository: RoomDownloadRepository, initialRecords: List<DownloadRecordEntity>, initialPlatformIds: Set<Long>) {
        var streamRecords = initialRecords
        var platformIds = initialPlatformIds
        lateinit var dialog: AlertDialog
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(WrenAdaptiveUi.horizontalSafePadding(activity, if (isTvRemote) 30 else 18), WrenAdaptiveUi.verticalSafePadding(activity, if (isTvRemote) 26 else 16), WrenAdaptiveUi.horizontalSafePadding(activity, if (isTvRemote) 30 else 18), dp(if (isTvRemote) 28 else 14))
            setBackgroundColor(colors.canvas)
            BrowseXColors.applyDirection(this, activity.currentBrowserSettings)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(activity).apply {
            text = t("Downloads", "التنزيلات")
            textSize = if (isTvRemote) 34f else 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.ink)
        })
        heading.addView(TextView(activity).apply {
            text = t("Your Wren Browser downloads", "تنزيلات Wren Browser")
            textSize = if (isTvRemote) 15f else 12f
            setTextColor(colors.muted)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val refresh = TextView(activity).apply {
            text = "↻"
            textSize = if (isTvRemote) 30f else 23f
            gravity = Gravity.CENTER
            setTextColor(colors.accent)
            background = rounded(colors.card, 13)
            minimumWidth = dp(if (isTvRemote) 60 else 48)
            minimumHeight = dp(if (isTvRemote) 60 else 48)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = false
            foreground = WrenFocusStyle.overlay(activity, 13, colors.accent)
            contentDescription = t("Refresh downloads", "تحديث التنزيلات")
        }
        val refreshSize = dp(if (isTvRemote) 60 else 48)
        header.addView(refresh, LinearLayout.LayoutParams(refreshSize, refreshSize))
        root.addView(header)
        root.addView(TextView(activity).apply {
            text = t(
                "BrowseX saves the response received from the website directly. Older Android-managed downloads may have different retry and resume limits.",
                "يحفظ BrowseX الاستجابة التي استلمها من الموقع مباشرة. قد تختلف حدود إعادة المحاولة والاستكمال في التنزيلات القديمة التي يديرها أندرويد."
            )
            textSize = if (isTvRemote) 14f else 11f
            setTextColor(colors.muted)
            setPadding(dp(2), dp(9), dp(2), dp(2))
        })
        val search = android.widget.EditText(activity).apply {
            hint = t("Search downloads by file name", "ابحث في التنزيلات باسم الملف")
            textSize = if (isTvRemote) 18f else 13f
            isSingleLine = true
            setTextColor(colors.ink)
            setHintTextColor(colors.muted)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(colors.card, 13)
            contentDescription = t("Search downloads", "البحث في التنزيلات")
        }
        val searchRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(if (isTvRemote) 62 else 48), 1f))
        val clearSearch = TextView(activity).apply {
            text = "×"
            textSize = if (isTvRemote) 30f else 24f
            gravity = Gravity.CENTER
            setTextColor(colors.muted)
            background = rounded(colors.card, 13)
            contentDescription = t("Clear search", "مسح البحث")
            visibility = android.view.View.GONE
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = false
            foreground = WrenFocusStyle.overlay(activity, 13, colors.accent)
            setOnClickListener {
                search.text?.clear()
                search.requestFocus()
            }
        }
        searchRow.addView(clearSearch, LinearLayout.LayoutParams(dp(if (isTvRemote) 62 else 48), dp(if (isTvRemote) 62 else 48)).apply {
            marginStart = dp(8)
        })
        root.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })
        var searchQuery = ""
        var selectedFilter = "all"
        val filterBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val filterLabels = listOf(
            "all" to t("All", "الكل"),
            "active" to t("In progress", "قيد التنزيل"),
            "completed" to t("Completed", "مكتملة"),
            "issues" to t("Issues", "تحتاج انتباهًا")
        )
        val filterButtons = mutableMapOf<String, TextView>()
        fun renderFilterSelection() {
            filterButtons.forEach { (key, button) ->
                val selected = key == selectedFilter
                button.setTextColor(if (selected) colors.accent else colors.muted)
                button.setTypeface(button.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                button.background = rounded(if (selected) colors.card else colors.canvas, 18)
                button.alpha = if (selected) 1f else 0.78f
                button.isSelected = selected
            }
        }
        filterLabels.forEach { (key, label) ->
            val chip = TextView(activity).apply {
                text = label
                textSize = if (isTvRemote) 15f else 11f
                gravity = Gravity.CENTER
                setPadding(dp(13), dp(9), dp(13), dp(9))
                minimumHeight = dp(if (isTvRemote) 54 else 42)
                isClickable = true
                isFocusable = true
                isFocusableInTouchMode = false
                foreground = WrenFocusStyle.overlay(activity, 18, colors.accent)
                contentDescription = label
                setOnClickListener {
                    selectedFilter = key
                    renderFilterSelection()
                    render()
                }
            }
            filterButtons[key] = chip
            filterBar.addView(chip, LinearLayout.LayoutParams(-2, -2).apply {
                marginEnd = dp(7)
            })
        }
        val filterScroller = android.widget.HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(filterBar, ViewGroup.LayoutParams(-2, -2))
        }
        root.addView(filterScroller, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
        renderFilterSelection()

        val scroll = ScrollView(activity)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(if (isTvRemote) 26 else 18) })

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
            val normalizedSearchQuery = BrowserTextNormalizer.normalize(searchQuery)
            streamRecords.forEach { record ->
                if (normalizedSearchQuery.isNotBlank() &&
                    !BrowserTextNormalizer.normalize(record.displayName).contains(normalizedSearchQuery)
                ) return@forEach
                val recordIsActive = record.status == "queued" || record.status == "running"
                val recordIsFailed = record.status == "failed"
                val recordIsCancelled = record.status == "cancelled"
                val recordIsCompleted = !recordIsActive && !recordIsFailed && !recordIsCancelled
                val matchesFilter = when (selectedFilter) {
                    "active" -> recordIsActive
                    "completed" -> recordIsCompleted
                    "issues" -> recordIsFailed || recordIsCancelled
                    else -> true
                }
                if (!matchesFilter) return@forEach
                count++
                val item = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(if (isTvRemote) 20 else 13), dp(if (isTvRemote) 18 else 13), dp(if (isTvRemote) 18 else 10), dp(if (isTvRemote) 18 else 13))
                    background = rounded(colors.card, 15)
                }
                val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(TextView(activity).apply {
                    text = record.displayName
                    textSize = if (isTvRemote) 18f else 14f
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
                    val percent = ((record.bytesWritten.coerceAtMost(record.totalBytes).toDouble() / record.totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                    " · ${record.bytesWritten / 1024} / ${record.totalBytes / 1024} KB · $percent%"
                } else {
                    " · ${record.bytesWritten / 1024} KB"
                }
                val detail = statusLabel + if (recordIsActive || !recordIsFailed) progressText + " · " + date else ""
                labels.addView(TextView(activity).apply {
                    text = detail
                    textSize = if (isTvRemote) 14f else 10f
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
                }, LinearLayout.LayoutParams(-1, dp(if (isTvRemote) 6 else 4)).apply { topMargin = dp(7) })
                item.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                val action = TextView(activity).apply {
                    text = when {
                        recordIsActive -> t("Cancel", "إلغاء")
                        recordIsFailed || recordIsCancelled -> t("Remove", "حذف")
                        else -> t("Open", "فتح")
                    }
                    textSize = if (isTvRemote) 16f else 13f
                    gravity = Gravity.CENTER
                    setTextColor(if (recordIsFailed || recordIsCancelled) colors.danger else colors.accent)
                    setPadding(dp(10), dp(8), dp(6), dp(8))
                    minimumWidth = dp(if (isTvRemote) 60 else 48)
                    minimumHeight = dp(if (isTvRemote) 60 else 48)
                    isClickable = true
                    isFocusable = true
                    isFocusableInTouchMode = false
                    foreground = WrenFocusStyle.overlay(activity, 12, colors.accent)
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
                if (!recordIsActive && !recordIsFailed && !recordIsCancelled) {
                    item.addView(TextView(activity).apply {
                        text = t("Share", "مشاركة")
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setTextColor(colors.accent)
                        setPadding(dp(7), dp(8), dp(7), dp(8))
                        minimumHeight = dp(48)
                        isClickable = true
                        isFocusable = true
                        isFocusableInTouchMode = false
                        foreground = WrenFocusStyle.overlay(activity, 12, colors.accent)
                        contentDescription = t("Share downloaded file", "مشاركة الملف المنزّل")
                        setOnClickListener {
                            val uri = runCatching { Uri.parse(record.uri) }.getOrNull()
                            if (uri == null || uri.scheme !in setOf("content", "file")) {
                                android.widget.Toast.makeText(activity, t("File location is unavailable", "موقع الملف غير متاح"), android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = record.mimeType.ifBlank { "*/*" }
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = android.content.ClipData.newUri(
                                        activity.contentResolver, record.displayName, uri
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    activity.startActivity(Intent.createChooser(share, t("Share download", "مشاركة التنزيل")))
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(activity, t("No app can share this file", "لا يوجد تطبيق لمشاركة هذا الملف"), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    })
                }
                if (!recordIsActive) item.addView(TextView(activity).apply {
                    text = "×"
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(colors.muted)
                    minimumWidth = dp(48)
                    minimumHeight = dp(48)
                    contentDescription = t("Remove download record", "حذف سجل التنزيل")
                    isClickable = true
                    isFocusable = true
                    isFocusableInTouchMode = false
                    foreground = WrenFocusStyle.overlay(activity, 12, colors.accent)
                    setOnClickListener {
                        AlertDialog.Builder(activity)
                            .setMessage(
                                if (recordIsFailed || recordIsCancelled) {
                                    t("Remove this download record from BrowseX?", "إزالة سجل هذا التنزيل من BrowseX؟")
                                } else {
                                    t("Delete this downloaded file from your device and remove its record?", "حذف الملف المنزّل من جهازك وإزالة سجله؟")
                                }
                            )
                            .setNegativeButton(t("Keep", "احتفاظ"), null)
                            .setPositiveButton(
                                if (recordIsFailed || recordIsCancelled) t("Remove", "إزالة")
                                else t("Delete file", "حذف الملف")
                            ) { _, _ ->
                                activity.lifecycleScope.launch {
                                    val fileDeleted = if (recordIsFailed || recordIsCancelled) {
                                        true
                                    } else {
                                        val uri = runCatching { Uri.parse(record.uri) }.getOrNull()
                                        uri != null && deleteOwnedDownloadFile(uri)
                                    }
                                    if (fileDeleted) {
                                        repository.remove(record.id)
                                        reloadRoomRecords()
                                    } else {
                                        android.widget.Toast.makeText(
                                            activity,
                                            t("BrowseX could not verify ownership or delete this file. The record was kept.", "تعذّر على BrowseX التحقق من ملكية الملف أو حذفه. تم الاحتفاظ بالسجل."),
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
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
                    val title = if (titleCol >= 0) c.getString(titleCol) ?: "Downloaded file" else "Downloaded file"
                    if (normalizedSearchQuery.isNotBlank() &&
                        !BrowserTextNormalizer.normalize(title).contains(normalizedSearchQuery)
                    ) continue
                    val status = if (statusCol >= 0) c.getInt(statusCol) else -1
                    val isPlatformActive = status == DownloadManager.STATUS_PENDING ||
                        status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED
                    val isPlatformCompleted = status == DownloadManager.STATUS_SUCCESSFUL
                    // Surface unexpected platform states under Issues instead of hiding them
                    // from every focused filter.
                    val isPlatformIssue = status == DownloadManager.STATUS_FAILED ||
                        (!isPlatformActive && !isPlatformCompleted)
                    val matchesFilter = when (selectedFilter) {
                        "active" -> isPlatformActive
                        "completed" -> isPlatformCompleted
                        "issues" -> isPlatformIssue
                        else -> true
                    }
                    if (!matchesFilter) continue
                    count++
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
                        textSize = if (isTvRemote) 18f else 14f
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
                        textSize = if (isTvRemote) 14f else 10f
                        setTextColor(colors.muted)
                        setPadding(0, dp(4), 0, 0)
                    })
                    item.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                    val action = TextView(activity).apply {
                        text = if (status == DownloadManager.STATUS_SUCCESSFUL) t("Open", "فتح") else if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED) t("Cancel", "إلغاء") else t("Remove", "حذف")
                        textSize = if (isTvRemote) 16f else 13f
                        gravity = Gravity.CENTER
                        setTextColor(if (status == DownloadManager.STATUS_SUCCESSFUL) colors.accent else colors.danger)
                        setPadding(dp(10), dp(8), dp(6), dp(8))
                        minimumWidth = dp(if (isTvRemote) 60 else 48)
                        minimumHeight = dp(if (isTvRemote) 60 else 48)
                        isClickable = true
                        isFocusable = true
                        isFocusableInTouchMode = false
                        foreground = WrenFocusStyle.overlay(activity, 12, colors.accent)
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
                                        val removed = runCatching { manager.remove(id) }.getOrDefault(0)
                                        if (removed > 0) {
                                            activity.lifecycleScope.launch {
                                                repository.removeTrackedPlatformDownload(id)
                                                reloadRoomRecords()
                                            }
                                        } else {
                                            android.widget.Toast.makeText(
                                                activity,
                                                t("Android could not cancel this download. It is still listed so you can try again.", "تعذّر على أندرويد إلغاء هذا التنزيل. سيبقى ظاهرًا لتتمكن من المحاولة مجددًا."),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }.show()
                            }
                        }
                    }
                    item.addView(action)
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        item.addView(TextView(activity).apply {
                            text = t("Share", "مشاركة")
                            textSize = 12f
                            gravity = Gravity.CENTER
                            setTextColor(colors.accent)
                            setPadding(dp(7), dp(8), dp(7), dp(8))
                            minimumHeight = dp(48)
                            isClickable = true
                            isFocusable = true
                            isFocusableInTouchMode = false
                            foreground = WrenFocusStyle.overlay(activity, 12, colors.accent)
                            contentDescription = t("Share downloaded file", "مشاركة الملف المنزّل")
                            setOnClickListener {
                                val uri = manager.getUriForDownloadedFile(id)
                                if (uri == null) {
                                    android.widget.Toast.makeText(activity, t("File location is unavailable", "موقع الملف غير متاح"), android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    val mime = activity.contentResolver.getType(uri) ?: "*/*"
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = mime
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = android.content.ClipData.newUri(
                                            activity.contentResolver, title, uri
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        activity.startActivity(Intent.createChooser(share, t("Share download", "مشاركة التنزيل")))
                                    } catch (_: Exception) {
                                        android.widget.Toast.makeText(activity, t("No app can share this file", "لا يوجد تطبيق لمشاركة هذا الملف"), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        })
                    }
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        item.addView(TextView(activity).apply {
                            text = "×"
                            textSize = 20f
                            gravity = Gravity.CENTER
                            setTextColor(colors.danger)
                            minimumWidth = dp(40)
                            minimumHeight = dp(48)
                            contentDescription = t("Delete downloaded file", "حذف الملف المنزّل")
                            isClickable = true
                            isFocusable = true
                            isFocusableInTouchMode = false
                            foreground = WrenFocusStyle.overlay(activity, 12, colors.accent)
                            setOnClickListener {
                                AlertDialog.Builder(activity)
                                    .setMessage(t("Delete this downloaded file from your device?", "حذف هذا الملف المنزّل من جهازك؟"))
                                    .setNegativeButton(t("Keep", "احتفاظ"), null)
                                    .setPositiveButton(t("Delete", "حذف")) { _, _ ->
                                        val removed = runCatching { manager.remove(id) }.getOrDefault(0)
                                        if (removed > 0) {
                                            activity.lifecycleScope.launch {
                                                repository.removeTrackedPlatformDownload(id)
                                                reloadRoomRecords()
                                            }
                                        } else {
                                            android.widget.Toast.makeText(
                                                activity,
                                                t("Android could not delete this download.", "تعذّر على أندرويد حذف هذا التنزيل."),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }.show()
                            }
                        })
                    }
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
                    setPadding(dp(if (isTvRemote) 20 else 13), dp(if (isTvRemote) 18 else 13), dp(if (isTvRemote) 18 else 10), dp(if (isTvRemote) 18 else 13))
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
                    isClickable = true
                    isFocusable = true
                    isFocusableInTouchMode = false
                    foreground = WrenFocusStyle.overlay(activity, 12, colors.accent)
                    setOnClickListener { activity.lifecycleScope.launch { repository.removeTrackedPlatformDownload(id); reloadRoomRecords() } }
                })
                list.addView(stale, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                count++
            }
            if (count == 0) list.addView(TextView(activity).apply {
                text = when {
                    normalizedSearchQuery.isNotBlank() && selectedFilter != "all" ->
                        t("No downloads match this search and filter.", "لا توجد تنزيلات تطابق البحث والتصفية.")
                    normalizedSearchQuery.isNotBlank() ->
                        t("No downloads match your search.", "لا توجد تنزيلات تطابق بحثك.")
                    selectedFilter == "active" ->
                        t("No downloads are in progress.", "لا توجد تنزيلات قيد التنفيذ.")
                    selectedFilter == "completed" ->
                        t("No completed downloads yet.", "لا توجد تنزيلات مكتملة بعد.")
                    selectedFilter == "issues" ->
                        t("No downloads need attention.", "لا توجد تنزيلات تحتاج إلى انتباه.")
                    else ->
                        t("No Wren downloads yet. Files you download from websites will appear here.", "لا توجد تنزيلات بعد. ستظهر هنا الملفات التي تنزّلها من المواقع.")
                }
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
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                clearSearch.visibility = if (searchQuery.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                render()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
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