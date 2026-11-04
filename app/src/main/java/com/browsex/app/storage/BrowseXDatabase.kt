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

@Dao
interface BrowseXStorageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFolders(items: List<BookmarkFolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBookmarks(items: List<BookmarkEntity>)

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

    @Query("SELECT * FROM bookmarks ORDER BY sort_order, created_at DESC")
    suspend fun bookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM history_entries ORDER BY visited_at DESC LIMIT :limit")
    suspend fun history(limit: Int): List<HistoryEntryEntity>

    @Query("UPDATE bookmarks SET folder_id = 'unfiled' WHERE folder_id = :folderId")
    suspend fun moveFolderItemsToUnfiled(folderId: String)

    @Query("DELETE FROM bookmark_folders WHERE id = :folderId AND id != 'unfiled'")
    suspend fun deleteFolder(folderId: String)

    @Transaction
    suspend fun removeFolder(folderId: String) {
        moveFolderItemsToUnfiled(folderId)
        deleteFolder(folderId)
    }
}

/**
 * Version one is a staging schema only. SharedPreferences remains authoritative
 * until the idempotent importer and rollback tests are shipped.
 */
@Database(
    entities = [
        BookmarkFolderEntity::class,
        BookmarkEntity::class,
        HistoryEntryEntity::class,
        SavedSessionEntity::class,
        SavedSessionTabEntity::class,
        RecentlyClosedTabEntity::class,
        SitePermissionDecisionEntity::class,
        DownloadRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BrowseXDatabase : RoomDatabase() {
    abstract fun storageDao(): BrowseXStorageDao
}
