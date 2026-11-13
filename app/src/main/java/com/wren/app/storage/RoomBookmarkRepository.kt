package com.wren.app.storage

import com.wren.app.NavigationInput
import com.wren.app.BookmarkImportPreview
import java.util.UUID

/**
 * Room-backed bookmark and folder operations. The Activity's library and backup flows
 * use this repository directly; the clean Wren database has no legacy-data import step.
 */
internal class RoomBookmarkRepository(
    private val dao: WrenStorageDao
) {
    suspend fun all(): List<BookmarkEntity> = dao.bookmarks(WrenStorageDao.MAX_BOOKMARKS)

    /** Portable bookmark-only backup; excludes history, sessions, cookies and credentials. */
    suspend fun exportBookmarks(): String {
        val root = org.json.JSONObject()
            .put("format", "wren-bookmarks")
            .put("version", 1)
        val folderArray = org.json.JSONArray()
        dao.folders().forEach { folder ->
            folderArray.put(org.json.JSONObject().put("id", folder.id).put("name", folder.name))
        }
        val bookmarkArray = org.json.JSONArray()
        dao.bookmarks(WrenStorageDao.MAX_BOOKMARKS).forEach { bookmark ->
            bookmarkArray.put(org.json.JSONObject()
                .put("id", bookmark.id)
                .put("url", bookmark.url)
                .put("title", bookmark.title)
                .put("folderId", bookmark.folderId)
                .put("createdAt", bookmark.createdAt))
        }
        return root.put("folders", folderArray).put("bookmarks", bookmarkArray).toString(2)
    }

    /** Standard Netscape bookmark HTML for import into other browsers. */
    suspend fun exportBookmarksHtml(): String =
        BookmarkHtmlCodec.fromBookmarks(dao.folders(), dao.bookmarks(WrenStorageDao.MAX_BOOKMARKS))

    /**
     * Imports a versioned backup as a merge. Existing bookmarks are never overwritten;
     * malformed or unsafe rows are skipped and the payload is size/count bounded.
     */
    suspend fun importBookmarks(payload: String, merge: Boolean = true): BookmarkBackupImportResult {
        require(payload.length <= MAX_BACKUP_BYTES) { "Backup file is too large" }
        val normalizedPayload = normalizeImportPayload(payload)
        val root = runCatching { org.json.JSONObject(normalizedPayload) }
            .getOrElse { throw IllegalArgumentException("Invalid bookmark backup", it) }
        require(root.optString("format") == BACKUP_FORMAT && root.optInt("version") == BACKUP_VERSION) {
            "Unsupported bookmark backup"
        }
        val folderArray = root.optJSONArray("folders") ?: org.json.JSONArray()
        val bookmarkArray = root.optJSONArray("bookmarks") ?: org.json.JSONArray()
        require(folderArray.length() <= MAX_BACKUP_FOLDERS) { "Too many folders in backup" }
        require(bookmarkArray.length() <= MAX_BACKUP_BOOKMARKS) { "Too many bookmarks in backup" }

        val folders = mutableListOf<BookmarkFolderEntity>()
        val seenFolderIds = mutableSetOf("unfiled")
        val seenFolderNames = mutableSetOf("unfiled")
        for (index in 0 until folderArray.length()) {
            val item = folderArray.optJSONObject(index) ?: continue
            val id = item.optString("id").trim().take(80)
            val name = normalizeDisplayText(item.optString("name"), MAX_FOLDER_NAME_LENGTH)
            val normalizedName = name.lowercase(java.util.Locale.ROOT)
            if (id.isBlank() || id == "unfiled" || name.isBlank() ||
                folders.size >= MAX_BACKUP_FOLDERS - 1 ||
                !seenFolderIds.add(id) || !seenFolderNames.add(normalizedName)
            ) continue
            folders.add(BookmarkFolderEntity(id, name, folders.size + 1, 0L))
        }

        val folderIds = folders.map { it.id }.toSet() + "unfiled"
        val bookmarks = mutableListOf<BookmarkEntity>()
        val seenUrls = mutableSetOf<String>()
        var skipped = 0
        for (index in 0 until bookmarkArray.length()) {
            val item = bookmarkArray.optJSONObject(index)
            if (item == null) {
                skipped++
                continue
            }
            val url = item.optString("url").trim()
            if (!isSafeUrl(url) || !seenUrls.add(url)) {
                skipped++
                continue
            }
            val title = normalizeDisplayText(item.optString("title"), MAX_TITLE_LENGTH)
                .ifBlank { android.net.Uri.parse(url).host.orEmpty().ifBlank { url } }
            val folderId = item.optString("folderId").takeIf(folderIds::contains) ?: "unfiled"
            bookmarks.add(BookmarkEntity(
                id = "import_${java.util.UUID.randomUUID()}",
                url = url,
                title = title,
                folderId = folderId,
                sortOrder = bookmarks.size,
                createdAt = item.optLong("createdAt", 0L).coerceAtLeast(0L)
            ))
        }
        val (importedFolders, importedBookmarks) = if (merge) {
            dao.mergeBookmarkBackup(folders, bookmarks)
        } else {
            dao.replaceBookmarkBackup(folders, bookmarks, WrenStorageDao.MAX_BOOKMARKS)
        }
        return BookmarkBackupImportResult(
            importedFolders = importedFolders,
            importedBookmarks = importedBookmarks,
            skippedBookmarks = skipped + (bookmarks.size - importedBookmarks)
        )
    }

    suspend fun previewImport(payload: String): BookmarkImportPreview {
        require(payload.length <= MAX_BACKUP_BYTES) { "Backup file is too large" }
        val normalizedPayload = normalizeImportPayload(payload)
        val root = runCatching { org.json.JSONObject(normalizedPayload) }
            .getOrElse { throw IllegalArgumentException("Invalid bookmark backup", it) }
        require(root.optString("format") == BACKUP_FORMAT && root.optInt("version") == BACKUP_VERSION) {
            "Unsupported bookmark backup"
        }
        val folders = root.optJSONArray("folders") ?: org.json.JSONArray()
        val incoming = root.optJSONArray("bookmarks") ?: org.json.JSONArray()
        require(folders.length() <= MAX_BACKUP_FOLDERS) { "Too many folders in backup" }
        require(incoming.length() <= MAX_BACKUP_BOOKMARKS) { "Too many bookmarks in backup" }
        val validUrls = (0 until incoming.length()).mapNotNull { index ->
            incoming.optJSONObject(index)?.optString("url")?.trim()?.takeIf(::isSafeUrl)
        }
        val unique = validUrls.distinct()
        val existing = dao.bookmarks(WrenStorageDao.MAX_BOOKMARKS).map { it.url }.toSet()
        return BookmarkImportPreview(
            totalItems = incoming.length(),
            validItems = unique.size,
            skippedItems = incoming.length() - validUrls.size,
            duplicateIncomingItems = validUrls.size - unique.size,
            existingUrlConflicts = unique.count(existing::contains),
            incomingFolders = folders.length()
        )
    }

    suspend fun folders(): List<BookmarkFolderEntity> = dao.folders()

    /** Ensures the reserved destination exists on a brand-new database. Safe to retry. */
    suspend fun ensureDefaultFolder(): Boolean {
        val inserted = dao.insertFolder(BookmarkFolderEntity(
            id = DEFAULT_FOLDER_ID,
            name = "Unfiled",
            sortOrder = 0,
            createdAt = 0L
        ))
        return inserted != -1L || dao.folder(DEFAULT_FOLDER_ID) != null
    }

    suspend fun createFolder(name: String, createdAt: Long = System.currentTimeMillis()): String? {
        val normalized = normalizeFolderName(name) ?: return null
        if (createdAt < 0L) return null
        val id = "folder_${UUID.randomUUID()}"
        val created = dao.createFolderIfNameAvailable(
            BookmarkFolderEntity(id = id, name = normalized, createdAt = createdAt)
        )
        return id.takeIf { created }
    }

    suspend fun renameFolder(folderId: String, name: String): Boolean {
        if (folderId == DEFAULT_FOLDER_ID) return false
        val normalized = normalizeFolderName(name) ?: return false
        return dao.renameFolderIfNameAvailable(folderId, normalized)
    }

    suspend fun deleteFolder(folderId: String): Boolean = dao.removeFolder(folderId)

    suspend fun reorderFolders(folderIds: List<String>): Boolean {
        if (folderIds.distinct().size != folderIds.size) return false
        return dao.reorderBookmarkFolders(folderIds)
    }

    suspend fun find(url: String): BookmarkEntity? {
        if (!isSafeUrl(url)) return null
        return dao.bookmark(url)
    }

    suspend fun add(
        url: String,
        title: String,
        folderId: String = DEFAULT_FOLDER_ID,
        createdAt: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isSafeUrl(url) || createdAt < 0L) return false
        if (dao.folder(folderId) == null) return false
        val host = android.net.Uri.parse(url).host.orEmpty()
        val normalizedTitle = normalizeDisplayText(title, MAX_TITLE_LENGTH).ifBlank { host.ifBlank { url } }
        val entity = BookmarkEntity(
            id = "bookmark_${UUID.randomUUID()}",
            url = url,
            title = normalizedTitle,
            folderId = folderId,
            createdAt = createdAt
        )
        // IGNORE plus the unique URL index makes duplicate prevention atomic.
        return dao.addBookmarkIfCapacityAvailable(entity)
    }

    suspend fun remove(url: String): Boolean =
        url.takeIf(::isSafeUrl)?.let { dao.deleteBookmark(it) > 0 } ?: false

    suspend fun rename(url: String, title: String): Boolean {
        if (!isSafeUrl(url)) return false
        val normalized = normalizeDisplayText(title, MAX_TITLE_LENGTH)
        if (normalized.isBlank()) return false
        return dao.updateBookmarkTitle(url, normalized) > 0
    }

    suspend fun move(url: String, folderId: String): Boolean {
        if (!isSafeUrl(url) || dao.folder(folderId) == null) return false
        return dao.moveBookmark(url, folderId) > 0
    }

    /** Moves one bookmark within its current folder; direction is -1 (up) or +1 (down). */
    suspend fun moveWithinFolder(url: String, folderId: String, direction: Int): Boolean {
        if (!isSafeUrl(url) || folderId.isBlank() || direction !in -1..1 || direction == 0) return false
        return dao.moveBookmarkWithinFolder(url, folderId, direction)
    }

    private fun normalizeImportPayload(payload: String): String {
        require(payload.length <= MAX_BACKUP_BYTES) { "Backup file is too large" }
        return if (payload.trimStart().startsWith("<")) BookmarkHtmlCodec.toBackupJson(payload) else payload
    }

    private fun isSafeUrl(url: String): Boolean =
        url.length <= MAX_URL_LENGTH && NavigationInput.isValidWebUrl(url)

    private fun normalizeFolderName(name: String): String? =
        normalizeDisplayText(name, MAX_FOLDER_NAME_LENGTH).takeIf { it.isNotBlank() }

    /** Removes control and bidi-override characters from untrusted display text. */
    private fun normalizeDisplayText(value: String, maxLength: Int): String =
        value.asSequence()
            .filterNot { char ->
                char.isISOControl() || char in '\u202A'..'\u202E' || char in '\u2066'..'\u2069'
            }
            .joinToString("")
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(maxLength)

    private companion object {
        const val DEFAULT_FOLDER_ID = "unfiled"
        const val MAX_URL_LENGTH = 4096
        const val MAX_TITLE_LENGTH = 160
        const val MAX_FOLDER_NAME_LENGTH = 40
        const val MAX_BACKUP_BYTES = 1_000_000
        const val MAX_BACKUP_FOLDERS = 50
        const val MAX_BACKUP_BOOKMARKS = 1000
        const val BACKUP_FORMAT = "wren-bookmarks"
        const val BACKUP_VERSION = 1
    }
}

/** Counts rows actually imported; conflicts with existing URLs are reported as skipped. */
internal data class BookmarkBackupImportResult(
    val importedFolders: Int,
    val importedBookmarks: Int,
    val skippedBookmarks: Int
)
