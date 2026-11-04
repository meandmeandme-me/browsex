package com.browsex.app

/** Stable, serializable bookmark models. Gecko sessions and credentials never belong here. */
internal data class BookmarkFolder(val id: String, val name: String) {
    companion object {
        const val DEFAULT_ID = "unfiled"
        val DEFAULT = BookmarkFolder(DEFAULT_ID, "Unfiled")
    }
}

internal data class BookmarkItem(
    val id: String,
    val url: String,
    val title: String,
    val folderId: String,
    val createdAt: Long
)
