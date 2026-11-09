package com.browsex.app.storage

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

/** Room schema for Wren's clean browser storage graph. */
@Entity(tableName = "bookmark_folders")
data class BookmarkFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L
)

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true), Index(value = ["folder_id"])],
    foreignKeys = [ForeignKey(
        entity = BookmarkFolderEntity::class,
        parentColumns = ["id"],
        childColumns = ["folder_id"],
        onDelete = ForeignKey.SET_DEFAULT
    )]
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    @ColumnInfo(name = "folder_id", defaultValue = "'unfiled'") val folderId: String = "unfiled",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L
)

@Entity(tableName = "history_entries", indices = [Index("visited_at"), Index("url")])
data class HistoryEntryEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    @ColumnInfo(name = "visited_at") val visitedAt: Long,
    @ColumnInfo(name = "visit_count") val visitCount: Int = 1
)

@Entity(tableName = "saved_sessions")
data class SavedSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "active_index") val activeIndex: Int,
    @ColumnInfo(name = "saved_at") val savedAt: Long
)

@Entity(
    tableName = "saved_session_tabs",
    primaryKeys = ["session_id", "position"],
    foreignKeys = [ForeignKey(
        entity = SavedSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("session_id")]
)
data class SavedSessionTabEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    val position: Int,
    val url: String
)

@Entity(tableName = "recently_closed_tabs", indices = [Index("closed_at")])
data class RecentlyClosedTabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    @ColumnInfo(name = "closed_at") val closedAt: Long
)

@Entity(
    tableName = "site_permission_decisions",
    primaryKeys = ["origin", "feature"]
)
data class SitePermissionDecisionEntity(
    val origin: String,
    val feature: String,
    val decision: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "download_records", indices = [Index("created_at"), Index("status")])
data class DownloadRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    val uri: String,
    @ColumnInfo(name = "bytes_written") val bytesWritten: Long,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long,
    val status: String,
    val error: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "browser_state")
data class BrowserStateEntity(
    @PrimaryKey val id: String = "open_tabs",
    @ColumnInfo(name = "active_index") val activeIndex: Int,
    @ColumnInfo(name = "saved_at") val savedAt: Long
)

@Entity(tableName = "open_tabs", indices = [Index("position")])
data class OpenTabEntity(
    @PrimaryKey val position: Int,
    val url: String
)

@Entity(tableName = "download_name_reservations", indices = [Index("position")])
data class DownloadNameReservationEntity(
    @PrimaryKey @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val position: Int,
    @ColumnInfo(name = "reserved_at") val reservedAt: Long
)

@Entity(tableName = "platform_download_ids", indices = [Index("position")])
data class PlatformDownloadIdEntity(
    @PrimaryKey @ColumnInfo(name = "download_id") val downloadId: Long,
    val position: Int,
    @ColumnInfo(name = "tracked_at") val trackedAt: Long
)


@Entity(tableName = "storage_migration_state")
data class StorageMigrationStateEntity(
    @PrimaryKey val id: String = "legacy_import",
    val version: Int,
    val digest: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long
)

@Dao
interface BrowseXStorageDao {
    companion object {
        const val MAX_BOOKMARK_FOLDERS = 50
        const val MAX_BOOKMARKS = 200
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFolders(items: List<BookmarkFolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBookmarks(items: List<BookmarkEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmark(item: BookmarkEntity): Long

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM bookmarks WHERE folder_id = :folderId")
    suspend fun nextBookmarkSortOrder(folderId: String): Int

    @Transaction
    suspend fun addBookmarkIfCapacityAvailable(item: BookmarkEntity): Boolean {
        if (bookmarkCount() >= MAX_BOOKMARKS) return false
        val ordered = item.copy(sortOrder = nextBookmarkSortOrder(item.folderId))
        return insertBookmark(ordered) != -1L
    }

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun bookmark(url: String): BookmarkEntity?

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmark(url: String): Int

    @Query("UPDATE bookmarks SET title = :title WHERE url = :url")
    suspend fun updateBookmarkTitle(url: String, title: String): Int

    @Query("SELECT * FROM bookmark_folders WHERE id = :folderId LIMIT 1")
    suspend fun folder(folderId: String): BookmarkFolderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: BookmarkFolderEntity): Long

    @Query("SELECT COUNT(*) FROM bookmark_folders WHERE lower(name) = lower(:name)")
    suspend fun folderNameCount(name: String): Int

    @Query("SELECT COUNT(*) FROM bookmark_folders WHERE lower(name) = lower(:name) AND id != :folderId")
    suspend fun otherFolderNameCount(name: String, folderId: String): Int

    @Query("UPDATE bookmark_folders SET name = :name WHERE id = :folderId")
    suspend fun renameFolder(folderId: String, name: String): Int

    @Query("UPDATE bookmark_folders SET sort_order = :sortOrder WHERE id = :folderId")
    suspend fun updateFolderSortOrder(folderId: String, sortOrder: Int): Int

    @Transaction
    suspend fun createFolderIfNameAvailable(folder: BookmarkFolderEntity): Boolean {
        // Legacy BrowserLibraryStore allows 50 folders total, including "unfiled".
        if (folder.name.isBlank() || folderCount() >= MAX_BOOKMARK_FOLDERS) return false
        if (folderNameCount(folder.name) > 0) return false
        return insertFolder(folder) != -1L
    }

    @Transaction
    suspend fun renameFolderIfNameAvailable(folderId: String, name: String): Boolean {
        if (folder(folderId) == null || name.isBlank()) return false
        if (otherFolderNameCount(name, folderId) > 0) return false
        return renameFolder(folderId, name) > 0
    }

    @Transaction
    suspend fun reorderBookmarkFolders(folderIds: List<String>): Boolean {
        val existingIds = folders().map { it.id }.toSet()
        if (folderIds.size != existingIds.size || folderIds.toSet() != existingIds) return false
        folderIds.forEachIndexed { index, id -> updateFolderSortOrder(id, index) }
        return true
    }

    @Query("SELECT * FROM bookmarks WHERE folder_id = :folderId ORDER BY sort_order, created_at DESC")
    suspend fun bookmarksInFolder(folderId: String): List<BookmarkEntity>

    @Query("UPDATE bookmarks SET folder_id = :folderId, sort_order = :sortOrder WHERE url = :url")
    suspend fun updateBookmarkFolderAndOrder(url: String, folderId: String, sortOrder: Int): Int

    @Query("UPDATE bookmarks SET sort_order = :sortOrder WHERE url = :url AND folder_id = :folderId")
    suspend fun updateBookmarkSortOrder(url: String, folderId: String, sortOrder: Int): Int

    @Transaction
    suspend fun moveBookmark(url: String, folderId: String): Int {
        if (folder(folderId) == null) return 0
        val item = bookmark(url) ?: return 0
        if (item.folderId == folderId) return 1
        return updateBookmarkFolderAndOrder(url, folderId, nextBookmarkSortOrder(folderId))
    }

    @Transaction
    suspend fun moveBookmarkWithinFolder(url: String, folderId: String, direction: Int): Boolean {
        if (direction !in -1..1 || direction == 0) return false
        val ordered = bookmarksInFolder(folderId).toMutableList()
        val currentIndex = ordered.indexOfFirst { it.url == url }
        val targetIndex = currentIndex + direction
        if (currentIndex < 0 || targetIndex !in ordered.indices) return false
        val current = ordered[currentIndex]
        ordered[currentIndex] = ordered[targetIndex]
        ordered[targetIndex] = current
        ordered.forEachIndexed { index, bookmark ->
            updateBookmarkSortOrder(bookmark.url, folderId, index)
        }
        return true
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putHistory(items: List<HistoryEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSessions(items: List<SavedSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSessionTabs(items: List<SavedSessionTabEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRecentlyClosed(items: List<RecentlyClosedTabEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPermissionDecisions(items: List<SitePermissionDecisionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDownloads(items: List<DownloadRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBrowserState(items: List<BrowserStateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOpenTabs(items: List<OpenTabEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDownloadNameReservations(items: List<DownloadNameReservationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPlatformDownloadIds(items: List<PlatformDownloadIdEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMigrationState(state: StorageMigrationStateEntity)

    @Query("SELECT * FROM storage_migration_state WHERE id = 'legacy_import' LIMIT 1")
    suspend fun migrationState(): StorageMigrationStateEntity?

    @Query("SELECT * FROM bookmarks ORDER BY sort_order, created_at DESC")
    suspend fun bookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM history_entries WHERE url = :url LIMIT 1")
    suspend fun historyEntryByUrl(url: String): HistoryEntryEntity?

    @Query("DELETE FROM history_entries WHERE url = :url")
    suspend fun deleteHistoryByUrl(url: String): Int

    @Query("DELETE FROM history_entries WHERE id IN (SELECT id FROM history_entries ORDER BY visited_at DESC LIMIT -1 OFFSET :keep)")
    suspend fun pruneHistory(keep: Int)

    @Transaction
    suspend fun recordHistoryEntry(entry: HistoryEntryEntity, keep: Int = 300) {
        val previous = historyEntryByUrl(entry.url)
        val updated = if (previous == null) entry else previous.copy(
            title = entry.title,
            visitedAt = maxOf(previous.visitedAt, entry.visitedAt),
            visitCount = (previous.visitCount + 1).coerceAtMost(Int.MAX_VALUE)
        )
        putHistory(listOf(updated))
        pruneHistory(keep.coerceAtLeast(0))
    }

    @Query("SELECT * FROM history_entries ORDER BY visited_at DESC LIMIT :limit")
    suspend fun history(limit: Int): List<HistoryEntryEntity>

    @Query("SELECT * FROM bookmark_folders ORDER BY sort_order, name")
    suspend fun folders(): List<BookmarkFolderEntity>

    @Query("DELETE FROM saved_session_tabs WHERE session_id = :sessionId")
    suspend fun clearTabsForSession(sessionId: String)

    @Query("DELETE FROM saved_sessions WHERE id = :sessionId")
    suspend fun deleteSavedSession(sessionId: String): Int

    @Transaction
    suspend fun removeSavedSession(sessionId: String): Boolean {
        clearTabsForSession(sessionId)
        return deleteSavedSession(sessionId) > 0
    }

    @Query("DELETE FROM recently_closed_tabs WHERE url = :url")
    suspend fun deleteRecentlyClosedByUrl(url: String)

    @Query("DELETE FROM recently_closed_tabs WHERE closed_at < :cutoff")
    suspend fun deleteRecentlyClosedBefore(cutoff: Long): Int

    @Query("DELETE FROM recently_closed_tabs WHERE id = :id")
    suspend fun deleteRecentlyClosedById(id: String): Int

    @Query("SELECT * FROM recently_closed_tabs WHERE id = :id LIMIT 1")
    suspend fun recentlyClosedTab(id: String): RecentlyClosedTabEntity?

    @Transaction
    suspend fun consumeRecentlyClosedTab(id: String): RecentlyClosedTabEntity? {
        if (id.isBlank()) return null
        val item = recentlyClosedTab(id) ?: return null
        return item.takeIf { deleteRecentlyClosedById(id) > 0 }
    }

    @Transaction
    suspend fun consumeRecentlyClosedTabs(ids: List<String>): List<RecentlyClosedTabEntity> {
        if (ids.isEmpty()) return emptyList()
        val requested = ids.filter { it.isNotBlank() }.distinct()
        if (requested.isEmpty()) return emptyList()
        val available = requested.mapNotNull { recentlyClosedTab(it) }
        return available.filter { deleteRecentlyClosedById(it.id) > 0 }
    }

    @Transaction
    suspend fun saveNamedSession(session: SavedSessionEntity, tabs: List<SavedSessionTabEntity>): Boolean {
        val existing = savedSessions().firstOrNull { it.name.equals(session.name, ignoreCase = true) }
        if (existing == null && savedSessionCount() >= 10) return false
        val stableId = existing?.id ?: session.id
        clearTabsForSession(stableId)
        putSessions(listOf(session.copy(id = stableId)))
        putSessionTabs(tabs.map { it.copy(sessionId = stableId) })
        return true
    }

    @Transaction
    suspend fun recordRecentlyClosed(tab: RecentlyClosedTabEntity) {
        deleteRecentlyClosedByUrl(tab.url)
        putRecentlyClosed(listOf(tab))
        val overflow = recentlyClosedTabs().drop(10)
        overflow.forEach { deleteRecentlyClosedById(it.id) }
    }

    @Query("SELECT * FROM saved_sessions ORDER BY saved_at DESC")
    suspend fun savedSessions(): List<SavedSessionEntity>

    @Query("SELECT * FROM saved_session_tabs ORDER BY session_id, position")
    suspend fun savedSessionTabs(): List<SavedSessionTabEntity>

    @Query("SELECT * FROM recently_closed_tabs ORDER BY closed_at DESC")
    suspend fun recentlyClosedTabs(): List<RecentlyClosedTabEntity>

    @Query("SELECT * FROM site_permission_decisions WHERE origin = :origin AND feature = :feature LIMIT 1")
    suspend fun permissionDecision(origin: String, feature: String): SitePermissionDecisionEntity?

    @Query("DELETE FROM site_permission_decisions WHERE origin = :origin AND feature = :feature")
    suspend fun deletePermissionDecision(origin: String, feature: String): Int

    @Query("DELETE FROM site_permission_decisions WHERE origin = :origin")
    suspend fun deletePermissionDecisionsForOrigin(origin: String): Int

    @Query("DELETE FROM site_permission_decisions")
    suspend fun clearAllPermissionDecisions()

    @Transaction
    suspend fun setPermissionDecision(item: SitePermissionDecisionEntity, keep: Int = 100) {
        putPermissionDecisions(listOf(item))
        val overflow = permissionDecisions()
            .sortedByDescending { it.updatedAt }
            .drop(keep.coerceAtLeast(0))
        overflow.forEach { deletePermissionDecision(it.origin, it.feature) }
    }

    @Query("SELECT * FROM site_permission_decisions ORDER BY origin, feature")
    suspend fun permissionDecisions(): List<SitePermissionDecisionEntity>

    @Query("SELECT * FROM download_records WHERE id = :id LIMIT 1")
    suspend fun downloadRecord(id: String): DownloadRecordEntity?

    @Query("DELETE FROM download_records WHERE id = :id")
    suspend fun deleteDownloadRecord(id: String): Int

    @Query("SELECT COUNT(*) FROM download_records WHERE status IN ('queued', 'running')")
    suspend fun activeDownloadCount(): Int

    @Query("DELETE FROM download_records WHERE id IN (SELECT id FROM download_records WHERE status NOT IN ('queued', 'running') ORDER BY created_at DESC LIMIT -1 OFFSET :keep)")
    suspend fun pruneDownloadRecords(keep: Int)

    @Transaction
    suspend fun createDownloadRecord(record: DownloadRecordEntity, keep: Int = 300): Boolean {
        if (keep <= 0) return false
        val active = activeDownloadCount()
        // Never evict an active transfer to make room for a new history row.
        if (active >= keep) return false
        pruneDownloadRecords((keep - active - 1).coerceAtLeast(0))
        putDownloads(listOf(record))
        return true
    }

    @Query("SELECT * FROM download_name_reservations ORDER BY position")
    suspend fun downloadNameReservations(): List<DownloadNameReservationEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM download_name_reservations")
    suspend fun nextDownloadNameReservationPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDownloadNameReservation(item: DownloadNameReservationEntity): Long

    @Query("DELETE FROM download_name_reservations WHERE normalized_name IN (SELECT normalized_name FROM download_name_reservations ORDER BY position ASC LIMIT -1 OFFSET :keep)")
    suspend fun pruneDownloadNameReservations(keep: Int)

    @Transaction
    suspend fun reserveDownloadName(candidate: String, now: Long): String {
        val dot = candidate.lastIndexOf('.').takeIf { it > 0 } ?: candidate.length
        val stem = candidate.substring(0, dot)
        val extension = candidate.substring(dot)
        var suffix = 0
        while (suffix < 10_000) {
            val display = if (suffix == 0) candidate else "$stem ($suffix)$extension"
            val normalized = display.lowercase(java.util.Locale.ROOT)
            val inserted = insertDownloadNameReservation(DownloadNameReservationEntity(
                normalizedName = normalized,
                displayName = display,
                position = nextDownloadNameReservationPosition(),
                reservedAt = now
            ))
            if (inserted != -1L) {
                pruneDownloadNameReservations(300)
                return display
            }
            suffix++
        }
        throw IllegalStateException("Unable to reserve a unique download filename")
    }

    @Query("DELETE FROM platform_download_ids WHERE download_id = :downloadId")
    suspend fun removePlatformDownloadId(downloadId: Long): Int

    @Query("SELECT * FROM platform_download_ids ORDER BY position")
    suspend fun platformDownloadIds(): List<PlatformDownloadIdEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM platform_download_ids")
    suspend fun nextPlatformDownloadIdPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlatformDownloadId(item: PlatformDownloadIdEntity): Long

    @Query("DELETE FROM platform_download_ids WHERE download_id IN (SELECT download_id FROM platform_download_ids ORDER BY position ASC LIMIT -1 OFFSET :keep)")
    suspend fun prunePlatformDownloadIds(keep: Int)

    @Transaction
    suspend fun trackPlatformDownloadId(item: PlatformDownloadIdEntity): Boolean {
        if (item.downloadId <= 0L) return false
        if (platformDownloadIds().any { it.downloadId == item.downloadId }) return true
        val inserted = insertPlatformDownloadId(item.copy(position = nextPlatformDownloadIdPosition()))
        if (inserted == -1L) return false
        prunePlatformDownloadIds(300)
        return true
    }

    @Query("UPDATE download_records SET status = 'running' WHERE id = :id AND status = 'queued'")
    suspend fun markDownloadRunning(id: String): Int

    @Query("UPDATE download_records SET bytes_written = :bytesWritten WHERE id = :id AND status = 'running' AND bytes_written < :bytesWritten")
    suspend fun updateRunningDownloadProgress(id: String, bytesWritten: Long): Int

    @Query("UPDATE download_records SET uri = :uri, bytes_written = CASE WHEN bytes_written < :bytesWritten THEN :bytesWritten ELSE bytes_written END, status = 'completed', error = '' WHERE id = :id AND status IN ('queued', 'running')")
    suspend fun completeActiveDownload(id: String, uri: String, bytesWritten: Long): Int

    @Query("UPDATE download_records SET uri = '', bytes_written = CASE WHEN :bytesWritten < 0 THEN bytes_written WHEN bytes_written < :bytesWritten THEN :bytesWritten ELSE bytes_written END, status = :status, error = :error WHERE id = :id AND status IN ('queued', 'running')")
    suspend fun finishActiveDownload(id: String, status: String, error: String, bytesWritten: Long): Int

    @Query("UPDATE download_records SET uri = '', status = 'failed', error = 'Download interrupted before BrowseX could finish it' WHERE status IN ('queued', 'running')")
    suspend fun failInterruptedDownloads(): Int

    @Query("SELECT * FROM download_records ORDER BY created_at DESC")
    suspend fun downloads(): List<DownloadRecordEntity>

    @Query("SELECT * FROM browser_state")
    suspend fun browserState(): List<BrowserStateEntity>

    @Transaction
    suspend fun saveOpenTabSnapshot(state: BrowserStateEntity, tabs: List<OpenTabEntity>) {
        clearOpenTabs()
        putOpenTabs(tabs)
        putBrowserState(listOf(state))
    }

    @Query("SELECT * FROM open_tabs ORDER BY position")
    suspend fun openTabs(): List<OpenTabEntity>

    @Query("UPDATE bookmarks SET folder_id = 'unfiled' WHERE folder_id = :folderId")
    suspend fun moveFolderItemsToUnfiled(folderId: String)

    @Query("DELETE FROM bookmark_folders WHERE id = :folderId AND id != 'unfiled'")
    suspend fun deleteFolder(folderId: String)

    @Query("DELETE FROM bookmarks")
    suspend fun clearBookmarks()

    @Query("DELETE FROM bookmark_folders")
    suspend fun clearFolders()

    @Query("DELETE FROM history_entries")
    suspend fun clearHistory()

    @Query("DELETE FROM saved_session_tabs")
    suspend fun clearSessionTabs()

    @Query("DELETE FROM saved_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM recently_closed_tabs")
    suspend fun clearRecentlyClosed()

    @Query("DELETE FROM site_permission_decisions")
    suspend fun clearPermissionDecisions()

    @Query("DELETE FROM download_records")
    suspend fun clearDownloads()

    @Query("DELETE FROM browser_state")
    suspend fun clearBrowserState()

    @Query("DELETE FROM open_tabs")
    suspend fun clearOpenTabs()

    @Query("DELETE FROM download_name_reservations")
    suspend fun clearDownloadNameReservations()

    @Query("DELETE FROM platform_download_ids")
    suspend fun clearPlatformDownloadIds()

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun bookmarkCount(): Int

    @Query("SELECT COUNT(*) FROM history_entries")
    suspend fun historyCount(): Int

    @Query("SELECT COUNT(*) FROM saved_sessions")
    suspend fun savedSessionCount(): Int

    @Query("SELECT COUNT(*) FROM bookmark_folders")
    suspend fun folderCount(): Int

    @Query("SELECT COUNT(*) FROM saved_session_tabs")
    suspend fun sessionTabCount(): Int

    @Query("SELECT COUNT(*) FROM recently_closed_tabs")
    suspend fun recentlyClosedCount(): Int

    @Query("SELECT COUNT(*) FROM site_permission_decisions")
    suspend fun permissionDecisionCount(): Int

    @Query("SELECT COUNT(*) FROM open_tabs")
    suspend fun openTabCount(): Int

    @Query("SELECT COUNT(*) FROM download_name_reservations")
    suspend fun downloadNameReservationCount(): Int

    @Query("SELECT COUNT(*) FROM platform_download_ids")
    suspend fun platformDownloadIdCount(): Int

    @Query("SELECT COUNT(*) FROM download_records")
    suspend fun downloadRecordCount(): Int

    @Transaction
    suspend fun replaceBookmarkBackup(
        incomingFolders: List<BookmarkFolderEntity>,
        incomingBookmarks: List<BookmarkEntity>,
        maxBookmarks: Int = MAX_BOOKMARKS
    ): Pair<Int, Int> {
        clearBookmarks()
        clearFolders()
        putFolders(listOf(BookmarkFolderEntity("unfiled", "Unfiled", 0, 0L)) +
            incomingFolders.take((MAX_BOOKMARK_FOLDERS - 1).coerceAtLeast(0)))
        val availableFolders = folders().map { it.id }.toSet()
        val accepted = incomingBookmarks.take(maxBookmarks.coerceAtLeast(0)).mapIndexed { index, item ->
            item.copy(folderId = item.folderId.takeIf(availableFolders::contains) ?: "unfiled", sortOrder = index)
        }
        putBookmarks(accepted)
        return (folderCount() - 1).coerceAtLeast(0) to accepted.size
    }

    /**
     * Merge a validated bookmark backup without overwriting existing URLs.
     * Folder matching is case-insensitive and the operation is one transaction.
     */
    @Transaction
    suspend fun mergeBookmarkBackup(
        incomingFolders: List<BookmarkFolderEntity>,
        incomingBookmarks: List<BookmarkEntity>
    ): Pair<Int, Int> {
        val folderMap = mutableMapOf("unfiled" to "unfiled")
        var importedFolders = 0
        incomingFolders.forEach { incoming ->
            if (incoming.id == "unfiled" || incoming.name.isBlank()) return@forEach
            val existingByName = folders().firstOrNull { it.name.equals(incoming.name, ignoreCase = true) }
            if (existingByName != null) {
                folderMap[incoming.id] = existingByName.id
            } else if (folderCount() < MAX_BOOKMARK_FOLDERS) {
                val id = if (folder(incoming.id) == null) incoming.id
                    else "imported_${incoming.id.hashCode().toUInt().toString(16)}"
                val safe = incoming.copy(id = id, sortOrder = folderCount())
                if (insertFolder(safe) != -1L) {
                    folderMap[incoming.id] = id
                    importedFolders++
                } else {
                    folderMap[incoming.id] = "unfiled"
                }
            } else {
                folderMap[incoming.id] = "unfiled"
            }
        }

        var importedBookmarks = 0
        incomingBookmarks.forEach { incoming ->
            if (bookmarkCount() >= MAX_BOOKMARKS) return@forEach
            if (bookmark(incoming.url) != null) return@forEach
            val folderId = folderMap[incoming.folderId] ?: "unfiled"
            if (insertBookmark(incoming.copy(folderId = folderId)) != -1L) {
                importedBookmarks++
            }
        }
        return importedFolders to importedBookmarks
    }

    @Transaction
    suspend fun removeFolder(folderId: String): Boolean {
        if (folderId == "unfiled" || folder(folderId) == null) return false
        moveFolderItemsToUnfiled(folderId)
        deleteFolder(folderId)
        return folder(folderId) == null
    }
}

/** Room storage schema for Wren browser data. */
@Database(
    entities = [
        BookmarkFolderEntity::class,
        BookmarkEntity::class,
        HistoryEntryEntity::class,
        SavedSessionEntity::class,
        SavedSessionTabEntity::class,
        RecentlyClosedTabEntity::class,
        SitePermissionDecisionEntity::class,
        DownloadRecordEntity::class,
        BrowserStateEntity::class,
        OpenTabEntity::class,
        DownloadNameReservationEntity::class,
        PlatformDownloadIdEntity::class,
        StorageMigrationStateEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class BrowseXDatabase : RoomDatabase() {
    abstract fun storageDao(): BrowseXStorageDao

    companion object {
        @Volatile private var instance: BrowseXDatabase? = null

        fun getInstance(context: android.content.Context): BrowseXDatabase =
            instance ?: synchronized(this) {
                instance ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    BrowseXDatabase::class.java,
                    "wren-browser.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
