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
 * Imports one immutable legacy snapshot. The completion state is committed in Room
 * alongside the imported rows, so losing the SharedPreferences mirror cannot cause
 * a second import to erase data created after the first import.
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
            // Always consult Room before honoring the SharedPreferences mirror. The Room
            // migration row is authoritative; the mirror may outlive a cleared/recreated DB.
            val immutableSnapshot = source.all.mapValues { (_, value) ->
                if (value is Set<*>) value.filterIsInstance<String>().toSet() else value
            }
            val parsed = LegacyStorageSnapshotParser.parse(immutableSnapshot)
            val db = database
            var imported = false
            val state = db.withTransaction {
                val dao = db.storageDao()
                val previous = dao.migrationState()
                if (previous != null && previous.version >= VERSION) {
                    previous
                } else {
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

                    check(dao.folderCount() == parsed.folders.size)
                    check(dao.bookmarkCount() == parsed.bookmarks.size)
                    check(dao.historyCount() == parsed.history.size)
                    check(dao.savedSessionCount() == parsed.sessions.size)
                    check(dao.sessionTabCount() == parsed.sessionTabs.size)
                    check(dao.recentlyClosedCount() == parsed.recentlyClosed.size)
                    check(dao.permissionDecisionCount() == parsed.permissions.size)
                    check(dao.downloadRecordCount() == parsed.downloads.size)
                    check(dao.openTabCount() == parsed.openTabs.size)
                    check(dao.downloadNameReservationCount() == parsed.reservations.size)
                    check(dao.platformDownloadIdCount() == parsed.platformIds.size)

                    imported = true
                    StorageMigrationStateEntity(
                        version = VERSION,
                        digest = parsed.digest,
                        completedAt = System.currentTimeMillis()
                    ).also { dao.putMigrationState(it) }
                }
            }

            // This marker is only a fast-path mirror; the Room row is authoritative.
            check(
                marker.edit()
                    .putInt(KEY_VERSION, state.version)
                    .putLong(KEY_COMPLETED_AT, state.completedAt)
                    .putString(KEY_DIGEST, state.digest)
                    .commit()
            ) { "Could not commit storage migration marker" }

            if (!imported) return@withContext null
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
