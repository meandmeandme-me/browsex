package com.browsex.app

import com.browsex.app.storage.BrowserStorageRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
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
    private val pending = ConcurrentHashMap<String, PendingDownload>()

    private class PendingDownload {
        val lock = Any()
        var writerStarted = false
        var cancelled = false
    }

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
            var pendingDownload: PendingDownload? = null
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
                pendingDownload = PendingDownload().also { pending[createdId] = it }
                check(repository.markRunning(createdId)) {
                    "Could not start the persisted download record"
                }
                runCatching { response.setReadTimeoutMillis(120_000L) }
                val lastPersistedProgress = AtomicLong(0L)
                val transferLock = pendingDownload.lock
                synchronized(transferLock) {
                    if (pendingDownload.cancelled) throw CancellationException("Download cancelled")
                    pendingDownload.writerStarted = true
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
                            try {
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
                                    if (persisted) {
                                        onCompleted(result)
                                    } else {
                                        // Keep the file: Android may already expose its completed URI.
                                        runCatching {
                                            repository.fail(
                                                createdId,
                                                "File was saved, but BrowseX could not update its download history"
                                            )
                                        }.onFailure { persistenceError ->
                                            android.util.Log.e(
                                                "BrowseX",
                                                "Could not finalize failed download metadata",
                                                persistenceError
                                            )
                                        }
                                        onFailed(
                                            IllegalStateException(
                                                "File was saved, but BrowseX could not update its download history"
                                            ),
                                            false
                                        )
                                    }
                                } else {
                                    if (cancelled) repository.cancel(createdId)
                                    else repository.fail(createdId, error?.message ?: "Download failed")
                                    onFailed(error, cancelled)
                                }
                            } finally {
                                pending.remove(createdId, pendingDownload)
                            }
                        }
                    }
                    bodyOwnershipTransferred.set(true)
                }
            } catch (error: Throwable) {
                if (bodyOwnershipTransferred.compareAndSet(false, true)) {
                    runCatching { body.close() }
                }
                if (downloadId != null) {
                    if (error is CancellationException) {
                        runCatching { storage.awaitReady().downloads.cancel(downloadId) }
                    } else {
                        runCatching {
                            storage.awaitReady().downloads.fail(downloadId, error.message ?: "Download failed")
                        }
                    }
                    pending.remove(downloadId, pendingDownload)
                }
                onFailed(error, error is CancellationException)
            }
        }
        // If shutdown cancels this coroutine before it reaches the writer, close Gecko's
        // one-shot body as well. Once handed off, the writer owns stream cleanup.
        job.invokeOnCompletion { cause ->
            if (cause != null && bodyOwnershipTransferred.compareAndSet(false, true)) {
                closeDownloadBodySafely { body.close() }
            }
        }
        return true
    }

    /**
     * Cancellation is serialized with stream handoff. This closes the race where a user
     * presses Cancel after Room creates the row but before the writer registers its job.
     */
    fun cancel(downloadId: String): Boolean {
        val entry = pending[downloadId] ?: return writer.cancel(downloadId)
        synchronized(entry.lock) {
            if (entry.cancelled) return false
            entry.cancelled = true
            return if (entry.writerStarted) writer.cancel(downloadId) else true
        }
    }

    fun shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) return
        writer.shutdown()
    }

    private companion object {
        const val PROGRESS_PERSIST_INTERVAL_BYTES = 1024L * 1024L
    }
}
