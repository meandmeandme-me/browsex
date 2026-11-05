package com.browsex.app

import org.mozilla.geckoview.WebResponse
import java.net.URI

/**
 * Owns validation, legacy-storage permission queuing and teardown for Gecko response downloads.
 *
 * The Activity supplies the launcher and platform checks; the coordinator owns the response
 * streams so a permission prompt cannot leak or silently lose a one-shot response body.
 */
internal class BrowserDownloadRequestCoordinator(
    private val apiLevel: Int,
    private val hasLegacyStoragePermission: () -> Boolean,
    private val canRequestLegacyStoragePermission: () -> Boolean,
    private val requestLegacyStoragePermission: () -> Unit,
    private val startDownload: (WebResponse) -> Unit,
    private val isHostAvailable: () -> Boolean,
    private val showMessage: (String) -> Unit,
    private val maxQueuedResponses: Int = 5
) {
    private val waiting = ArrayDeque<WebResponse>()
    private var permissionInFlight = false

    init {
        require(maxQueuedResponses > 0)
    }

    fun enqueue(response: WebResponse) {
        if (!isHostAvailable()) {
            closeResponseBody(response)
            return
        }
        if (!isSupported(response)) {
            closeResponseBody(response)
            showIfAvailable("This download response is not supported")
            return
        }
        if (apiLevel <= 28 && !hasLegacyStoragePermission()) {
            if (!canRequestLegacyStoragePermission() || !isHostAvailable()) {
                closeResponseBody(response)
                showIfAvailable("Storage permission is unavailable")
                return
            }
            if (waiting.size >= maxQueuedResponses) {
                closeResponseBody(response)
                showIfAvailable("Too many downloads are waiting for storage permission")
                return
            }
            waiting.addLast(response)
            launchPermissionIfNeeded()
            return
        }
        startSafely(response)
    }

    fun onLegacyPermissionResult(granted: Boolean) {
        permissionInFlight = false
        val pending = waiting.toList()
        waiting.clear()
        if (!granted || !isHostAvailable() || !hasLegacyStoragePermission()) {
            pending.forEach(::closeResponseBody)
            showIfAvailable("Storage permission was denied; download cancelled")
            return
        }
        pending.forEach { response ->
            if (isSupported(response)) startSafely(response)
            else closeResponseBody(response)
        }
    }

    /** Called by the host before its Activity and Gecko runtime are torn down. */
    fun cancelPending() {
        permissionInFlight = false
        waiting.toList().forEach { it.body?.close() }
        waiting.clear()
    }

    private fun launchPermissionIfNeeded() {
        if (permissionInFlight || waiting.isEmpty()) return
        permissionInFlight = true
        try {
            requestLegacyStoragePermission()
        } catch (error: RuntimeException) {
            permissionInFlight = false
            cancelPending()
            showIfAvailable("Storage permission could not be requested")
            android.util.Log.w("BrowseX", "Could not launch legacy download permission", error)
        }
    }

    /**
     * A host failure must close the one-shot Gecko response body. Permission-result batches
     * continue processing later items even if one individual download cannot be started.
     */
    private fun startSafely(response: WebResponse) {
        try {
            startDownload(response)
        } catch (error: RuntimeException) {
            closeResponseBody(response)
            android.util.Log.e("BrowseX", "Could not start response download", error)
            showIfAvailable("Could not start this download")
        }
    }

    private fun closeResponseBody(response: WebResponse) {
        val closed = closeDownloadBodySafely { response.body?.close() }
        if (!closed) android.util.Log.w("BrowseX", "Could not close a rejected Gecko download response body")
    }

    private fun isSupported(response: WebResponse): Boolean =
        BrowserDownloadResponsePolicy.isSupported(
            rawUrl = response.uri,
            statusCode = response.statusCode,
            hasBody = response.body != null
        )

    private fun showIfAvailable(message: String) {
        if (isHostAvailable()) showMessage(message)
    }
}



/** Closes one-shot Gecko response bodies without allowing cleanup failures to abort a queue. */
internal fun closeDownloadBodySafely(closeBody: () -> Unit): Boolean =
    try {
        closeBody()
        true
    } catch (_: Exception) {
        false
    }

/** Pure validation policy for external Gecko download responses. */
internal object BrowserDownloadResponsePolicy {
    fun isSupported(rawUrl: String, statusCode: Int, hasBody: Boolean): Boolean {
        if (!hasBody || statusCode !in 200..299) return false
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        return (scheme == "https" || scheme == "http") &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }
}
