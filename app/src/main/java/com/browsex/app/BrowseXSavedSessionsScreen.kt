package com.browsex.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.browsex.app.storage.RoomSavedSession
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BrowseXSavedSessionsScreen(
    sessions: List<RoomSavedSession>,
    onDismiss: () -> Unit,
    onRestore: (RoomSavedSession) -> Unit,
    onOpenAlongside: (RoomSavedSession) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en) = BrowseXStrings.text(context, en, ar)
    var query by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<RoomSavedSession?>(null) }
    var renameValue by remember(renameTarget?.id) { mutableStateOf(renameTarget?.name.orEmpty()) }
    var deleteTarget by remember { mutableStateOf<RoomSavedSession?>(null) }
    val needle = query.trim().lowercase(Locale.ROOT)
    val filtered = sessions.filter { needle.isBlank() || it.name.lowercase(Locale.ROOT).contains(needle) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t("Saved sessions", "الجلسات المحفوظة"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${sessions.size} / 10",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text(t("Done", "تم")) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                    label = { Text(t("Search sessions", "البحث في الجلسات")) }
                )
                if (filtered.isEmpty()) {
                    Text(
                        t("No saved sessions found.", "لم يتم العثور على جلسات محفوظة."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { session ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(13.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                session.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${session.urls.size} tabs · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.savedAt)),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = {
                                            renameTarget = session
                                            renameValue = session.name
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = t("Rename session", "إعادة تسمية الجلسة"))
                                        }
                                        IconButton(onClick = { deleteTarget = session }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = t("Delete session", "حذف الجلسة"))
                                        }
                                    }
                                    Text(
                                        session.urls.take(3).joinToString(" · ") { url ->
                                            runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull().orEmpty().ifBlank { url }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(onClick = { onRestore(session) }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Restore, contentDescription = null)
                                            Text(t("Restore", "استعادة"), modifier = Modifier.padding(start = 5.dp))
                                        }
                                        TextButton(onClick = { onOpenAlongside(session) }, modifier = Modifier.weight(1f)) {
                                            Text(t("Open alongside", "فتح بجانب الحالي"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(t("Rename session", "إعادة تسمية الجلسة")) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it.take(40) },
                    singleLine = true,
                    label = { Text(t("Session name", "اسم الجلسة")) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameTarget?.let { onRename(it.id, renameValue) }
                    renameTarget = null
                }) { Text(t("Save", "حفظ")) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(t("Delete saved session?", "حذف الجلسة المحفوظة؟")) },
            text = { Text(t("This removes the saved snapshot. It does not close your current tabs.", "سيؤدي هذا إلى حذف النسخة المحفوظة فقط ولن يغلق التبويبات الحالية.")) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget?.let { onDelete(it.id) }
                    deleteTarget = null
                }) { Text(t("Delete", "حذف")) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }
}
