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
    private val maxPendingCallbacks = 64
    var isInFlight: Boolean = false
        private set

    fun request(callback: (Boolean) -> Unit) {
        if (!featureEnabled()) {
            deliver(callback, false)
            return
        }
        if (apiLevel < 33) {
            deliver(callback, notificationsEnabled())
            return
        }
        if (permissionGranted()) {
            runCatching { clearDenial() }
            deliver(callback, notificationsEnabled())
            return
        }
        if (previouslyDenied() || !hostAvailable()) {
            deliver(callback, false)
            return
        }
        if (callbacks.size >= maxPendingCallbacks) {
            deliver(callback, false)
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
                runCatching { afterResolved() }
            }
        }
    }

    fun onResult(granted: Boolean) {
        if (!isInFlight) return
        isInFlight = false
        // Persistence failures must never strand Gecko's waiting permission callbacks.
        // The runtime result remains authoritative for this request; a later request can
        // retry the preference update without leaving the coordinator stuck in-flight.
        if (granted) runCatching { clearDenial() } else runCatching { markDenied() }
        val allowed = granted && runCatching { permissionGranted() && notificationsEnabled() }.getOrDefault(false)
        resolveCallbacks(allowed)
        runCatching { afterResolved() }
    }

    fun rejectAll() {
        isInFlight = false
        resolveCallbacks(false)
    }

    private fun deliver(callback: (Boolean) -> Unit, result: Boolean) {
        runCatching { callback(result) }
    }

    private fun resolveCallbacks(result: Boolean) {
        val pending = callbacks.toList()
        callbacks.clear()
        pending.forEach { callback -> deliver(callback, result) }
    }
}


/** Website notifications are delivered only after an explicit, persisted user grant. */
internal object BrowserSiteNotificationPolicy {
    fun isAllowed(savedDecision: String?): Boolean = savedDecision == "allow"
}
