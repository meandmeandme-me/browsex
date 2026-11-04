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
import java.util.concurrent.Executors

/**
 * Persists the response body GeckoView already received.
 *
 * This deliberately does not make a second HTTP request: request method, POST body,
 * cookies, authorization and one-time response URLs remain those of the original
 * Gecko response. The input stream is always closed by this writer.
 */
internal class DownloadResponseWriter(context: Context) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BrowseX-download-writer").apply { isDaemon = true }
    }

    data class Result(val uri: Uri, val displayName: String, val bytesWritten: Long)

    fun write(
        body: InputStream,
        displayName: String,
        mimeType: String?,
        onComplete: (Result?, Throwable?) -> Unit
    ) {
        executor.execute {
            var pendingUri: Uri? = null
            try {
                val safeName = displayName
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .filterNot { it.isISOControl() }
                    .trim().trim('.').take(140).ifBlank { "download" }
                val mime = mimeType?.takeIf { it.contains('/') && it.length <= 120 } ?: "application/octet-stream"
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                        body.use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                    } ?: error("Android could not open the download file")
                    val complete = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    appContext.contentResolver.update(uri, complete, null, null)
                    Result(uri, safeName, bytes)
                } else {
                    val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!directory.exists() && !directory.mkdirs()) error("Downloads folder is unavailable")
                    val file = uniqueFile(directory, safeName)
                    val bytes = FileOutputStream(file).use { output ->
                        body.use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                    }
                    Result(FileProvider.getUriForFile(appContext, appContext.packageName + ".files", file), file.name, bytes)
                }
                onComplete(result, null)
            } catch (error: Throwable) {
                runCatching { pendingUri?.let { appContext.contentResolver.delete(it, null, null) } }
                runCatching { body.close() }
                onComplete(null, error)
            }
        }
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
        executor.shutdown()
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
