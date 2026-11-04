package com.browsex.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.text.DateFormat
import java.util.Date

/** DownloadManager-backed download library; Android remains responsible for file transfer. */
internal class DownloadsPage(private val activity: MainActivity) {
    private val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    fun show() {
        lateinit var dialog: AlertDialog
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            setBackgroundColor(Color.rgb(239, 245, 250))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(TextView(activity).apply {
            text = "Downloads"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(19, 43, 64))
        })
        heading.addView(TextView(activity).apply {
            text = "Your Browse X downloads"
            textSize = 12f
            setTextColor(Color.rgb(83, 105, 123))
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val refresh = TextView(activity).apply {
            text = "↻"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(8, 102, 217))
            background = rounded(Color.WHITE, 13)
            contentDescription = "Refresh downloads"
        }
        header.addView(refresh, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header)

        val scroll = ScrollView(activity)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(18) })

        fun statusText(status: Int): String = when (status) {
            DownloadManager.STATUS_PENDING -> "Waiting"
            DownloadManager.STATUS_RUNNING -> "Downloading"
            DownloadManager.STATUS_PAUSED -> "Paused"
            DownloadManager.STATUS_SUCCESSFUL -> "Downloaded"
            DownloadManager.STATUS_FAILED -> "Failed"
            else -> "Unknown status"
        }

        fun render() {
            list.removeAllViews()
            val ownedIds = activity.browserStoreForDownloads().browseXDownloadIds()
            val cursor = manager.query(DownloadManager.Query())
            var count = 0
            cursor?.use { c ->
                val idCol = c.getColumnIndex(DownloadManager.COLUMN_ID)
                val titleCol = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val statusCol = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val totalCol = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val soFarCol = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val dateCol = c.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
                while (c.moveToNext()) {
                    if (idCol < 0) continue
                    val id = c.getLong(idCol)
                    if (id !in ownedIds) continue
                    count++
                    val title = c.getString(titleCol) ?: "Downloaded file"
                    val status = c.getInt(statusCol)
                    val total = if (totalCol >= 0) c.getLong(totalCol) else -1L
                    val soFar = if (soFarCol >= 0) c.getLong(soFarCol) else 0L
                    val date = if (dateCol >= 0) c.getLong(dateCol) else 0L
                    val item = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(13), dp(13), dp(10), dp(13))
                        background = rounded(Color.WHITE, 15)
                    }
                    val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    labels.addView(TextView(activity).apply {
                        text = title
                        textSize = 14f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.rgb(19, 43, 64))
                        maxLines = 2
                    })
                    val size = if (total > 0) "${(soFar / 1024)} KB / ${(total / 1024)} KB"
                        else if (soFar > 0) "${soFar / 1024} KB" else "Size unavailable"
                    val timestamp = if (date > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(date)) else ""
                    labels.addView(TextView(activity).apply {
                        text = listOf(statusText(status), size, timestamp).filter { it.isNotBlank() }.joinToString(" · ")
                        textSize = 10f
                        setTextColor(Color.rgb(83, 105, 123))
                        setPadding(0, dp(4), 0, 0)
                    })
                    item.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                    val action = TextView(activity).apply {
                        text = if (status == DownloadManager.STATUS_SUCCESSFUL) "Open" else "×"
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setTextColor(if (status == DownloadManager.STATUS_SUCCESSFUL) Color.rgb(8, 102, 217) else Color.rgb(190, 65, 75))
                        setPadding(dp(10), dp(8), dp(6), dp(8))
                        setOnClickListener {
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val uri = manager.getUriForDownloadedFile(id)
                                if (uri != null) {
                                    val mime = activity.contentResolver.getType(uri) ?: "*/*"
                                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    try { activity.startActivity(intent) }
                                    catch (_: Exception) { android.widget.Toast.makeText(activity, "No app can open this file", android.widget.Toast.LENGTH_SHORT).show() }
                                }
                            } else {
                                AlertDialog.Builder(activity).setMessage("Remove this download?")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Remove") { _, _ ->
                                        manager.remove(id)
                                        activity.browserStoreForDownloads().removeBrowseXDownload(id)
                                        render()
                                    }.show()
                            }
                        }
                    }
                    item.addView(action)
                    list.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                }
            }
            if (count == 0) list.addView(TextView(activity).apply {
                text = "No Browse X downloads yet. Files you download from websites will appear here."
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(83, 105, 123))
                setPadding(dp(22), dp(40), dp(22), dp(40))
            })
        }
        refresh.setOnClickListener { render() }
        render()
        dialog = AlertDialog.Builder(activity).setView(root).create()
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
