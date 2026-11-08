package com.browsex.app

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.browsex.app.storage.BookmarkFolderEntity
import com.browsex.app.storage.BookmarkEntity
import com.browsex.app.storage.HistoryEntryEntity
import kotlinx.coroutines.launch

/** Room-backed bookmarks/history surface. Every mutation is asynchronous and persisted. */
internal class RoomSavedPagesPage(
    private val activity: MainActivity,
    private val store: BrowserStore,
    private val title: String,
    private val onOpen: (String) -> Unit
) {
    private val isHistory = title.contains("History", ignoreCase = true)
    private val colors get() = BrowseXColors.resolve(activity, store)
    private val isTvRemote get() = WrenAdaptiveUi.isTelevision(activity)
    private fun t(en: String, ar: String) = BrowseXStrings.text(activity, en, ar)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    fun show() {
        activity.lifecycleScope.launch {
            val graph = runCatching { activity.storageRuntime.awaitReady() }.getOrElse {
                android.util.Log.e("BrowseX", "Could not open Room library", it)
                android.widget.Toast.makeText(activity, t("Library is temporarily unavailable", "المكتبة غير متاحة مؤقتًا"), android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val bookmarkRepo = graph.bookmarks
            val historyRepo = graph.history
            var bookmarks = bookmarkRepo.all()
            var folders = bookmarkRepo.folders()
            var history = historyRepo.recent()
            var selectedFolder: String? = null

            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(WrenAdaptiveUi.horizontalSafePadding(activity), WrenAdaptiveUi.verticalSafePadding(activity), WrenAdaptiveUi.horizontalSafePadding(activity), dp(if (isTvRemote) 28 else 14))
                setBackgroundColor(colors.canvas)
                BrowseXColors.applyDirection(this, store)
            }
            val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(TextView(activity).apply {
                text = if (isHistory) t("History", "السجل") else t("Bookmarks", "المفضلة")
                textSize = if (isTvRemote) 34f else 25f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colors.ink)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val close = TextView(activity).apply {
                text = "×"; textSize = if (isTvRemote) 34f else 27f; gravity = Gravity.CENTER
                setTextColor(colors.ink); background = rounded(colors.card, 14)
                contentDescription = t("Close", "إغلاق")
                WrenAdaptiveUi.prepareFocusable(this, activity, 14, colors.accent, if (isTvRemote) 64 else 48)
            }
            header.addView(close)
            root.addView(header)

            val search = EditText(activity).apply {
                hint = if (isHistory) t("Search browsing history", "ابحث في سجل التصفح") else t("Search bookmarks", "ابحث في المفضلة")
                textSize = if (isTvRemote) 19f else 14f; isSingleLine = true
                setTextColor(colors.ink); setHintTextColor(colors.muted)
                setPadding(dp(14), 0, dp(14), 0); background = rounded(colors.card, 14)
            }
            root.addView(search, LinearLayout.LayoutParams(-1, dp(if (isTvRemote) 64 else 48)).apply { topMargin = dp(12) })
            val folderBar = LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(4))
                clipChildren = false
            }
            val folderScroll = android.widget.HorizontalScrollView(activity).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                visibility = if (isHistory) android.view.View.GONE else android.view.View.VISIBLE
                addView(folderBar, android.widget.HorizontalScrollView.LayoutParams(-2, -2))
            }
            root.addView(folderScroll)
            val scroll = ScrollView(activity).apply { clipToPadding = false }
            val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(8)) }
            scroll.addView(list)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            lateinit var dialog: AlertDialog

            suspend fun reload() {
                bookmarks = bookmarkRepo.all()
                folders = bookmarkRepo.folders()
                history = historyRepo.recent()
            }
            lateinit var render: (String) -> Unit
            lateinit var drawFolders: () -> Unit

            fun createFolderDialog() {
                val field = EditText(activity).apply { hint = t("Folder name", "اسم المجلد"); maxLines = 1 }
                AlertDialog.Builder(activity).setTitle(t("New bookmark folder", "مجلد مفضلة جديد"))
                    .setView(field).setNegativeButton(t("Cancel", "إلغاء"), null)
                    .setPositiveButton(t("Create", "إنشاء")) { _, _ ->
                        activity.lifecycleScope.launch {
                            val id = bookmarkRepo.createFolder(field.text?.toString().orEmpty())
                            if (id == null) android.widget.Toast.makeText(activity, t("Folder name is invalid or already used", "اسم المجلد غير صالح أو مستخدم"), android.widget.Toast.LENGTH_SHORT).show()
                            else selectedFolder = id
                            reload(); drawFolders(); render(search.text?.toString().orEmpty())
                        }
                    }.show()
            }

            drawFolders = {
                folderBar.removeAllViews()
                fun chip(label: String, id: String?) {
                    val chosen = selectedFolder == id
                    val chip = TextView(activity).apply {
                        text = label; textSize = if (isTvRemote) 16f else 11f; typeface = Typeface.DEFAULT_BOLD
                        setTextColor(if (chosen) colors.accent else colors.muted)
                        setPadding(dp(if (isTvRemote) 18 else 11), dp(if (isTvRemote) 14 else 8), dp(if (isTvRemote) 18 else 11), dp(if (isTvRemote) 14 else 8))
                        background = rounded(if (chosen) colors.selected else colors.card, 18)
                        isClickable = true
                        WrenAdaptiveUi.prepareFocusable(this, activity, 18, colors.accent, if (isTvRemote) 60 else 48)
                        setOnClickListener { selectedFolder = id; drawFolders(); render(search.text?.toString().orEmpty()) }
                    }
                    if (id != null && id != "unfiled") chip.setOnLongClickListener {
                        AlertDialog.Builder(activity).setTitle(label)
                            .setItems(arrayOf(t("Rename folder", "إعادة تسمية المجلد"), t("Delete folder", "حذف المجلد"))) { _, action ->
                                if (action == 0) {
                                    val field = EditText(activity).apply { setText(label); maxLines = 1 }
                                    AlertDialog.Builder(activity).setTitle(t("Rename folder", "إعادة تسمية المجلد"))
                                        .setView(field).setNegativeButton(t("Cancel", "إلغاء"), null)
                                        .setPositiveButton(t("Save", "حفظ")) { _, _ ->
                                            activity.lifecycleScope.launch {
                                                if (!bookmarkRepo.renameFolder(id, field.text?.toString().orEmpty())) {
                                                    android.widget.Toast.makeText(activity, t("Could not rename folder", "تعذرت إعادة تسمية المجلد"), android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                reload(); drawFolders(); render(search.text?.toString().orEmpty())
                                            }
                                        }.show()
                                } else {
                                    AlertDialog.Builder(activity).setMessage(t("Bookmarks in this folder will move to Unfiled.", "ستُنقل المفضلة في هذا المجلد إلى غير مصنف."))
                                        .setNegativeButton(t("Cancel", "إلغاء"), null)
                                        .setPositiveButton(t("Delete", "حذف")) { _, _ ->
                                            activity.lifecycleScope.launch {
                                                bookmarkRepo.deleteFolder(id)
                                                if (selectedFolder == id) selectedFolder = null
                                                reload(); drawFolders(); render(search.text?.toString().orEmpty())
                                            }
                                        }.show()
                                }
                            }.show()
                        true
                    }
                    folderBar.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
                }
                chip(t("All", "الكل"), null)
                folders.forEach { chip(if (it.id == "unfiled") t("Unfiled", "غير مصنف") else it.name, it.id) }
                val add = TextView(activity).apply {
                    text = "+"; textSize = 20f; gravity = Gravity.CENTER
                    setTextColor(colors.accent); background = rounded(colors.card, 16)
                    contentDescription = t("Create folder", "إنشاء مجلد")
                    WrenAdaptiveUi.prepareFocusable(this, activity, 16, colors.accent, if (isTvRemote) 60 else 48)
                    minimumWidth = dp(if (isTvRemote) 60 else 48)
                    setOnClickListener { createFolderDialog() }
                }
                folderBar.addView(add)
            }

            render = { rawQuery ->
                list.removeAllViews()
                val query = BrowserTextNormalizer.normalize(rawQuery)
                val rows: List<Triple<String, String, String>> = if (isHistory) {
                    history.map { Triple(it.title, it.url, "") }
                } else {
                    bookmarks.filter { selectedFolder == null || it.folderId == selectedFolder }
                        .map { bookmark -> Triple(bookmark.title, bookmark.url, bookmark.folderId) }
                }
                val filtered = rows.filter { (label, url, _) ->
                    query.isBlank() || listOf(label, url, Uri.parse(url).host.orEmpty())
                        .any { BrowserTextNormalizer.normalize(it).contains(query) }
                }
                val count = TextView(activity).apply {
                    text = if (BrowseXColors.isArabic(activity, store)) "${filtered.size} من ${rows.size} صفحة"
                        else "${filtered.size} of ${rows.size} pages"
                    textSize = if (isTvRemote) 15f else 11f; setTextColor(colors.muted); setPadding(dp(2), dp(4), dp(2), dp(8))
                }
                list.addView(count)
                if (filtered.isEmpty()) {
                    list.addView(TextView(activity).apply {
                        text = if (isHistory) t("Your browsing history is empty.", "سجل التصفح فارغ.")
                            else t("No bookmarks in this folder yet.", "لا توجد مفضلة في هذا المجلد بعد.")
                        textSize = if (isTvRemote) 18f else 13f; gravity = Gravity.CENTER; setTextColor(colors.muted)
                        setPadding(dp(20), dp(34), dp(20), dp(34)); background = rounded(colors.card, 16)
                    })
                }
                filtered.forEach { (label, url, folderId) ->
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(if (isTvRemote) 18 else 12), dp(if (isTvRemote) 18 else 11), dp(if (isTvRemote) 14 else 7), dp(if (isTvRemote) 18 else 11)); background = rounded(colors.card, 14)
                        isClickable = true
                        WrenAdaptiveUi.prepareFocusable(this, activity, 14, colors.accent, if (isTvRemote) 76 else 48)
                        contentDescription = label.ifBlank { Uri.parse(url).host.orEmpty().ifBlank { url } } + ", " + url
                    }
                    val details = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    details.addView(TextView(activity).apply {
                        text = label.ifBlank { Uri.parse(url).host.orEmpty().ifBlank { url } }
                        textSize = if (isTvRemote) 18f else 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(colors.ink); maxLines = 1
                    })
                    details.addView(TextView(activity).apply {
                        text = url; textSize = if (isTvRemote) 14f else 10f; setTextColor(colors.muted); maxLines = 2
                        setPadding(0, dp(3), 0, 0)
                    })
                    row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                    val remove = TextView(activity).apply {
                        text = "×"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(colors.danger)
                        contentDescription = if (isHistory) t("Remove from history", "حذف من السجل") else t("Remove bookmark", "إزالة من المفضلة")
                        WrenAdaptiveUi.prepareFocusable(this, activity, 12, colors.danger, if (isTvRemote) 60 else 48)
                        setOnClickListener {
                            activity.lifecycleScope.launch {
                                if (isHistory) historyRepo.remove(url) else bookmarkRepo.remove(url)
                                reload(); render(search.text?.toString().orEmpty())
                            }
                        }
                    }
                    row.addView(remove)
                    row.setOnClickListener { dialog.dismiss(); onOpen(url) }
                    if (!isHistory) row.setOnLongClickListener {
                        val actions = arrayOf(
                            t("Move to folder", "نقل إلى مجلد"),
                            t("Move up in folder", "تحريك للأعلى في المجلد"),
                            t("Move down in folder", "تحريك للأسفل في المجلد")
                        )
                        AlertDialog.Builder(activity).setTitle(t("Organize bookmark", "تنظيم المفضلة"))
                            .setItems(actions) { _, action ->
                                if (action == 0) {
                                    val currentFolders = folders
                                    val folderLabels = currentFolders.map {
                                        if (it.id == "unfiled") t("Unfiled", "غير مصنف") else it.name
                                    }.toTypedArray()
                                    AlertDialog.Builder(activity)
                                        .setTitle(t("Move bookmark to folder", "نقل المفضلة إلى مجلد"))
                                        .setItems(folderLabels) { _, index ->
                                            activity.lifecycleScope.launch {
                                                bookmarkRepo.move(url, currentFolders[index].id)
                                                reload(); render(search.text?.toString().orEmpty())
                                            }
                                        }.show()
                                } else {
                                    activity.lifecycleScope.launch {
                                        val direction = if (action == 1) -1 else 1
                                        val moved = bookmarkRepo.moveWithinFolder(url, folderId, direction)
                                        if (!moved) android.widget.Toast.makeText(
                                            activity,
                                            t("This bookmark is already at the edge of its folder.", "هذه المفضلة في طرف المجلد بالفعل."),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        reload(); render(search.text?.toString().orEmpty())
                                    }
                                }
                            }.show()
                        true
                    }
                    list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
                }
            }

            if (isHistory) {
                val clear = TextView(activity).apply {
                    text = t("Clear history", "مسح السجل"); textSize = if (isTvRemote) 16f else 12f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(colors.danger); setPadding(dp(8), dp(8), dp(4), dp(8))
                    WrenAdaptiveUi.prepareFocusable(this, activity, 14, colors.danger, if (isTvRemote) 60 else 48)
                    setOnClickListener {
                        AlertDialog.Builder(activity).setTitle(t("Clear browsing history?", "مسح سجل التصفح؟"))
                            .setNegativeButton(t("Cancel", "إلغاء"), null)
                            .setPositiveButton(t("Clear", "مسح")) { _, _ ->
                                activity.lifecycleScope.launch { historyRepo.clear(); reload(); render(search.text?.toString().orEmpty()) }
                            }.show()
                    }
                }
                root.addView(clear)
            }
            drawFolders()
            render("")
            search.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render(s?.toString().orEmpty())
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
            dialog = AlertDialog.Builder(activity).setView(root).create()
            close.setOnClickListener { dialog.dismiss() }
            dialog.setOnShowListener {
                dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            }
            dialog.show()
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            if (isTvRemote) search.post { if (dialog.isShowing) search.requestFocus() }
        }
    }
}
