package com.browsex.app.storage

import com.browsex.app.NavigationInput
import com.browsex.app.TabSessionState
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/** Pure parser for an immutable SharedPreferences snapshot. It never writes to the legacy source. */
internal object LegacyStorageSnapshotParser {
    private const val HOME_URL = "resource://android/assets/home.html"
    private const val MAX_JSON = 1_000_000

    internal data class Parsed(
        val folders: List<BookmarkFolderEntity>,
        val bookmarks: List<BookmarkEntity>,
        val history: List<HistoryEntryEntity>,
        val sessions: List<SavedSessionEntity>,
        val sessionTabs: List<SavedSessionTabEntity>,
        val recentlyClosed: List<RecentlyClosedTabEntity>,
        val permissions: List<SitePermissionDecisionEntity>,
        val downloads: List<DownloadRecordEntity>,
        val browserState: List<BrowserStateEntity>,
        val openTabs: List<OpenTabEntity>,
        val reservations: List<DownloadNameReservationEntity>,
        val platformIds: List<PlatformDownloadIdEntity>,
        val digest: String
    )

    fun parse(source: Map<String, *>): Parsed {
        fun string(key: String): String? = source[key] as? String
        val folders = parseFolders(string("bookmark_folders_v1"))
        val folderIds = folders.map { it.id }.toSet()
        val bookmarks = parseBookmarks(string("bookmark_items_v1"), string("bookmarks"), folderIds)
        val history = parseHistory(string("history_entries_v1"), string("history"))
        val sessions = parseSessions(string("named_sessions_v1"))
        val recent = parseRecentlyClosed(string("recently_closed_tabs_v1"))
        val permissions = parsePermissions(string("site_media_permissions"))
        val downloads = parseDownloads(string("stream_downloads_v1"))
        val active = source["active_tab_index"] as? Int ?: 0
        val tabSnapshot = TabSessionState.normalize(parseStrings(string("open_tabs"), 500), active, HOME_URL)
        val openTabs = tabSnapshot.urls.mapIndexed { index, url -> OpenTabEntity(index, url) }
        return Parsed(
            folders, bookmarks, history, sessions.first, sessions.second, recent, permissions, downloads,
            listOf(BrowserStateEntity(activeIndex = tabSnapshot.activeIndex, savedAt = 0L)),
            openTabs,
            parseReservedNames(string("browsex_download_names")),
            parseDownloadIds(string("browsex_download_ids")),
            digestSnapshot(source)
        )
    }

    private data class BookmarkRow(
        val id: String, val url: String, val title: String, val folderId: String, val createdAt: Long
    )

    private fun parseFolders(raw: String?): List<BookmarkFolderEntity> {
        val result = mutableListOf(BookmarkFolderEntity("unfiled", "Unfiled", 0, 0L))
        val array = parseArray(raw, 500) ?: return result
        val ids = mutableSetOf("unfiled")
        val names = mutableSetOf("unfiled")
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (result.size >= 50) break
            val id = item.optString("id").take(80)
            val name = item.optString("name").trim().take(40)
            if (id.isBlank() || name.isBlank() || id == "unfiled") continue
            if (id !in ids && name.lowercase(Locale.ROOT) !in names) {
                ids.add(id)
                names.add(name.lowercase(Locale.ROOT))
                result.add(BookmarkFolderEntity(id, name, result.size, 0L))
            }
        }
        return result
    }

    private fun parseBookmarks(detailed: String?, legacy: String?, folderIds: Set<String>): List<BookmarkEntity> {
        val array = parseArray(detailed, 500)
        val rows = mutableListOf<BookmarkRow>()
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = validUrl(item.optString("url")) ?: continue
                rows.add(BookmarkRow(
                    item.optString("id").take(80).ifBlank { stableId("bookmark", url) },
                    url,
                    item.optString("title").trim().take(160).ifBlank { hostOf(url) },
                    item.optString("folderId").takeIf(folderIds::contains) ?: "unfiled",
                    item.optLong("createdAt", 0L).coerceAtLeast(0L)
                ))
            }
        }
        // The legacy URL-only mirror is written in the same preference transaction.
        // Merge it when detailed JSON is partial/corrupt so one malformed row cannot
        // make an otherwise valid legacy bookmark disappear from the import.
        parseStrings(legacy, 500).forEach { rawUrl ->
            val url = validUrl(rawUrl) ?: return@forEach
            if (rows.none { it.url == url }) {
                rows.add(BookmarkRow(stableId("bookmark", url), url,
                    hostOf(url).ifBlank { url }, "unfiled", 0L))
            }
        }
        val usedIds = mutableSetOf<String>()
        return rows.distinctBy { it.url }.take(200).mapIndexed { index, item ->
            var id = item.id
            if (!usedIds.add(id)) {
                var suffix = 0
                do {
                    id = stableId("bookmark-duplicate", "${item.url}#${index}#${suffix++}")
                } while (!usedIds.add(id))
            }
            BookmarkEntity(id, item.url, item.title, item.folderId, index, item.createdAt)
        }
    }

    private fun parseHistory(detailed: String?, legacy: String?): List<HistoryEntryEntity> {
        val array = parseArray(detailed, 600)
        val rows = mutableListOf<HistoryEntryEntity>()
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = validUrl(item.optString("url")) ?: continue
                rows.add(HistoryEntryEntity(
                    stableId("history", url), url,
                    item.optString("title").trim().take(160).ifBlank { hostOf(url) },
                    item.optLong("visitedAt", 0L).coerceAtLeast(0L), 1
                ))
            }
        }
        // Preserve valid URLs from the compatibility mirror if a detailed row is
        // missing; detailed metadata wins when both forms contain the same URL.
        parseStrings(legacy, 600).forEach { rawUrl ->
            val url = validUrl(rawUrl) ?: return@forEach
            if (rows.none { it.url == url }) {
                rows.add(HistoryEntryEntity(stableId("history", url), url, hostOf(url), 0L, 1))
            }
        }
        return rows.distinctBy { it.url }.take(300)
    }

    private fun parseSessions(raw: String?): Pair<List<SavedSessionEntity>, List<SavedSessionTabEntity>> {
        val array = parseArray(raw, 10) ?: return emptyList<SavedSessionEntity>() to emptyList()
        val sessions = mutableListOf<SavedSessionEntity>()
        val tabs = mutableListOf<SavedSessionTabEntity>()
        val usedIds = mutableSetOf<String>()
        val usedNames = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").take(100)
            val name = item.optString("name").trim().take(40)
            if (id.isBlank() || name.isBlank() || !usedIds.add(id) ||
                !usedNames.add(name.lowercase(Locale.ROOT))
            ) continue
            val urlArray = item.optJSONArray("urls") ?: JSONArray()
            val rawUrls = (0 until minOf(urlArray.length(), 20))
                .mapNotNull { urlArray.optString(it).takeIf(String::isNotBlank) }
            if (rawUrls.isEmpty() || rawUrls.none(::isPersistableTabUrl)) continue
            val snapshot = TabSessionState.normalize(rawUrls, item.optInt("activeIndex", 0), HOME_URL)
            sessions.add(SavedSessionEntity(id, name, snapshot.activeIndex,
                item.optLong("savedAt", 0L).coerceAtLeast(0L)))
            snapshot.urls.forEachIndexed { position, url -> tabs.add(SavedSessionTabEntity(id, position, url)) }
        }
        return sessions to tabs
    }

    private fun isPersistableTabUrl(raw: String): Boolean {
        val url = raw.trim()
        return url == HOME_URL || url == "about:blank" ||
            (url.startsWith("$HOME_URL?") &&
                TabSessionState.normalize(listOf(url), 0, HOME_URL).urls.firstOrNull() == url) ||
            NavigationInput.isValidWebUrl(url)
    }

    private fun parseRecentlyClosed(raw: String?): List<RecentlyClosedTabEntity> {
        val array = parseArray(raw, 10) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").take(100)
            val url = validUrl(item.optString("url")) ?: return@mapNotNull null
            if (id.isBlank()) return@mapNotNull null
            RecentlyClosedTabEntity(id, url,
                item.optString("title").trim().take(180).ifBlank { hostOf(url) },
                item.optLong("closedAt", 0L).coerceAtLeast(0L))
        }.distinctBy { it.id }
    }

    private fun parsePermissions(raw: String?): List<SitePermissionDecisionEntity> {
        if (raw.isNullOrBlank() || raw.length > 100_000) return emptyList()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val result = mutableListOf<SitePermissionDecisionEntity>()
        val keys = json.keys()
        while (keys.hasNext() && result.size < 100) {
            val key = keys.next()
            val split = key.lastIndexOf('|')
            if (split <= 0) continue
            val origin = key.substring(0, split)
            val feature = key.substring(split + 1)
            val decision = json.optString(key)
            if (feature !in setOf("camera", "microphone", "notifications", "geolocation") || decision !in setOf("allow", "deny")) continue
            if (!safeOrigin(origin)) continue
            result.add(SitePermissionDecisionEntity(origin, feature, decision, 0L))
        }
        return result
    }

    private fun parseDownloads(raw: String?): List<DownloadRecordEntity> {
        val array = parseArray(raw, 300) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").take(100)
            val name = item.optString("displayName").trim().take(140)
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            val rawStatus = item.optString("status",
                if (item.optBoolean("failed", false)) "failed" else "completed")
            val status = rawStatus.takeIf { it in setOf("queued", "running", "completed", "failed", "cancelled") } ?: "failed"
            val interrupted = status == "queued" || status == "running"
            val uri = item.optString("uri").take(2048)
            if (status == "completed" && uri.isBlank()) return@mapNotNull null
            DownloadRecordEntity(
                id, "stream", name, item.optString("mimeType", "application/octet-stream").take(120),
                if (interrupted) "" else uri,
                item.optLong("bytesWritten", 0L).coerceAtLeast(0L),
                item.optLong("totalBytes", -1L).coerceAtLeast(-1L),
                if (interrupted) "failed" else status,
                if (interrupted) "Download interrupted before storage migration" else item.optString("error").take(240),
                item.optLong("createdAt", 0L).coerceAtLeast(0L)
            )
        }.distinctBy { it.id }
    }

    private fun parseReservedNames(raw: String?): List<DownloadNameReservationEntity> {
        val seen = LinkedHashSet<String>()
        return parseStrings(raw, 300).mapNotNull { display ->
            val normalized = display.trim().lowercase(Locale.ROOT)
            if (normalized.isBlank() || !seen.add(normalized)) null
            else DownloadNameReservationEntity(normalized, display.take(180), seen.size - 1, 0L)
        }
    }

    private fun parseDownloadIds(raw: String?): List<PlatformDownloadIdEntity> {
        val array = parseArray(raw, 300) ?: return emptyList()
        val ids = LinkedHashSet<Long>()
        for (index in 0 until array.length()) {
            val id = array.optLong(index, -1L)
            if (id > 0L) ids.add(id)
        }
        return ids.toList().takeLast(300).mapIndexed { index, id -> PlatformDownloadIdEntity(id, index, 0L) }
    }

    private fun parseStrings(raw: String?, max: Int): List<String> =
        parseArray(raw, max)?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty()

    private fun parseArray(raw: String?, max: Int): JSONArray? {
        if (raw.isNullOrBlank() || raw.length > MAX_JSON) return null
        val original = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        return JSONArray().also { safe ->
            for (index in 0 until minOf(original.length(), max)) safe.put(original.opt(index))
        }
    }

    private fun validUrl(raw: String): String? =
        raw.trim().takeIf { it.length <= 4096 && NavigationInput.isValidWebUrl(it) }

    private fun hostOf(url: String): String = try {
        URI(url).host.orEmpty()
    } catch (_: Exception) { "" }

    private fun safeOrigin(raw: String): Boolean = try {
        val uri = URI(raw)
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.port <= 65535
    } catch (_: Exception) { false }

    private fun stableId(prefix: String, value: String): String = prefix + "-" + sha256(value)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun digestSnapshot(source: Map<String, *>): String {
        val canonical = source.toSortedMap().entries.joinToString("\n") { entry ->
            val value = entry.value
            val rendered = if (value is Set<*>) value.filterIsInstance<String>().sorted().joinToString(",")
                else value?.toString().orEmpty()
            entry.key + "=" + rendered
        }
        return sha256(canonical)
    }
}
