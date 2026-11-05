package com.browsex.app.storage

import com.browsex.app.NavigationInput
import java.util.UUID

/**
 * Room-backed bookmark operations. This is the first repository slice; production callers
 * remain on the legacy store until the complete read/write cutover is explicitly wired.
 */
internal class RoomBookmarkRepository(
    private val dao: BrowseXStorageDao
) {
    suspend fun all(): List<BookmarkEntity> = dao.bookmarks()

    suspend fun folders(): List<BookmarkFolderEntity> = dao.folders()

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
        val normalizedTitle = title.trim().ifBlank { host.ifBlank { url } }.take(MAX_TITLE_LENGTH)
        val entity = BookmarkEntity(
            id = "bookmark_${UUID.randomUUID()}",
            url = url,
            title = normalizedTitle,
            folderId = folderId,
            createdAt = createdAt
        )
        // IGNORE plus the unique URL index makes duplicate prevention atomic.
        return dao.insertBookmark(entity) != -1L
    }

    suspend fun remove(url: String): Boolean =
        url.takeIf(::isSafeUrl)?.let { dao.deleteBookmark(it) > 0 } ?: false

    suspend fun move(url: String, folderId: String): Boolean {
        if (!isSafeUrl(url) || dao.folder(folderId) == null) return false
        return dao.moveBookmark(url, folderId) > 0
    }

    private fun isSafeUrl(url: String): Boolean =
        url.length <= MAX_URL_LENGTH && NavigationInput.isValidWebUrl(url)

    private fun normalizeFolderName(name: String): String? =
        name.trim().take(MAX_FOLDER_NAME_LENGTH).takeIf { it.isNotBlank() }

    private companion object {
        const val DEFAULT_FOLDER_ID = "unfiled"
        const val MAX_URL_LENGTH = 4096
        const val MAX_TITLE_LENGTH = 160
        const val MAX_FOLDER_NAME_LENGTH = 40
    }
}
