package com.browsex.app

internal data class StreamDownloadRecord(
    val id: String,
    val displayName: String,
    val uri: String,
    val mimeType: String,
    val bytesWritten: Long,
    val createdAt: Long,
    val failed: Boolean = false,
    val error: String = ""
)
