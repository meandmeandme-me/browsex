package com.browsex.app

internal data class BookmarkImportPreview(
    val totalItems: Int,
    val validItems: Int,
    val skippedItems: Int,
    val duplicateIncomingItems: Int,
    val existingUrlConflicts: Int,
    val incomingFolders: Int
)
