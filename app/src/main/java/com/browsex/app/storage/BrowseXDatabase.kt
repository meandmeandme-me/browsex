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

/** Additive local schema. It is deliberately not wired into production yet. */
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
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFolders(items: List<BookmarkFolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBookmarks(items: List<BookmarkEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmark(item: BookmarkEntity): Long

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun bookmark(url: String): BookmarkEntity?

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmark(url: String): Int

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

    @Query("UPDATE bookmarks SET folder_id = :folderId WHERE url = :url")
    suspend fun moveBookmark(url: String, folderId: String): Int

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

    @Query("SELECT * FROM history_entries ORDER BY visited_at DESC LIMIT :limit")
    suspend fun history(limit: Int): List<HistoryEntryEntity>

    @Query("SELECT * FROM bookmark_folders ORDER BY sort_order, name")
    suspend fun folders(): List<BookmarkFolderEntity>

    @Query("SELECT * FROM saved_sessions ORDER BY saved_at DESC")
    suspend fun savedSessions(): List<SavedSessionEntity>

    @Query("SELECT * FROM saved_session_tabs ORDER BY session_id, position")
    suspend fun savedSessionTabs(): List<SavedSessionTabEntity>

    @Query("SELECT * FROM recently_closed_tabs ORDER BY closed_at DESC")
    suspend fun recentlyClosedTabs(): List<RecentlyClosedTabEntity>

    @Query("SELECT * FROM site_permission_decisions ORDER BY origin, feature")
    suspend fun permissionDecisions(): List<SitePermissionDecisionEntity>

    @Query("SELECT * FROM download_records ORDER BY created_at DESC")
    suspend fun downloads(): List<DownloadRecordEntity>

    @Query("SELECT * FROM browser_state")
    suspend fun browserState(): List<BrowserStateEntity>

    @Query("SELECT * FROM open_tabs ORDER BY position")
    suspend fun openTabs(): List<OpenTabEntity>

    @Query("SELECT * FROM download_name_reservations ORDER BY position")
    suspend fun downloadNameReservations(): List<DownloadNameReservationEntity>

    @Query("SELECT * FROM platform_download_ids ORDER BY position")
    suspend fun platformDownloadIds(): List<PlatformDownloadIdEntity>

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
    suspend fun removeFolder(folderId: String): Boolean {
        if (folderId == "unfiled" || folder(folderId) == null) return false
        moveFolderItemsToUnfiled(folderId)
        deleteFolder(folderId)
        return folder(folderId) == null
    }
}

/** Room storage schema. Legacy import state is committed atomically with imported rows. */
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
                    "browsex-storage-v1.db"
                ).build().also { instance = it }
            }
    }
}
