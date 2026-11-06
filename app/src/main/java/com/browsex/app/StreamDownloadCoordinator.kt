package com.browsex.app

import com.browsex.app.storage.BrowserStorageRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
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
    private val shuttingDown = AtomicBoolean(false)

    fun start(
        response: WebResponse,
        onCompleted: (DownloadResponseWriter.Result) -> Unit,
        onFailed: (Throwable?, Boolean) -> Unit
    ): Boolean {
        val body = response.body ?: return false
        if (shuttingDown.get()) {
            runCatching { body.close() }
            return false
        }
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

        val bodyOwnershipTransferred = AtomicBoolean(false)
        val job = scope.launch {
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
                check(repository.markRunning(createdId)) {
                    "Could not start the persisted download record"
                }
                runCatching { response.setReadTimeoutMillis(120_000L) }
                val lastPersistedProgress = AtomicLong(0L)
                writer.write(
                    id = createdId,
                    body = body,
                    displayName = filename,
                    mimeType = mime,
                    onProgress = { bytes ->
                        // The writer reports frequently for responsive UI, but Room does not
                        // need a transaction for every small chunk of a large file.
                        while (true) {
                            val previous = lastPersistedProgress.get()
                            if (bytes - previous < PROGRESS_PERSIST_INTERVAL_BYTES) break
                            if (lastPersistedProgress.compareAndSet(previous, bytes)) {
                                scope.launch { repository.updateProgress(createdId, bytes) }
                                break
                            }
                        }
                    }
                ) { result, error, cancelled ->
                    scope.launch {
                        if (result != null) {
                            val persisted = runCatching {
                                repository.complete(createdId, result.uri.toString(), result.bytesWritten)
                            }.getOrElse { persistenceError ->
                                android.util.Log.e(
                                    "BrowseX",
                                    "File was written but its completed state could not be persisted",
                                    persistenceError
                                )
                                onFailed(
                                    IllegalStateException(
                                        "File was saved, but BrowseX could not update its download history",
                                        persistenceError
                                    ),
                                    false
                                )
                                return@launch
                            }
                            if (persisted) onCompleted(result)
                            else onFailed(
                                IllegalStateException(
                                    "File was saved, but BrowseX could not update its download history"
                                ),
                                false
                            )
                        } else {
                            if (cancelled) repository.cancel(createdId)
                            else repository.fail(createdId, error?.message ?: "Download failed")
                            onFailed(error, cancelled)
                        }
                    }
                }
                bodyOwnershipTransferred.set(true)
            } catch (error: Throwable) {
                if (bodyOwnershipTransferred.compareAndSet(false, true)) {
                    runCatching { body.close() }
                }
                if (downloadId != null) {
                    runCatching { storage.awaitReady().downloads.fail(downloadId, error.message ?: "Download failed") }
                }
                onFailed(error, false)
            }
        }
        // If shutdown cancels this coroutine before it reaches the writer, close Gecko's
        // one-shot body as well. Once handed off, the writer owns stream cleanup.
        job.invokeOnCompletion { cause ->
            if (cause != null && bodyOwnershipTransferred.compareAndSet(false, true)) {
                closeDownloadBodySafely { body.close() }
            }
        }
        // writer.write is synchronous; the first call that reaches it transfers ownership.
        // The marker is set by the writer callback registration path below.
        return true
    }

    /** The writer owns the terminal callback and persists exactly one final state. */
    fun cancel(downloadId: String): Boolean = writer.cancel(downloadId)

    fun shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) return
        // Keep the persistence scope alive while the writer drains cancelled jobs:
        // their terminal callbacks must still commit cancelled/failed states to Room.
        // The scope is owned by this coordinator and becomes collectible with it.
        writer.shutdown()
    }

    private companion object {
        const val PROGRESS_PERSIST_INTERVAL_BYTES = 1024L * 1024L
    }
}
