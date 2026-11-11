package com.browsex.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.browsex.app.storage.BookmarkEntity
import com.browsex.app.storage.BookmarkFolderEntity
import com.browsex.app.storage.HistoryEntryEntity
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal data class BrowseXSavedPagesState(
    val isHistory: Boolean = false,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val folders: List<BookmarkFolderEntity> = emptyList(),
    val history: List<HistoryEntryEntity> = emptyList()
)

@Composable
internal fun BrowseXSavedPagesScreen(
    state: BrowseXSavedPagesState,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRenameBookmark: (String, String) -> Unit,
    onMoveBookmark: (String, String) -> Unit,
    onMoveWithinFolder: (String, String, Int) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onClearHistory: () -> Unit,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val context = LocalContext.current
    fun t(en: String, ar: String = en): String = BrowseXStrings.text(context, en, ar)
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(0) }
    var editBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var editTitle by remember(editBookmark?.url) { mutableStateOf(editBookmark?.title.orEmpty()) }
    var moveBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var createFolderOpen by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var manageFoldersOpen by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<BookmarkFolderEntity?>(null) }
    var renameFolderName by remember(renameFolder?.id) { mutableStateOf(renameFolder?.name.orEmpty()) }
    var confirmDeleteFolder by remember { mutableStateOf<BookmarkFolderEntity?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }

    val needle = query.trim().lowercase(Locale.ROOT)
    val visibleBookmarks = state.bookmarks.asSequence()
        .filter { selectedFolder == null || it.folderId == selectedFolder }
        .filter { needle.isBlank() || it.title.lowercase(Locale.ROOT).contains(needle) || it.url.lowercase(Locale.ROOT).contains(needle) }
        .let { sequence ->
            when (sortMode) {
                1 -> sequence.sortedBy { it.title.lowercase(Locale.ROOT) }
                2 -> sequence.sortedByDescending { it.createdAt }
                else -> sequence.sortedWith(compareBy<BookmarkEntity> { it.folderId }.thenBy { it.sortOrder }.thenByDescending { it.createdAt })
            }
        }.toList()
    val visibleHistory = state.history.asSequence()
        .filter { needle.isBlank() || it.title.lowercase(Locale.ROOT).contains(needle) || it.url.lowercase(Locale.ROOT).contains(needle) }
        .let { sequence ->
            when (sortMode) {
                1 -> sequence.sortedBy { it.title.lowercase(Locale.ROOT) }
                2 -> sequence.sortedBy { it.visitedAt }
                else -> sequence.sortedByDescending { it.visitedAt }
            }
        }.toList()

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
                            text = if (state.isHistory) t("History", "السجل") else t("Bookmarks", "المفضلة"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.isHistory) "${state.history.size} saved visits" else "${state.bookmarks.size} saved pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.isHistory) {
                        TextButton(onClick = { confirmClearHistory = true }) {
                            Text(t("Clear history", "مسح السجل"))
                        }
                    } else {
                        IconButton(onClick = { createFolderOpen = true }) {
                            Icon(Icons.Default.Add, contentDescription = t("New folder", "مجلد جديد"))
                        }
                        IconButton(onClick = { manageFoldersOpen = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = t("Manage folders", "إدارة المجلدات"))
                        }
                    }
                    TextButton(onClick = onDismiss) { Text(t("Done", "تم")) }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(160) },
                    modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                    singleLine = true,
                    label = { Text(t(if (state.isHistory) "Search history" else "Search bookmarks", if (state.isHistory) "بحث في السجل" else "بحث في المفضلة")) }
                )

                if (!state.isHistory) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedFolder == null,
                            onClick = { selectedFolder = null },
                            label = { Text(t("All", "الكل")) }
                        )
                        FilterChip(
                            selected = selectedFolder == "unfiled",
                            onClick = { selectedFolder = "unfiled" },
                            label = { Text(t("Unfiled", "غير مصنف")) }
                        )
                        state.folders.filter { it.id != "unfiled" }.forEach { folder ->
                            FilterChip(
                                selected = selectedFolder == folder.id,
                                onClick = { selectedFolder = folder.id },
                                label = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 5.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { sortMode = (sortMode + 1) % 3 }) {
                        Text(
                            t(
                                when (sortMode) {
                                    1 -> "Sort: A–Z"
                                    2 -> if (state.isHistory) "Sort: oldest" else "Sort: newest"
                                    else -> if (state.isHistory) "Sort: newest" else "Sort: saved order"
                                },
                                when (sortMode) {
                                    1 -> "الترتيب: أ–ي"
                                    2 -> if (state.isHistory) "الأقدم أولاً" else "الأحدث أولاً"
                                    else -> if (state.isHistory) "الأحدث أولاً" else "ترتيب الحفظ"
                                }
                            )
                        )
                    }
                }

                if (state.isHistory) {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(visibleHistory, key = { it.id }) { item ->
                            SavedPageRow(
                                title = item.title,
                                subtitle = item.url,
                                metadata = t("Visited", "تمت الزيارة") + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.visitedAt)),
                                isHistory = true,
                                onOpen = { onOpen(item.url) },
                                onRemove = { onRemove(item.url) },
                                onShare = { onShare(item.url) },
                                onCopy = { onCopy(item.url) }
                            )
                        }
                        if (visibleHistory.isEmpty()) item {
                            EmptySavedPages(t("No history matches this search", "لا توجد نتائج في السجل"))
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(visibleBookmarks, key = { it.id }) { item ->
                            SavedBookmarkRow(
                                item = item,
                                onOpen = { onOpen(item.url) },
                                onRemove = { onRemove(item.url) },
                                onShare = { onShare(item.url) },
                                onCopy = { onCopy(item.url) },
                                onEdit = {
                                    editBookmark = item
                                    editTitle = item.title
                                },
                                onMove = { moveBookmark = item },
                                onMoveUp = { onMoveWithinFolder(item.url, item.folderId, -1) },
                                onMoveDown = { onMoveWithinFolder(item.url, item.folderId, 1) }
                            )
                        }
                        if (visibleBookmarks.isEmpty()) item {
                            EmptySavedPages(t("No bookmarks match this search", "لا توجد مفضلة تطابق البحث"))
                        }
                    }
                }
            }
        }
    }

    if (editBookmark != null) {
        AlertDialog(
            onDismissRequest = { editBookmark = null },
            title = { Text(t("Edit bookmark title", "تعديل عنوان المفضلة")) },
            text = {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it.take(160) },
                    singleLine = true,
                    label = { Text(t("Title", "العنوان")) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editBookmark?.let { onRenameBookmark(it.url, editTitle) }
                    editBookmark = null
                }) { Text(t("Save", "حفظ")) }
            },
            dismissButton = {
                TextButton(onClick = { editBookmark = null }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }

    if (moveBookmark != null) {
        AlertDialog(
            onDismissRequest = { moveBookmark = null },
            title = { Text(t("Move bookmark to folder", "نقل المفضلة إلى مجلد")) },
            text = {
                Column {
                    state.folders.forEach { folder ->
                        TextButton(
                            onClick = {
                                moveBookmark?.let { onMoveBookmark(it.url, folder.id) }
                                moveBookmark = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (folder.id == "unfiled") t("Unfiled", "غير مصنف") else folder.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveBookmark = null }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }

    if (createFolderOpen) {
        AlertDialog(
            onDismissRequest = { createFolderOpen = false },
            title = { Text(t("New bookmark folder", "مجلد مفضلة جديد")) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it.take(40) },
                    singleLine = true,
                    label = { Text(t("Folder name", "اسم المجلد")) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCreateFolder(folderName)
                    folderName = ""
                    createFolderOpen = false
                }) { Text(t("Create", "إنشاء")) }
            },
            dismissButton = {
                TextButton(onClick = { createFolderOpen = false }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }

    if (manageFoldersOpen) {
        AlertDialog(
            onDismissRequest = { manageFoldersOpen = false },
            title = { Text(t("Manage folders", "إدارة المجلدات")) },
            text = {
                Column {
                    state.folders.filter { it.id != "unfiled" }.forEach { folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(folder.name, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                            IconButton(onClick = {
                                renameFolder = folder
                                renameFolderName = folder.name
                                manageFoldersOpen = false
                            }) { Icon(Icons.Default.Edit, contentDescription = t("Rename folder", "إعادة تسمية")) }
                            IconButton(onClick = {
                                confirmDeleteFolder = folder
                                manageFoldersOpen = false
                            }) { Icon(Icons.Default.DeleteOutline, contentDescription = t("Delete folder", "حذف المجلد")) }
                        }
                    }
                    if (state.folders.none { it.id != "unfiled" }) {
                        Text(t("No custom folders yet", "لا توجد مجلدات مخصصة بعد"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { manageFoldersOpen = false }) { Text(t("Done", "تم")) }
            }
        )
    }

    if (renameFolder != null) {
        AlertDialog(
            onDismissRequest = { renameFolder = null },
            title = { Text(t("Rename folder", "إعادة تسمية المجلد")) },
            text = {
                OutlinedTextField(
                    value = renameFolderName,
                    onValueChange = { renameFolderName = it.take(40) },
                    singleLine = true,
                    label = { Text(t("Folder name", "اسم المجلد")) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameFolder?.let { onRenameFolder(it.id, renameFolderName) }
                    renameFolder = null
                }) { Text(t("Save", "حفظ")) }
            },
            dismissButton = {
                TextButton(onClick = { renameFolder = null }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }

    if (confirmDeleteFolder != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteFolder = null },
            title = { Text(t("Delete folder?", "حذف المجلد؟")) },
            text = { Text(t("Bookmarks in this folder will move to Unfiled.", "ستُنقل المفضلة في هذا المجلد إلى غير مصنف.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteFolder?.let { onDeleteFolder(it.id) }
                    if (selectedFolder == confirmDeleteFolder?.id) selectedFolder = null
                    confirmDeleteFolder = null
                }) { Text(t("Delete", "حذف")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFolder = null }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text(t("Clear browsing history?", "مسح سجل التصفح؟")) },
            text = { Text(t("This removes the saved browsing history from this device.", "سيؤدي هذا إلى حذف سجل التصفح المحفوظ على هذا الجهاز.")) },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    confirmClearHistory = false
                }) { Text(t("Clear", "مسح")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text(t("Cancel", "إلغاء")) }
            }
        )
    }
}

@Composable
private fun SavedBookmarkRow(
    item: BookmarkEntity,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val host = runCatching { android.net.Uri.parse(item.url).host?.removePrefix("www.") }.getOrNull().orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(start = 13.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(item.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(host.ifBlank { item.url }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Bookmark actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit title") }, onClick = { menuOpen = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                    DropdownMenuItem(text = { Text("Move to folder") }, onClick = { menuOpen = false; onMove() }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) })
                    DropdownMenuItem(text = { Text("Move up") }, onClick = { menuOpen = false; onMoveUp() }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) })
                    DropdownMenuItem(text = { Text("Move down") }, onClick = { menuOpen = false; onMoveDown() }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menuOpen = false; onShare() })
                    DropdownMenuItem(text = { Text("Copy address") }, onClick = { menuOpen = false; onCopy() })
                    DropdownMenuItem(text = { Text("Remove") }, onClick = { menuOpen = false; onRemove() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) })
                }
            }
        }
    }
}

@Composable
private fun SavedPageRow(
    title: String,
    subtitle: String,
    metadata: String,
    isHistory: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val host = runCatching { android.net.Uri.parse(subtitle).host?.removePrefix("www.") }.getOrNull().orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(start = 13.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isHistory) Icons.Default.History else Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(host.ifBlank { subtitle }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(metadata, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Page actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menuOpen = false; onShare() })
                    DropdownMenuItem(text = { Text("Copy address") }, onClick = { menuOpen = false; onCopy() })
                    DropdownMenuItem(text = { Text("Remove") }, onClick = { menuOpen = false; onRemove() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) })
                }
            }
        }
    }
}

@Composable
private fun EmptySavedPages(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 28.dp, horizontal = 8.dp)
    )
}
