package com.wren.app.storage

import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Interoperable Netscape bookmark HTML reader/writer used by desktop and mobile browsers. */
internal object BookmarkHtmlCodec {
    private const val MAX_ITEMS = 1_000
    private const val MAX_FOLDERS = 50
    private val tokenPattern = Regex(
        "(?is)<H3\\b[^>]*>(.*?)</H3>|<DL\\b[^>]*>|</DL\\s*>|<A\\b([^>]*)>(.*?)</A>"
    )
    private val hrefPattern = Regex("(?is)\\bHREF\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")

    fun toBackupJson(html: String): String {
        require(html.length <= 1_000_000) { "Bookmark file is too large" }
        require(html.contains("<A", ignoreCase = true)) { "No bookmarks found in HTML file" }
        val folderIds = linkedMapOf<String, String>()
        val folders = JSONArray()
        val bookmarks = JSONArray()
        val folderStack = mutableListOf<String>()
        var pendingFolder: String? = null

        tokenPattern.findAll(html).forEach { match ->
            when {
                match.groups[1] != null -> {
                    pendingFolder = decode(match.groupValues[1]).take(40).takeIf(String::isNotBlank)
                }
                match.value.startsWith("<DL", ignoreCase = true) -> {
                    folderStack.add(pendingFolder.orEmpty())
                    pendingFolder = null
                }
                match.value.startsWith("</DL", ignoreCase = true) -> {
                    if (folderStack.isNotEmpty()) folderStack.removeAt(folderStack.lastIndex)
                }
                match.groups[2] != null -> {
                    if (bookmarks.length() >= MAX_ITEMS) return@forEach
                    val attributes = match.groupValues[2]
                    val href = hrefPattern.find(attributes)?.let { found ->
                        found.groups[1]?.value ?: found.groups[2]?.value ?: found.groups[3]?.value.orEmpty()
                    }?.let(::decode)?.trim().orEmpty()
                    if (href.isBlank()) return@forEach
                    val title = decode(match.groupValues[3]).take(160).ifBlank { href }
                    val folderName = folderStack.lastOrNull()?.takeIf(String::isNotBlank)
                    val folderId = folderName?.let { name ->
                        folderIds.getOrPut(name.lowercase(java.util.Locale.ROOT)) {
                            val id = "html_${UUID.randomUUID()}"
                            if (folders.length() < MAX_FOLDERS) {
                                folders.put(JSONObject().put("id", id).put("name", name))
                                id
                            } else "unfiled"
                        }
                    } ?: "unfiled"
                    bookmarks.put(JSONObject()
                        .put("url", href)
                        .put("title", title)
                        .put("folderId", folderId))
                }
            }
        }
        require(bookmarks.length() > 0) { "No usable bookmarks found in HTML file" }
        return JSONObject()
            .put("format", "wren-bookmarks")
            .put("version", 1)
            .put("folders", folders)
            .put("bookmarks", bookmarks)
            .toString()
    }

    fun fromBookmarks(folders: List<BookmarkFolderEntity>, bookmarks: List<BookmarkEntity>): String {
        val out = StringBuilder()
        out.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
            .append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
            .append("<TITLE>Wren Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
        val folderById = folders.associateBy { it.id }
        bookmarks.filter { it.folderId == "unfiled" }.forEach { appendBookmark(out, it) }
        folders.filter { it.id != "unfiled" }.forEach { folder ->
            out.append("<DT><H3>").append(escape(folder.name)).append("</H3>\n<DL><p>\n")
            bookmarks.filter { it.folderId == folder.id }.forEach { appendBookmark(out, it) }
            out.append("</DL><p>\n")
        }
        bookmarks.filter { it.folderId != "unfiled" && it.folderId !in folderById }.forEach {
            appendBookmark(out, it)
        }
        out.append("</DL><p>\n")
        return out.toString()
    }

    private fun appendBookmark(out: StringBuilder, item: BookmarkEntity) {
        out.append("<DT><A HREF=\"").append(escape(item.url)).append("\">")
            .append(escape(item.title)).append("</A>\n")
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun decode(value: String): String =
        Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().trim()
}
