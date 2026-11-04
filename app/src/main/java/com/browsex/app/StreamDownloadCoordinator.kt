package com.browsex.app

import org.mozilla.geckoview.WebResponse

/**
 * Owns the metadata and completion lifecycle for original Gecko response streams.
 * Android permission prompting and user-facing notifications remain in the Activity.
 */
internal class StreamDownloadCoordinator(
    private val store: BrowserStore,
    private val writer: DownloadResponseWriter
) {
    fun start(
        response: WebResponse,
        onCompleted: (DownloadResponseWriter.Result) -> Unit,
        onFailed: (Throwable?, Boolean) -> Unit
    ): Boolean {
        val body = response.body ?: return false
        val url = response.uri
        val disposition = response.headers.entries.firstOrNull {
            it.key.equals("content-disposition", ignoreCase = true)
        }?.value
        val mime = response.headers.entries.firstOrNull {
            it.key.equals("content-type", ignoreCase = true)
        }?.value?.substringBefore(';')
        val candidate = DownloadFileNamePolicy.sanitize(
            android.webkit.URLUtil.guessFileName(url, disposition, mime)
        )
        val filename = store.reserveDownloadFileName(candidate)
        val totalBytes = response.headers.entries.firstOrNull {
            it.key.equals("content-length", ignoreCase = true)
        }?.value?.toLongOrNull()?.takeIf { it >= 0L } ?: -1L
        val downloadId = store.beginStreamDownload(
            displayName = filename,
            mimeType = mime ?: "application/octet-stream",
            totalBytes = totalBytes
        )
        runCatching { response.setReadTimeoutMillis(120_000L) }
        writer.write(
            id = downloadId,
            body = body,
            displayName = filename,
            mimeType = mime,
            onProgress = { bytes -> store.updateStreamDownloadProgress(downloadId, bytes) }
        ) { result, error, cancelled ->
            if (result != null) {
                store.completeStreamDownload(downloadId, result.uri.toString(), result.bytesWritten)
                onCompleted(result)
            } else {
                store.failStreamDownload(
                    downloadId,
                    if (cancelled) "Cancelled by user" else error?.message ?: "Download failed",
                    cancelled = cancelled
                )
                onFailed(error, cancelled)
            }
        }
        return true
    }

    /**
     * Requests cancellation only. The writer owns the terminal callback and persists
     * exactly one final state after the response stream has actually stopped.
     */
    fun cancel(downloadId: String): Boolean = writer.cancel(downloadId)

    fun shutdown() = writer.shutdown()
}
