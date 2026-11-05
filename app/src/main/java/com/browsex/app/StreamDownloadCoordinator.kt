package com.browsex.app

import com.browsex.app.storage.BrowserStorageRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.mozilla.geckoview.WebResponse

/**
 * Owns the metadata and completion lifecycle for original Gecko response streams.
 * Metadata is committed to Room before the response body is handed to the writer.
 */
internal class StreamDownloadCoordinator(
    private val storage: BrowserStorageRuntime,
    private val writer: DownloadResponseWriter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        val totalBytes = response.headers.entries.firstOrNull {
            it.key.equals("content-length", ignoreCase = true)
        }?.value?.toLongOrNull()?.takeIf { it >= 0L } ?: -1L

        scope.launch {
            var downloadId: String? = null
            try {
                val repository = storage.awaitReady().downloads
                val filename = repository.reserveFileName(candidate)
                    ?: throw IllegalStateException("Could not reserve a download filename")
                val createdId = repository.beginStream(
                    displayName = filename,
                    mimeType = mime ?: "application/octet-stream",
                    totalBytes = totalBytes
                ) ?: throw IllegalStateException("BrowseX download history is full")
                downloadId = createdId
                runCatching { response.setReadTimeoutMillis(120_000L) }
                writer.write(
                    id = createdId,
                    body = body,
                    displayName = filename,
                    mimeType = mime,
                    onProgress = { bytes ->
                        scope.launch { repository.updateProgress(createdId, bytes) }
                    }
                ) { result, error, cancelled ->
                    scope.launch {
                        if (result != null) {
                            repository.complete(createdId, result.uri.toString(), result.bytesWritten)
                            onCompleted(result)
                        } else {
                            if (cancelled) repository.cancel(createdId)
                            else repository.fail(createdId, error?.message ?: "Download failed")
                            onFailed(error, cancelled)
                        }
                    }
                }
            } catch (error: Throwable) {
                runCatching { body.close() }
                if (downloadId != null) {
                    runCatching { storage.awaitReady().downloads.fail(downloadId, error.message ?: "Download failed") }
                }
                onFailed(error, false)
            }
        }
        return true
    }

    /** The writer owns the terminal callback and persists exactly one final state. */
    fun cancel(downloadId: String): Boolean = writer.cancel(downloadId)

    fun shutdown() {
        writer.shutdown()
        scope.cancel()
    }
}
