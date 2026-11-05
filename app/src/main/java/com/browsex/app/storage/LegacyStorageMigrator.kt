package com.browsex.app.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class LegacyStorageMigrationReport(
    val bookmarkCount: Int,
    val folderCount: Int,
    val historyCount: Int,
    val sessionCount: Int,
    val recentlyClosedCount: Int,
    val permissionCount: Int,
    val downloadCount: Int,
    val openTabCount: Int,
    val platformDownloadIdCount: Int,
    val reservationCount: Int,
    val snapshotDigest: String
)

/**
 * Copies a single immutable legacy snapshot. It never clears legacy keys or
 * changes production readers; the migration marker is saved only after Room commits.
 */
internal class LegacyStorageMigrator(
    context: Context,
    sourcePreferences: SharedPreferences? = null,
    markerPreferences: SharedPreferences? = null,
    databaseOverride: BrowseXDatabase? = null
) {
    private val app = context.applicationContext
    private val source = sourcePreferences
        ?: app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    private val marker = markerPreferences
        ?: app.getSharedPreferences(MARKER_PREFS, Context.MODE_PRIVATE)
    private val database by lazy { databaseOverride ?: BrowseXDatabase.getInstance(app) }

    suspend fun migrateIfNeeded(): LegacyStorageMigrationReport? = migrationMutex.withLock {
        withContext(Dispatchers.IO) {
            if (marker.getInt(KEY_VERSION, 0) >= VERSION) return@withContext null
            val immutableSnapshot = source.all.mapValues { (_, value) ->
                if (value is Set<*>) value.filterIsInstance<String>().toSet() else value
            }
            val parsed = LegacyStorageSnapshotParser.parse(immutableSnapshot)
            val db = database
            db.withTransaction {
                val dao = db.storageDao()
                dao.clearBookmarks()
                dao.clearFolders()
                dao.clearHistory()
                dao.clearSessionTabs()
                dao.clearSessions()
                dao.clearRecentlyClosed()
                dao.clearPermissionDecisions()
                dao.clearDownloads()
                dao.clearBrowserState()
                dao.clearOpenTabs()
                dao.clearDownloadNameReservations()
                dao.clearPlatformDownloadIds()

                // The Unfiled parent row must exist before bookmark foreign keys are inserted.
                dao.putFolders(parsed.folders)
                dao.putBookmarks(parsed.bookmarks)
                dao.putHistory(parsed.history)
                dao.putSessions(parsed.sessions)
                dao.putSessionTabs(parsed.sessionTabs)
                dao.putRecentlyClosed(parsed.recentlyClosed)
                dao.putPermissionDecisions(parsed.permissions)
                dao.putDownloads(parsed.downloads)
                dao.putBrowserState(parsed.browserState)
                dao.putOpenTabs(parsed.openTabs)
                dao.putDownloadNameReservations(parsed.reservations)
                dao.putPlatformDownloadIds(parsed.platformIds)

                check(dao.bookmarkCount() == parsed.bookmarks.size)
                check(dao.historyCount() == parsed.history.size)
                check(dao.savedSessionCount() == parsed.sessions.size)
                check(dao.downloadRecordCount() == parsed.downloads.size)
            }

            check(
                marker.edit()
                    .putInt(KEY_VERSION, VERSION)
                    .putLong(KEY_COMPLETED_AT, System.currentTimeMillis())
                    .putString(KEY_DIGEST, parsed.digest)
                    .commit()
            ) { "Could not commit storage migration marker" }

            LegacyStorageMigrationReport(
                parsed.bookmarks.size, parsed.folders.size, parsed.history.size,
                parsed.sessions.size, parsed.recentlyClosed.size, parsed.permissions.size,
                parsed.downloads.size, parsed.openTabs.size, parsed.platformIds.size,
                parsed.reservations.size, parsed.digest
            )
        }
    }

    companion object {
        private const val LEGACY_PREFS = "browsex.browser"
        private const val MARKER_PREFS = "browsex.storage_migration"
        private const val KEY_VERSION = "completed_version"
        private const val KEY_COMPLETED_AT = "completed_at"
        private const val KEY_DIGEST = "snapshot_sha256"
        private const val VERSION = 1
        private val migrationMutex = Mutex()
    }
}
