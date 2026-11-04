package com.browsex.app

internal data class StreamDownloadRecord(
    val id: String,
    val displayName: String,
    val uri: String,
    val mimeType: String,
    val bytesWritten: Long,
    val createdAt: Long,
    val failed: Boolean = false,
    val error: String = "",
    val status: String = if (failed) "failed" else "completed",
    val totalBytes: Long = -1L
) {
    val isActive: Boolean get() = status == "queued" || status == "running"
    val isCancelled: Boolean get() = status == "cancelled"
}
