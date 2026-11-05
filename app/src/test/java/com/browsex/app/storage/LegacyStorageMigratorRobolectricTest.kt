package com.browsex.app.storage

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LegacyStorageMigratorRobolectricTest {
    @Test
    fun migratesLegacySnapshotWithoutChangingSourceAndRetriesIdempotently() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val suffix = UUID.randomUUID().toString()
        val source = context.getSharedPreferences("migration-source-$suffix", Context.MODE_PRIVATE)
        val marker = context.getSharedPreferences("migration-marker-$suffix", Context.MODE_PRIVATE)
        val database = Room.inMemoryDatabaseBuilder(
            context,
            BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val folders = JSONArray()
                .put(JSONObject().put("id", "unfiled").put("name", "Unfiled"))
                .put(JSONObject().put("id", "work").put("name", "Work"))
            val bookmarks = JSONArray().put(
                JSONObject().put("id", "bookmark-work-1")
                    .put("url", "https://example.test/docs")
                    .put("title", "Docs")
                    .put("folderId", "work")
                    .put("createdAt", 100L)
            )
            val history = JSONArray().put(
                JSONObject().put("url", "https://example.test/docs")
                    .put("title", "Docs")
                    .put("visitedAt", 200L)
            )
            val sessions = JSONArray().put(
                JSONObject().put("id", "session-1")
                    .put("name", "Research")
                    .put("urls", JSONArray().put("https://example.test/docs").put("https://other.test"))
                    .put("activeIndex", 1)
                    .put("savedAt", 300L)
            )
            val recentlyClosed = JSONArray().put(
                JSONObject().put("id", "closed-1")
                    .put("url", "https://closed.test")
                    .put("title", "Closed")
                    .put("closedAt", 400L)
            )
            val permissions = JSONObject().put("https://example.test|camera", "allow")
            val downloads = JSONArray().put(
                JSONObject().put("id", "stream-1")
                    .put("displayName", "report.pdf")
                    .put("mimeType", "application/pdf")
                    .put("uri", "content://downloads/report")
                    .put("bytesWritten", 512L)
                    .put("totalBytes", 512L)
                    .put("status", "completed")
                    .put("createdAt", 500L)
            )
            val openTabs = JSONArray().put("https://example.test/docs").put("file:///private")
            val names = JSONArray().put("report.pdf").put("REPORT.PDF")
            val platformIds = JSONArray().put(101L).put(102L)

            source.edit()
                .putString("bookmark_folders_v1", folders.toString())
                .putString("bookmark_items_v1", bookmarks.toString())
                .putString("history_entries_v1", history.toString())
                .putString("named_sessions_v1", sessions.toString())
                .putString("recently_closed_tabs_v1", recentlyClosed.toString())
                .putString("site_media_permissions", permissions.toString())
                .putString("stream_downloads_v1", downloads.toString())
                .putString("open_tabs", openTabs.toString())
                .putInt("active_tab_index", 0)
                .putString("browsex_download_names", names.toString())
                .putString("browsex_download_ids", platformIds.toString())
                .commit()

            val sourceBefore = source.all.toMap()
            val migrator = LegacyStorageMigrator(context, source, marker, database)
            val report = migrator.migrateIfNeeded()

            assertNotNull(report)
            assertEquals(1, report?.bookmarkCount)
            assertEquals(2, report?.folderCount)
            assertEquals(1, report?.historyCount)
            assertEquals(1, report?.sessionCount)
            assertEquals(1, report?.recentlyClosedCount)
            assertEquals(1, report?.permissionCount)
            assertEquals(1, report?.downloadCount)
            assertEquals(1, report?.openTabCount)
            assertEquals(2, report?.platformDownloadIdCount)
            assertEquals(1, report?.reservationCount)

            val dao = database.storageDao()
            assertEquals("work", dao.bookmarks().single().folderId)
            assertEquals("https://example.test/docs", dao.history(10).single().url)
            assertEquals("session-1", dao.savedSessions().single().id)
            assertEquals(2, dao.savedSessionTabs().size)
            assertEquals("closed-1", dao.recentlyClosedTabs().single().id)
            assertEquals("camera", dao.permissionDecisions().single().feature)
            assertEquals("completed", dao.downloads().single().status)
            assertEquals("https://example.test/docs", dao.openTabs().single().url)
            assertEquals(1, dao.browserState().single().activeIndex)
            assertEquals(1, dao.downloadNameReservations().size)
            assertEquals(setOf(101L, 102L), dao.platformDownloadIds().map { it.downloadId }.toSet())
            assertEquals(sourceBefore, source.all.toMap())
            assertEquals(1, marker.getInt("completed_version", 0))
            assertFalse(source.all.containsKey("cookies"))

            // A completed marker makes a repeated app-start call a no-op.
            assertNull(migrator.migrateIfNeeded())

            // Simulate recovery after the Room transaction committed but before the
            // completion marker was durable. Re-import replaces the owned tables atomically.
            marker.edit().remove("completed_version").commit()
            val retried = migrator.migrateIfNeeded()
            assertNotNull(retried)
            assertEquals(1, dao.bookmarks().size)
            assertEquals(1, dao.history(10).size)
            assertEquals(1, dao.savedSessions().size)
            assertEquals(2, dao.savedSessionTabs().size)
            assertEquals(1, dao.downloads().size)
            assertEquals(sourceBefore, source.all.toMap())
        } finally {
            database.close()
            source.edit().clear().commit()
            marker.edit().clear().commit()
        }
    }

    @Test
    fun failedForeignKeyImportRollsBackAllNewRowsAndLeavesMarkerUnset() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val suffix = UUID.randomUUID().toString()
        val source = context.getSharedPreferences("migration-fk-source-$suffix", Context.MODE_PRIVATE)
        val marker = context.getSharedPreferences("migration-fk-marker-$suffix", Context.MODE_PRIVATE)
        val database = Room.inMemoryDatabaseBuilder(
            context,
            BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            // The parser repairs missing folder references, so create an intentional
            // constraint failure with a pre-existing bookmark whose folder is absent.
            val dao = database.storageDao()
            dao.putBookmarks(listOf(
                BookmarkEntity("preexisting", "https://existing.test", "Existing", "missing", 0, 1L)
            ))
            source.edit()
                .putString(
                    "bookmark_folders_v1",
                    JSONArray().put(JSONObject().put("id", "unfiled").put("name", "Unfiled")).toString()
                )
                .putString(
                    "bookmark_items_v1",
                    JSONArray().put(JSONObject().put("id", "new-item")
                        .put("url", "https://new.test").put("folderId", "unfiled")).toString()
                )
                .commit()

            val migrator = LegacyStorageMigrator(context, source, marker, database)
            val result = runCatching { migrator.migrateIfNeeded() }

            assertTrue(result.isFailure)
            assertEquals(0, dao.bookmarkCount())
            assertEquals(0, dao.historyCount())
            assertEquals(0, marker.getInt("completed_version", 0))
        } finally {
            database.close()
            source.edit().clear().commit()
            marker.edit().clear().commit()
        }
    }
}
