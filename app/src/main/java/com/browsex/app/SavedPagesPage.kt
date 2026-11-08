package com.browsex.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.net.Uri

/** Searchable, persistent bookmarks/history library with explicit empty and no-match states. */
internal class SavedPagesPage(
    private val activity: MainActivity,
    private val store: BrowserStore,
    private val title: String,
    initialPages: List<String>,
    private val isHistory: Boolean,
    private val onOpen: (String) -> Unit
) {
    private val pages = initialPages.toMutableList()
    private var selectedFolderId: String? = null
    private var dismissDialog: (() -> Unit)? = null
    private val colors get() = BrowseXColors.resolve(activity, store)
    private val ink get() = colors.ink
    private val muted get() = colors.muted
    private val accent get() = colors.accent
    private fun t(en: String, ar: String) = BrowseXStrings.text(activity, en, ar)

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), this@SavedPagesPage.colors.line)
    }

    fun show() {
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
            text = if (title == "Bookmarks") t("Bookmarks", "المفضلة") else t("History", "السجل")
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
        })
        heading.addView(TextView(activity).apply {
            text = if (isHistory) t("Pages you have visited", "الصفحات التي زرتها") else t("Websites saved for later", "مواقع حفظتها لوقت لاحق")
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(activity).apply {
            text = "×"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(ink)
            background = rounded(colors.card, 14)
            contentDescription = t("Close", "إغلاق")
        }
        close.minimumWidth = dp(48)
        close.minimumHeight = dp(48)
        header.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)

        val search = EditText(activity).apply {
            hint = if (isHistory) t("Search browsing history", "ابحث في سجل التصفح") else t("Search bookmarks", "ابحث في المفضلة")
            textSize = 14f
            isSingleLine = true
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(15), 0, dp(15), 0)
            background = rounded(colors.card, 15)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(17)
        })
        val folderBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = if (isHistory) android.view.View.GONE else android.view.View.VISIBLE
            setPadding(0, dp(8), 0, dp(2))
        }
        root.addView(folderBar)

        val scroll = ScrollView(activity).apply { clipToPadding = false }
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        scroll.addView(list)
        val empty = TextView(activity).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(22), dp(38), dp(22), dp(38))
            background = rounded(colors.card, 16)
        }

        val tools = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(10), dp(2), dp(6))
        }
        val count = TextView(activity).apply {
            textSize = 12f
            setTextColor(muted)
        }
        tools.addView(count, LinearLayout.LayoutParams(0, -2, 1f))
        if (isHistory) {
            val clear = TextView(activity).apply {
                text = t("Clear history", "مسح السجل")
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(colors.danger)
                setPadding(dp(10), dp(8), dp(4), dp(8))
                setOnClickListener {
                    AlertDialog.Builder(activity)
                        .setTitle(t("Clear browsing history?", "مسح سجل التصفح؟"))
                        .setMessage("This removes the history saved by Browse X. Bookmarks and downloaded files are kept.")
                        .setNegativeButton(t("Cancel", "إلغاء"), null)
                        .setPositiveButton(t("Clear", "مسح")) { _, _ ->
                            store.clearHistory()
                            pages.clear()
                            search.text?.clear()
                            if (search.text.isNullOrEmpty()) {
                                list.removeAllViews()
                                count.text = if (BrowseXColors.isArabic(activity, store)) "0 من 0 صفحة" else "0 of 0 ${if (isHistory) "pages" else "saved sites"}"
                                empty.text = if (isHistory) t("Your browsing history is empty.", "سجل التصفح فارغ.") else t("No bookmarks yet. Save a page to find it here.", "لا توجد صفحات مفضلة بعد. احفظ صفحة لتظهر هنا.")
                                list.addView(empty, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                            }
                        }.show()
                }
            }
            tools.addView(clear)
        }
        root.addView(tools)

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun refresh(query: String) = render(query, list, count, empty)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, countBefore: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, countAfter: Int) = refresh(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        fun drawFolders() {
            folderBar.removeAllViews()
            val folders = store.bookmarkFolders()
            fun chip(label: String, id: String?) {
                val selected = selectedFolderId == id
                val view = TextView(activity).apply {
                    text = label
                    textSize = 11f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (selected) colors.accent else muted)
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    background = rounded(if (selected) colors.selected else colors.card, 18)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { selectedFolderId = id; drawFolders(); refresh(search.text?.toString().orEmpty()) }
                }
                if (id != null && id != BookmarkFolder.DEFAULT_ID) {
                    view.setOnLongClickListener {
                        AlertDialog.Builder(activity)
                            .setTitle(label)
                            .setItems(arrayOf(t("Rename folder", "إعادة تسمية المجلد"), t("Delete folder", "حذف المجلد"))) { _, action ->
                                if (action == 0) {
                                    val field = EditText(activity).apply { setText(label); maxLines = 1 }
                                    AlertDialog.Builder(activity)
                                        .setTitle(t("Rename folder", "إعادة تسمية المجلد"))
                                        .setView(field)
                                        .setNegativeButton(t("Cancel", "إلغاء"), null)
                                        .setPositiveButton(t("Save", "حفظ")) { _, _ ->
                                            if (!store.renameBookmarkFolder(id, field.text?.toString().orEmpty())) {
                                                android.widget.Toast.makeText(activity, t("Could not rename folder", "تعذرت إعادة تسمية المجلد"), android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            drawFolders()
                                            refresh(search.text?.toString().orEmpty())
                                        }.show()
                                } else {
                                    AlertDialog.Builder(activity)
                                        .setMessage(t("Bookmarks in this folder will move to Unfiled.", "ستُنقل المفضلة في هذا المجلد إلى غير مصنف."))
                                        .setNegativeButton(t("Cancel", "إلغاء"), null)
                                        .setPositiveButton(t("Delete", "حذف")) { _, _ ->
                                            store.removeBookmarkFolder(id)
                                            if (selectedFolderId == id) selectedFolderId = null
                                            drawFolders()
                                            refresh(search.text?.toString().orEmpty())
                                        }.show()
                                }
                            }
                            .show()
                        true
                    }
                }
                folderBar.addView(view, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
            }
            chip(t("All", "الكل"), null)
            folders.forEach { folder ->
                chip(if (folder.id == BookmarkFolder.DEFAULT_ID) t("Unfiled", "غير مصنف") else folder.name, folder.id)
            }
            val add = TextView(activity).apply {
                text = "+"
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(accent)
                setPadding(dp(12), dp(5), dp(12), dp(5))
                background = rounded(colors.card, 18)
                contentDescription = t("Create folder", "إنشاء مجلد")
                setOnClickListener {
                    val field = EditText(activity).apply {
                        hint = t("Folder name", "اسم المجلد")
                        maxLines = 1
                    }
                    AlertDialog.Builder(activity)
                        .setTitle(t("New bookmark folder", "مجلد مفضلة جديد"))
                        .setView(field)
                        .setNegativeButton(t("Cancel", "إلغاء"), null)
                        .setPositiveButton(t("Create", "إنشاء")) { _, _ ->
                            val created = store.createBookmarkFolder(field.text?.toString().orEmpty())
                            if (created == null) android.widget.Toast.makeText(activity, t("Folder name is invalid or already used", "اسم المجلد غير صالح أو مستخدم"), android.widget.Toast.LENGTH_SHORT).show()
                            else { selectedFolderId = created.id; drawFolders(); refresh(search.text?.toString().orEmpty()) }
                        }.show()
                }
            }
            folderBar.addView(add, LinearLayout.LayoutParams(dp(42), dp(38)))
        }
        drawFolders()
        refresh("")

        dialog = AlertDialog.Builder(activity).setView(root).create()
        close.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dismissDialog = { dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun render(
        query: String,
        list: LinearLayout,
        count: TextView,
        empty: TextView
    ) {
        list.removeAllViews()
        val normalized = BrowserTextNormalizer.normalize(query)
        val folderUrls = if (isHistory || selectedFolderId == null) null else
            store.bookmarkItems().filter { it.folderId == selectedFolderId }.map { it.url }.toSet()
        val bookmarkMetadata = if (isHistory) emptyMap() else store.bookmarkItems().associateBy { it.url }
        val historyMetadata = if (isHistory) store.historyEntries().associateBy { it.url } else emptyMap()
        fun pageTitle(url: String): String =
            (if (isHistory) historyMetadata[url]?.title else bookmarkMetadata[url]?.title)
                ?.takeIf { it.isNotBlank() } ?: Uri.parse(url).host.orEmpty().ifBlank { url }
        val filtered = pages.filter { page ->
            (folderUrls == null || page in folderUrls) &&
                (normalized.isBlank() || listOf(page, Uri.parse(page).host.orEmpty(), pageTitle(page))
                    .any { BrowserTextNormalizer.normalize(it).contains(normalized) })
        }
        count.text = if (BrowseXColors.isArabic(activity, store)) "${filtered.size} من ${pages.size} صفحة" else "${filtered.size} of ${pages.size} ${if (isHistory) "pages" else "saved sites"}"
        if (filtered.isEmpty()) {
            empty.text = when {
                pages.isEmpty() && isHistory -> "Your browsing history is empty."
                pages.isEmpty() -> "No bookmarks yet. Save a page to find it here."
                else -> t("No pages match your search.", "لا توجد صفحات تطابق البحث.")
            }
            list.addView(empty, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
            })
            return
        }
        filtered.forEach { page ->
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(12), dp(8), dp(12))
                background = rounded(colors.card, 15)
                isFocusable = true
                isClickable = true
            }
            val details = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            val host = Uri.parse(page).host ?: page
            details.addView(TextView(activity).apply {
                text = pageTitle(page)
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ink)
                maxLines = 1
            })
            details.addView(TextView(activity).apply {
                text = page
                textSize = 10f
                setTextColor(muted)
                maxLines = 2
                setPadding(0, dp(4), 0, 0)
            })
            item.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            val remove = TextView(activity).apply {
                text = "×"
                textSize = 23f
                gravity = Gravity.CENTER
                setTextColor(colors.danger)
                contentDescription = if (isHistory) t("Remove from history", "حذف من السجل") else t("Remove bookmark", "إزالة من المفضلة")
                setPadding(dp(12), dp(5), dp(8), dp(5))
                minimumWidth = dp(48)
                minimumHeight = dp(48)
                setOnClickListener {
                    if (isHistory) store.removeHistory(page) else store.removeBookmark(page)
                    pages.remove(page)
                    render(query, list, count, empty)
                }
            }
            item.addView(remove)
            item.setOnClickListener { onOpen(page); dismissDialog?.invoke() }
            if (!isHistory) item.setOnLongClickListener {
                val folders = store.bookmarkFolders()
                val labels = folders.map { if (it.id == BookmarkFolder.DEFAULT_ID) t("Unfiled", "غير مصنف") else it.name }.toTypedArray()
                val actions = arrayOf(
                    t("Move to folder", "نقل إلى مجلد"),
                    t("Move earlier", "نقل للأعلى"),
                    t("Move later", "نقل للأسفل")
                )
                AlertDialog.Builder(activity)
                    .setTitle(t("Manage bookmark", "إدارة المفضلة"))
                    .setItems(actions) { _, action ->
                        when (action) {
                            0 -> AlertDialog.Builder(activity)
                                .setTitle(t("Move bookmark to folder", "نقل المفضلة إلى مجلد"))
                                .setItems(labels) { _, index ->
                                    store.moveBookmark(page, folders[index].id)
                                    render(query, list, count, empty)
                                }
                                .setNegativeButton(t("Cancel", "إلغاء"), null)
                                .show()
                            1 -> { store.reorderBookmark(page, -1, selectedFolderId); render(query, list, count, empty) }
                            2 -> { store.reorderBookmark(page, 1, selectedFolderId); render(query, list, count, empty) }
                        }
                    }
                    .setNegativeButton(t("Cancel", "إلغاء"), null)
                    .show()
                true
            }
            list.addView(item, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }
    }
}
