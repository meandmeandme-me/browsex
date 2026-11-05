package com.browsex.app.storage

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyStorageSnapshotParserTest {
    @Test
    fun legacyCollectionsFilterUnsafeUrlsAndDeduplicateDeterministically() {
        val source = mapOf(
            "bookmarks" to JSONArray().put("https://example.com").put("https://example.com")
                .put("javascript:alert(1)").toString(),
            "history" to JSONArray().put("https://news.example").put("file:///private").toString()
        )
        val first = LegacyStorageSnapshotParser.parse(source)
        val second = LegacyStorageSnapshotParser.parse(source)
        assertEquals(1, first.bookmarks.size)
        assertEquals(1, first.history.size)
        assertEquals(first.bookmarks.single().id, second.bookmarks.single().id)
        assertEquals("unfiled", first.bookmarks.single().folderId)
    }

    @Test
    fun legacyMirrorsBackfillValidRowsMissingFromDetailedJson() {
        val bookmarks = JSONArray()
            .put(JSONObject().put("id", "detailed").put("url", "https://detailed.example"))
        val history = JSONArray()
            .put(JSONObject().put("url", "https://detailed.example").put("title", "Detailed"))
        val parsed = LegacyStorageSnapshotParser.parse(mapOf(
            "bookmark_items_v1" to bookmarks.toString(),
            "bookmarks" to JSONArray().put("https://detailed.example")
                .put("https://legacy.example").toString(),
            "history_entries_v1" to history.toString(),
            "history" to JSONArray().put("https://detailed.example")
                .put("https://legacy-history.example").toString()
        ))
        assertEquals(2, parsed.bookmarks.size)
        assertEquals(2, parsed.history.size)
        assertEquals("detailed", parsed.bookmarks.first().id)
    }

    @Test
    fun missingBookmarkFoldersAndReferencesAreRepairedToUnfiled() {
        val items = JSONArray()
            .put(JSONObject().put("id", "stable-id").put("url", "https://example.com").put("folderId", "missing"))
        val parsed = LegacyStorageSnapshotParser.parse(mapOf("bookmark_items_v1" to items.toString()))
        assertEquals("unfiled", parsed.bookmarks.single().folderId)
        assertTrue(parsed.folders.any { it.id == "unfiled" })
    }

    @Test
    fun tabsSessionsAndRecentlyClosedRejectUnsupportedSchemes() {
        val sessions = JSONArray().put(
            JSONObject().put("id", "s1").put("name", "Research")
                .put("urls", JSONArray().put("https://one.example").put("file:///private").put("https://two.example"))
                .put("activeIndex", 2)
        )
        val parsed = LegacyStorageSnapshotParser.parse(mapOf(
            "open_tabs" to JSONArray().put("https://safe.example").put("file:///private").toString(),
            "active_tab_index" to 1,
            "named_sessions_v1" to sessions.toString(),
            "recently_closed_tabs_v1" to JSONArray()
                .put(JSONObject().put("id", "bad").put("url", "file:///private")).toString()
        ))
        assertEquals(1, parsed.openTabs.size)
        assertEquals("https://safe.example", parsed.openTabs.single().url)
        assertEquals(2, parsed.sessionTabs.size)
        assertEquals(1, parsed.sessions.single().activeIndex)
        assertTrue(parsed.recentlyClosed.isEmpty())
    }

    @Test
    fun emptyOrInvalidNamedSessionsAreNotRecreatedAsHomeSessions() {
        val sessions = JSONArray()
            .put(JSONObject().put("id", "empty").put("name", "Empty").put("urls", JSONArray()))
            .put(JSONObject().put("id", "invalid").put("name", "Invalid")
                .put("urls", JSONArray().put("file:///private")))
        val parsed = LegacyStorageSnapshotParser.parse(mapOf("named_sessions_v1" to sessions.toString()))
        assertTrue(parsed.sessions.isEmpty())
        assertTrue(parsed.sessionTabs.isEmpty())
    }

    @Test
    fun permissionAndDownloadTablesKeepSeparateSemantics() {
        val permissions = JSONObject().put("https://example.com|camera", "allow")
            .put("https://example.com|location", "allow").put("file:///bad|camera", "allow")
        val downloads = JSONArray().put(
            JSONObject().put("id", "stream-1").put("displayName", "video.mp4")
                .put("status", "running").put("mimeType", "video/mp4").put("bytesWritten", 42L)
        )
        val parsed = LegacyStorageSnapshotParser.parse(mapOf(
            "site_media_permissions" to permissions.toString(),
            "stream_downloads_v1" to downloads.toString(),
            "browsex_download_ids" to JSONArray().put(100L).toString()
        ))
        assertEquals(1, parsed.permissions.size)
        assertEquals("failed", parsed.downloads.single().status)
        assertEquals(1, parsed.platformIds.size)
    }

    @Test
    fun duplicatePrimaryIdsNeverOverwriteDistinctLegacyRows() {
        val bookmarks = JSONArray()
            .put(JSONObject().put("id", "duplicate").put("url", "https://one.example"))
            .put(JSONObject().put("id", "duplicate").put("url", "https://two.example"))
        val sessions = JSONArray()
            .put(JSONObject().put("id", "same-session").put("name", "First")
                .put("urls", JSONArray().put("https://one.example")))
            .put(JSONObject().put("id", "same-session").put("name", "Second")
                .put("urls", JSONArray().put("https://two.example")))
        val downloads = JSONArray()
            .put(JSONObject().put("id", "same-download").put("displayName", "one.bin")
                .put("status", "failed"))
            .put(JSONObject().put("id", "same-download").put("displayName", "two.bin")
                .put("status", "failed"))
        val parsed = LegacyStorageSnapshotParser.parse(mapOf(
            "bookmark_items_v1" to bookmarks.toString(),
            "named_sessions_v1" to sessions.toString(),
            "stream_downloads_v1" to downloads.toString()
        ))
        assertEquals(2, parsed.bookmarks.size)
        assertEquals(2, parsed.bookmarks.map { it.id }.distinct().size)
        assertEquals(1, parsed.sessions.size)
        assertEquals(1, parsed.sessionTabs.size)
        assertEquals(1, parsed.downloads.size)
    }

    @Test
    fun malformedJsonFailsClosedWithoutChangingTheInputSnapshot() {
        val source = mapOf("bookmark_items_v1" to "{broken", "history_entries_v1" to "{broken")
        val parsed = LegacyStorageSnapshotParser.parse(source)
        assertTrue(parsed.bookmarks.isEmpty())
        assertTrue(parsed.history.isEmpty())
        assertEquals("{broken", source["bookmark_items_v1"])
        assertTrue(parsed.digest.isNotBlank())
    }
}
