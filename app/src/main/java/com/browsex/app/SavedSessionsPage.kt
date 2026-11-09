package com.browsex.app

import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.browsex.app.storage.RoomSavedSession

/** Manage named snapshots of regular tabs without mixing private browsing into durable sessions. */
internal class SavedSessionsPage(
    private val activity: MainActivity,
    private val currentUrls: List<String>,
    private val activeIndex: Int,
    private val onRestore: (List<String>, Int) -> Unit
) {
    private fun t(en: String, ar: String) = BrowseXStrings.text(activity, en, ar)

    fun show() {
        activity.lifecycleScope.launch {
            val repository = runCatching { activity.storageRuntime.awaitReady().sessions }
                .onFailure { android.util.Log.e("Wren", "Could not open saved sessions", it) }
                .getOrNull() ?: run {
                    android.widget.Toast.makeText(activity, t("Could not load saved sessions", "تعذر تحميل الجلسات المحفوظة"), android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
            fun render() {
                activity.lifecycleScope.launch {
                    val sessions = runCatching { repository.sessions() }.getOrDefault(emptyList())
                    val list = ListView(activity)
                    var visibleSessions = sessions.sortedByDescending { it.savedAt }
                    var sortMode = 0
                    var currentQuery = ""
                    val adapter = ArrayAdapter(
                        activity,
                        android.R.layout.simple_list_item_2,
                        android.R.id.text1,
                        visibleSessions.map { "${it.name}\n${it.urls.size} " + t("tabs", "تبويبات") + " · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.savedAt)) }
                    )
                    list.adapter = adapter
                    val empty = android.widget.TextView(activity).apply {
                        gravity = android.view.Gravity.CENTER
                        setPadding(32, 32, 32, 32)
                    }
                    fun updateEmptyState() {
                        empty.text = when {
                            sessions.isEmpty() -> t("No saved sessions yet. Save your current tabs to return to them later.", "لا توجد جلسات محفوظة بعد. احفظ التبويبات الحالية للعودة إليها لاحقًا.")
                            else -> t("No sessions match your search.", "لا توجد جلسات تطابق البحث.")
                        }
                    }
                    updateEmptyState()
                    val search = EditText(activity).apply {
                        hint = t("Search saved sessions", "ابحث في الجلسات المحفوظة")
                        inputType = InputType.TYPE_CLASS_TEXT
                        setSingleLine(true)
                        setPadding(WrenAdaptiveUi.dp(activity, 14), WrenAdaptiveUi.dp(activity, 10), WrenAdaptiveUi.dp(activity, 14), WrenAdaptiveUi.dp(activity, 10))
                        BrowseXColors.applyDirection(this, activity.currentBrowserSettings)
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                currentQuery = TabSearchPolicy.normalize(s?.toString().orEmpty())
                                visibleSessions = sessions.filter { session ->
                                    TabSearchPolicy.matchesNormalized(session.name, session.urls.joinToString(" "), currentQuery)
                                }.let { filtered ->
                                    when (sortMode) {
                                        1 -> filtered.sortedBy { it.savedAt }
                                        2 -> filtered.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
                                        else -> filtered.sortedByDescending { it.savedAt }
                                    }
                                }
                                adapter.clear()
                                adapter.addAll(visibleSessions.map { "${it.name}\n${it.urls.size} " + t("tabs", "تبويبات") + " · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.savedAt)) })
                                updateEmptyState()
                            }
                            override fun afterTextChanged(s: android.text.Editable?) = Unit
                        })
                    }
                    val clearSearch = android.widget.TextView(activity).apply {
                        text = "×"
                        textSize = 24f
                        gravity = android.view.Gravity.CENTER
                        contentDescription = t("Clear search", "مسح البحث")
                        visibility = View.GONE
                        isFocusable = true
                        isClickable = true
                        setOnClickListener {
                            search.text?.clear()
                            search.requestFocus()
                        }
                        setPadding(WrenAdaptiveUi.dp(activity, 12), 0, WrenAdaptiveUi.dp(activity, 12), 0)
                    }
                    search.addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            clearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                        }
                        override fun afterTextChanged(s: android.text.Editable?) = Unit
                    })
                    val searchRow = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        addView(search, LinearLayout.LayoutParams(0, WrenAdaptiveUi.dp(activity, 52), 1f))
                        addView(clearSearch, LinearLayout.LayoutParams(WrenAdaptiveUi.dp(activity, 48), WrenAdaptiveUi.dp(activity, 52)))
                    }
                    val sortButton = android.widget.Button(activity).apply {
                        text = t("Sort: newest", "الترتيب: الأحدث")
                        setOnClickListener {
                            sortMode = (sortMode + 1) % 3
                            visibleSessions = sessions.filter { session ->
                                TabSearchPolicy.matchesNormalized(session.name, session.urls.joinToString(" "), currentQuery)
                            }.let { filtered ->
                                when (sortMode) {
                                    1 -> filtered.sortedBy { it.savedAt }
                                    2 -> filtered.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
                                    else -> filtered.sortedByDescending { it.savedAt }
                                }
                            }
                            text = when (sortMode) {
                                1 -> t("Sort: oldest", "الترتيب: الأقدم")
                                2 -> t("Sort: name", "الترتيب: الاسم")
                                else -> t("Sort: newest", "الترتيب: الأحدث")
                            }
                            adapter.clear()
                            adapter.addAll(visibleSessions.map { "${it.name}\n${it.urls.size} " + t("tabs", "تبويبات") + " · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.savedAt)) })
                            updateEmptyState()
                        }
                    }
                    val content = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(searchRow, LinearLayout.LayoutParams(-1, WrenAdaptiveUi.dp(activity, 52)))
                        addView(sortButton, LinearLayout.LayoutParams(-1, WrenAdaptiveUi.dp(activity, 48)))
                        addView(list, LinearLayout.LayoutParams(-1, WrenAdaptiveUi.dp(activity, 320)))
                        addView(empty, LinearLayout.LayoutParams(-1, WrenAdaptiveUi.dp(activity, 160)))
                        list.emptyView = empty
                    }
                    val dialog = AlertDialog.Builder(activity)
                        .setTitle(t("Saved sessions", "الجلسات المحفوظة"))
                        .setView(content)
                        .setPositiveButton(t("Save current tabs", "حفظ التبويبات الحالية")) { _, _ -> promptSave(repository, ::render) }
                        .setNegativeButton(t("Close", "إغلاق"), null)
                        .create()
                    list.setOnItemClickListener { _, _, position, _ ->
                        val selected = visibleSessions.getOrNull(position) ?: return@setOnItemClickListener
                        AlertDialog.Builder(activity)
                            .setTitle(selected.name)
                            .setMessage(t("Replace the current regular tabs with this saved session?", "استبدال التبويبات العادية الحالية بهذه الجلسة المحفوظة؟"))
                            .setNegativeButton(t("Cancel", "إلغاء"), null)
                            .setPositiveButton(t("Restore", "استعادة")) { _, _ ->
                                onRestore(selected.urls, selected.activeIndex)
                                dialog.dismiss()
                            }
                            .show()
                    }
                    list.setOnItemLongClickListener { _, _, position, _ ->
                        val selected = visibleSessions.getOrNull(position) ?: return@setOnItemLongClickListener true
                        AlertDialog.Builder(activity)
                            .setTitle(selected.name)
                            .setItems(arrayOf(t("Rename session", "إعادة تسمية الجلسة"), t("Duplicate session", "نسخ الجلسة"), t("Share session", "مشاركة الجلسة"), t("Delete session", "حذف الجلسة"))) { _, action ->
                                if (action == 0) {
                                    val input = EditText(activity).apply {
                                        setText(selected.name)
                                        setSelection(text.length)
                                        hint = t("Session name", "اسم الجلسة")
                                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                                        setSingleLine(true)
                                    }
                                    AlertDialog.Builder(activity)
                                        .setTitle(t("Rename session", "إعادة تسمية الجلسة"))
                                        .setView(input)
                                        .setNegativeButton(t("Cancel", "إلغاء"), null)
                                        .setPositiveButton(t("Save", "حفظ")) { _, _ ->
                                            activity.lifecycleScope.launch {
                                                val renamed = runCatching {
                                                    repository.renameSession(selected.id, input.text?.toString().orEmpty())
                                                }.onFailure { android.util.Log.e("Wren", "Could not rename saved session", it) }
                                                    .getOrDefault(false)
                                                android.widget.Toast.makeText(
                                                    activity,
                                                    if (renamed) t("Session renamed", "تمت إعادة تسمية الجلسة")
                                                    else t("Name is empty or already used. Choose a unique name up to 40 characters.", "الاسم فارغ أو مستخدم. اختر اسمًا فريدًا لا يتجاوز 40 حرفًا."),
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                                if (renamed) {
                                                    dialog.dismiss()
                                                    render()
                                                }
                                            }
                                        }
                                        .show()
                                } else if (action == 1) {
                                    val input = EditText(activity).apply {
                                        setText("${selected.name} (copy)")
                                        setSelection(text.length)
                                        hint = t("New session name", "اسم الجلسة الجديدة")
                                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                                        setSingleLine(true)
                                    }
                                    AlertDialog.Builder(activity)
                                        .setTitle(t("Duplicate session", "نسخ الجلسة"))
                                        .setView(input)
                                        .setNegativeButton(t("Cancel", "إلغاء"), null)
                                        .setPositiveButton(t("Save copy", "حفظ النسخة")) { _, _ ->
                                            activity.lifecycleScope.launch {
                                                val duplicated = runCatching {
                                                    repository.saveSession(input.text?.toString().orEmpty(), selected.urls, selected.activeIndex)
                                                }.onFailure { android.util.Log.e("Wren", "Could not duplicate saved session", it) }.getOrDefault(false)
                                                android.widget.Toast.makeText(
                                                    activity,
                                                    if (duplicated) t("Session duplicated", "تم نسخ الجلسة")
                                                    else t("Could not duplicate. Check the name and the 10-session limit.", "تعذر نسخ الجلسة. تحقق من الاسم وحد 10 جلسات."),
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                                if (duplicated) {
                                                    dialog.dismiss()
                                                    render()
                                                }
                                            }
                                        }
                                        .show()
                                } else if (action == 2) {
                                    val shareText = buildString {
                                        append(selected.name)
                                        append("\\n\\n")
                                        selected.urls.forEachIndexed { index, url ->
                                            append(index + 1)
                                            append(". ")
                                            append(url)
                                            append('\\n')
                                        }
                                    }
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, selected.name)
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                    }
                                    runCatching {
                                        activity.startActivity(android.content.Intent.createChooser(send, t("Share browsing session", "مشاركة جلسة التصفح")))
                                    }.onFailure {
                                        android.widget.Toast.makeText(activity, t("No app available to share this session", "لا يوجد تطبيق متاح لمشاركة هذه الجلسة"), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    AlertDialog.Builder(activity)
                                        .setTitle(t("Delete session?", "حذف الجلسة؟"))
                                        .setMessage(selected.name)
                                        .setNegativeButton(t("Cancel", "إلغاء"), null)
                                        .setPositiveButton(t("Delete", "حذف")) { _, _ ->
                                            activity.lifecycleScope.launch {
                                                repository.deleteSession(selected.id)
                                                dialog.dismiss()
                                                render()
                                            }
                                        }
                                        .show()
                                }
                            }
                            .setNegativeButton(t("Cancel", "إلغاء"), null)
                            .show()
                        true
                    }
                    dialog.show()
                }
            }
            render()
        }
    }

    private fun promptSave(repository: com.browsex.app.storage.RoomSessionRepository, refresh: () -> Unit) {
        if (currentUrls.isEmpty()) {
            android.widget.Toast.makeText(activity, t("There are no regular tabs to save", "لا توجد تبويبات عادية لحفظها"), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(activity).apply {
            hint = t("Session name", "اسم الجلسة")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }
        AlertDialog.Builder(activity)
            .setTitle(t("Save current tabs", "حفظ التبويبات الحالية"))
            .setView(input)
            .setNegativeButton(t("Cancel", "إلغاء"), null)
            .setPositiveButton(t("Save", "حفظ")) { _, _ ->
                val name = input.text?.toString().orEmpty()
                activity.lifecycleScope.launch {
                    val saved = runCatching {
                        repository.saveSession(name, currentUrls, activeIndex)
                    }.onFailure { android.util.Log.e("Wren", "Could not save session", it) }.getOrDefault(false)
                    android.widget.Toast.makeText(
                        activity,
                        if (saved) t("Session saved", "تم حفظ الجلسة") else t("Could not save session. Use a name up to 40 characters; up to 10 sessions are supported.", "تعذر حفظ الجلسة. استخدم اسمًا لا يتجاوز 40 حرفًا؛ الحد الأقصى 10 جلسات."),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    if (saved) refresh()
                }
            }
            .show()
    }
}
