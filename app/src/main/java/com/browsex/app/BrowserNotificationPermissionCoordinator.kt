package com.browsex.app

/** Owns the POST_NOTIFICATIONS request and coalesces concurrent Gecko notification requests. */
internal class BrowserNotificationPermissionCoordinator(
    private val apiLevel: Int,
    private val featureEnabled: () -> Boolean,
    private val permissionGranted: () -> Boolean,
    private val notificationsEnabled: () -> Boolean,
    private val previouslyDenied: () -> Boolean,
    private val clearDenial: () -> Unit,
    private val markDenied: () -> Unit,
    private val hostAvailable: () -> Boolean,
    private val launchRequest: () -> Unit,
    private val afterResolved: () -> Unit
) {
    private val callbacks = mutableListOf<(Boolean) -> Unit>()
    var isInFlight: Boolean = false
        private set

    fun request(callback: (Boolean) -> Unit) {
        if (!featureEnabled()) {
            callback(false)
            return
        }
        if (apiLevel < 33) {
            callback(notificationsEnabled())
            return
        }
        if (permissionGranted()) {
            clearDenial()
            callback(notificationsEnabled())
            return
        }
        if (previouslyDenied() || !hostAvailable()) {
            callback(false)
            return
        }
        callbacks.add(callback)
        if (!isInFlight) {
            isInFlight = true
            try {
                launchRequest()
            } catch (_: Exception) {
                // Android's Log facade is unavailable in plain JVM tests. More importantly,
                // a launcher failure must resolve every waiting Gecko callback exactly once.
                isInFlight = false
                resolveCallbacks(false)
                afterResolved()
            }
        }
    }

    fun onResult(granted: Boolean) {
        if (!isInFlight) return
        isInFlight = false
        if (granted) clearDenial() else markDenied()
        resolveCallbacks(granted && notificationsEnabled())
        afterResolved()
    }

    fun rejectAll() {
        isInFlight = false
        resolveCallbacks(false)
    }

    private fun resolveCallbacks(result: Boolean) {
        val pending = callbacks.toList()
        callbacks.clear()
        pending.forEach { callback -> runCatching { callback(result) } }
    }
}


/** Website notifications are delivered only after an explicit, persisted user grant. */
internal object BrowserSiteNotificationPolicy {
    fun isAllowed(savedDecision: String?): Boolean = savedDecision == "allow"
}
