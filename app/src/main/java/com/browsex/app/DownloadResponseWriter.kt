package com.browsex.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persists the exact response body GeckoView already received.
 *
 * The writer never re-fetches the URL. Cancellation closes the original stream,
 * and partial MediaStore/filesystem output is deleted before completion is reported.
 */
internal class DownloadResponseWriter(context: Context) {
    private val appContext = context.applicationContext
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8)
    ) { runnable -> Thread(runnable, "BrowseX-download-writer").apply { isDaemon = true } }
    private val jobs = ConcurrentHashMap<String, Job>()

    data class Result(val uri: Uri, val displayName: String, val bytesWritten: Long)

    private class Job(
        val body: InputStream,
        val onProgress: (Long) -> Unit,
        val onComplete: (Result?, Throwable?, Boolean) -> Unit
    ) {
        val cancelled = AtomicBoolean(false)
        val completionLock = Any()
        // Guarded by completionLock: once finalized, cancellation must not report success.
        var finalized = false
    }

    fun write(
        id: String,
        body: InputStream,
        displayName: String,
        mimeType: String?,
        onProgress: (Long) -> Unit,
        onComplete: (Result?, Throwable?, Boolean) -> Unit
    ) {
        val job = Job(body, onProgress, onComplete)
        if (jobs.putIfAbsent(id, job) != null) {
            runCatching { body.close() }
            onComplete(null, IllegalStateException("Duplicate download identifier"), false)
            return
        }
        try {
            executor.execute {
                var pendingUri: Uri? = null
                var pendingFile: File? = null
                var completed: Result? = null
                var failure: Throwable? = null
                try {
                    checkNotCancelled(job)
                    val safeName = DownloadFileNamePolicy.sanitize(displayName)
                    val mime = mimeType?.takeIf { it.contains('/') && it.length <= 120 }
                        ?: "application/octet-stream"
                    completed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                            put(MediaStore.Downloads.MIME_TYPE, mime)
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BrowseX")
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: error("Android could not create the download file")
                        pendingUri = uri
                        val bytes = appContext.contentResolver.openOutputStream(uri, "w")?.use { output ->
                            copyBody(job, output)
                        } ?: error("Android could not open the download file")
                        synchronized(job.completionLock) {
                            checkNotCancelled(job)
                            val complete = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                            check(appContext.contentResolver.update(uri, complete, null, null) > 0) {
                                "Android could not finalize the download file"
                            }
                            pendingUri = null
                            job.finalized = true
                        }
                        Result(uri, safeName, bytes)
                    } else {
                        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!directory.exists() && !directory.mkdirs()) error("Downloads folder is unavailable")
                        val file = uniqueFile(directory, safeName)
                        pendingFile = file
                        val bytes = FileOutputStream(file).use { output -> copyBody(job, output) }
                        val contentUri = FileProvider.getUriForFile(
                            appContext, appContext.packageName + ".files", file
                        )
                        synchronized(job.completionLock) {
                            checkNotCancelled(job)
                            pendingFile = null
                            job.finalized = true
                        }
                        Result(contentUri, file.name, bytes)
                    }
                } catch (error: Throwable) {
                    failure = error
                    runCatching { pendingUri?.let { appContext.contentResolver.delete(it, null, null) } }
                    runCatching { pendingFile?.delete() }
                } finally {
                    runCatching { body.close() }
                    jobs.remove(id, job)
                }
                val cancelled = job.cancelled.get() || failure is CancellationException
                job.onComplete(if (cancelled) null else completed, failure, cancelled)
            }
        } catch (_: RejectedExecutionException) {
            jobs.remove(id, job)
            runCatching { body.close() }
            onComplete(null, IllegalStateException("Too many downloads are already queued"), false)
        }
    }

    fun cancel(id: String): Boolean {
        val job = jobs[id] ?: return false
        val shouldClose = synchronized(job.completionLock) {
            if (job.finalized || !job.cancelled.compareAndSet(false, true)) false else true
        }
        if (!shouldClose) return false
        runCatching { job.body.close() }
        return true
    }

    private fun copyBody(job: Job, output: java.io.OutputStream): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        var lastReported = 0L
        while (true) {
            checkNotCancelled(job)
            val count = job.body.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
            total += count
            if (total - lastReported >= PROGRESS_INTERVAL_BYTES) {
                lastReported = total
                job.onProgress(total)
            }
        }
        checkNotCancelled(job)
        output.flush()
        if (total != lastReported) job.onProgress(total)
        return total
    }

    private fun checkNotCancelled(job: Job) {
        if (job.cancelled.get()) throw CancellationException("Download cancelled")
    }

    private fun uniqueFile(directory: File, requestedName: String): File {
        val dot = requestedName.lastIndexOf('.').takeIf { it > 0 } ?: requestedName.length
        val stem = requestedName.substring(0, dot)
        val extension = requestedName.substring(dot)
        var candidate = File(directory, requestedName)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "$stem ($suffix)$extension")
            suffix++
        }
        return candidate
    }

    fun shutdown() {
        jobs.keys.toList().forEach(::cancel)
        executor.shutdown()
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_BYTES = 256 * 1024
    }
}
