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
    private val onRestore: (List<String>, Int) -> Unit,
    private val onOpenAlongside: (List<String>, Int) -> Unit
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
                    val importButton = android.widget.Button(activity).apply {
                        text = t("Import session from text", "استيراد جلسة من نص")
                        setOnClickListener { promptImport(repository, ::render) }
                    }
                    val content = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(importButton, LinearLayout.LayoutParams(-1, WrenAdaptiveUi.dp(activity, 48)))
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
                        .setNeutralButton(t("Delete all", "حذف الكل"), null)
                        .setNegativeButton(t("Close", "إغلاق"), null)
                        .create()
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
                            isEnabled = sessions.isNotEmpty()
                            setOnClickListener {
                                AlertDialog.Builder(activity)
                                    .setTitle(t("Delete all saved sessions?", "حذف جميع الجلسات المحفوظة؟"))
                                    .setMessage(t("This cannot be undone. Open tabs will not be affected.", "لا يمكن التراجع عن هذا الإجراء. لن تتأثر التبويبات المفتوحة."))
                                    .setNegativeButton(t("Cancel", "إلغاء"), null)
                                    .setPositiveButton(t("Delete all", "حذف الكل")) { _, _ ->
                                        activity.lifecycleScope.launch {
                                            sessions.forEach { session -> repository.deleteSession(session.id) }
                                            dialog.dismiss()
                                            render()
                                        }
                                    }
                                    .show()
                            }
                        }
                    }
                    list.setOnItemClickListener { _, _, position, _ ->
                        val selected = visibleSessions.getOrNull(position) ?: return@setOnItemClickListener
                        AlertDialog.Builder(activity)
                            .setTitle(selected.name)
                            .setItems(arrayOf(
                                t("Replace current tabs", "استبدال التبويبات الحالية"),
                                t("Open alongside current tabs", "فتح بجانب التبويبات الحالية"),
                                t("Cancel", "إلغاء")
                            )) { _, action ->
                                when (action) {
                                    0 -> {
                                        onRestore(selected.urls, selected.activeIndex)
                                        dialog.dismiss()
                                    }
                                    1 -> {
                                        onOpenAlongside(selected.urls, selected.activeIndex)
                                        dialog.dismiss()
                                    }
                                }
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
                                        append("\n\n")
                                        selected.urls.forEachIndexed { index, url ->
                                            append(index + 1)
                                            append(". ")
                                            append(url)
                                            append('\n')
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

    private fun promptImport(repository: com.browsex.app.storage.RoomSessionRepository, refresh: () -> Unit) {
        val nameInput = EditText(activity).apply {
            hint = t("Session name", "اسم الجلسة")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }
        val urlsInput = EditText(activity).apply {
            hint = t("Paste a shared session or one website URL per line", "الصق جلسة مشتركة أو رابط موقع في كل سطر")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 6
            maxLines = 10
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            BrowseXColors.applyDirection(this, activity.currentBrowserSettings)
        }
        val fields = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(nameInput, LinearLayout.LayoutParams(-1, dp(52)))
            addView(urlsInput, LinearLayout.LayoutParams(-1, dp(180)))
        }
        AlertDialog.Builder(activity)
            .setTitle(t("Import browsing session", "استيراد جلسة تصفح"))
            .setView(fields)
            .setNegativeButton(t("Cancel", "إلغاء"), null)
            .setPositiveButton(t("Import", "استيراد")) { _, _ ->
                val urls = urlsInput.text?.toString().orEmpty()
                    .lineSequence()
                    .map { it.trim().replace(Regex("^\\\\d+[.)]\\\\s*"), "") }
                    .filter { it.startsWith("https://", true) || it.startsWith("http://", true) }
                    .filter(NavigationInput::isValidWebUrl)
                    .distinct()
                    .take(50)
                    .toList()
                if (urls.isEmpty()) {
                    android.widget.Toast.makeText(activity, t("No valid website URLs found", "لم يتم العثور على روابط مواقع صالحة"), android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                activity.lifecycleScope.launch {
                    val saved = runCatching {
                        repository.saveSession(nameInput.text?.toString().orEmpty(), urls, 0)
                    }.onFailure { android.util.Log.e("Wren", "Could not import browsing session", it) }
                        .getOrDefault(false)
                    android.widget.Toast.makeText(
                        activity,
                        if (saved) t("Session imported", "تم استيراد الجلسة")
                        else t("Could not import. Check the name and the 10-session limit.", "تعذر الاستيراد. تحقق من الاسم وحد 10 جلسات."),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    if (saved) refresh()
                }
            }
            .show()
    }

    private fun dp(value: Int): Int = WrenAdaptiveUi.dp(activity, value)

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
