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
                    list.adapter = ArrayAdapter(
                        activity,
                        android.R.layout.simple_list_item_2,
                        android.R.id.text1,
                        sessions.map { "${it.name}\n${it.urls.size} " + t("tabs", "تبويبات") + " · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.savedAt)) }
                    )
                    val empty = android.widget.TextView(activity).apply {
                        text = t("No saved sessions yet. Save your current tabs to return to them later.", "لا توجد جلسات محفوظة بعد. احفظ التبويبات الحالية للعودة إليها لاحقًا.")
                        gravity = android.view.Gravity.CENTER
                        setPadding(32, 32, 32, 32)
                    }
                    val content = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
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
                        val selected = sessions.getOrNull(position) ?: return@setOnItemClickListener
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
                        val selected = sessions.getOrNull(position) ?: return@setOnItemLongClickListener true
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
                            }.show()
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
