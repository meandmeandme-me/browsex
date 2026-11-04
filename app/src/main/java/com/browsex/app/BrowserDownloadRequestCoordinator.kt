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
        if (!isSupported(response)) {
            response.body?.close()
            showIfAvailable("This download response is not supported")
            return
        }
        if (apiLevel <= 28 && !hasLegacyStoragePermission()) {
            if (!canRequestLegacyStoragePermission() || !isHostAvailable()) {
                response.body?.close()
                showIfAvailable("Storage permission is unavailable")
                return
            }
            if (waiting.size >= maxQueuedResponses) {
                response.body?.close()
                showIfAvailable("Too many downloads are waiting for storage permission")
                return
            }
            waiting.addLast(response)
            launchPermissionIfNeeded()
            return
        }
        startDownload(response)
    }

    fun onLegacyPermissionResult(granted: Boolean) {
        permissionInFlight = false
        val pending = waiting.toList()
        waiting.clear()
        if (!granted || !isHostAvailable() || !hasLegacyStoragePermission()) {
            pending.forEach { it.body?.close() }
            showIfAvailable("Storage permission was denied; download cancelled")
            return
        }
        pending.forEach { response ->
            if (isSupported(response)) startDownload(response)
            else response.body?.close()
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
