package com.browsex.app

import org.mozilla.geckoview.GeckoSession

/**
 * Serializes Android runtime-permission prompts requested by Gecko sessions.
 *
 * Activity Result launchers remain in MainActivity. This coordinator owns queue
 * ordering and exactly-once completion when tabs change or the Activity is torn down.
 * All methods are called on the main thread.
 */
internal class BrowserAndroidPermissionQueue {
    private val queued = java.util.ArrayDeque<PendingPlatformPermission>()
    private var active: PendingPlatformPermission? = null

    val hasActiveRequest: Boolean
        get() = active != null

    fun enqueue(request: PendingPlatformPermission) {
        queued.addLast(request)
    }

    /** Selects the next request belonging to the currently selected, still-live tab. */
    fun takeNext(
        activeSession: GeckoSession?,
        containsSession: (GeckoSession) -> Boolean
    ): PendingPlatformPermission? {
        if (active != null) return null
        while (queued.isNotEmpty()) {
            val next = queued.removeFirst()
            if (next.completed || next.session !== activeSession || !containsSession(next.session)) {
                if (!next.completed) {
                    next.completed = true
                    next.callback.reject()
                }
                continue
            }
            active = next
            return next
        }
        return null
    }

    /** Completes the system prompt and records the user's Android permission decision. */
    fun completeActive(
        grants: Map<String, Boolean>,
        activeSession: GeckoSession?,
        containsSession: (GeckoSession) -> Boolean,
        clearDenial: (String) -> Unit,
        markDenied: (String) -> Unit
    ) {
        val request = active
        active = null
        if (request == null || request.completed) return

        val granted = request.permissions.all { grants[it] == true } &&
            request.session === activeSession && containsSession(request.session)
        request.permissions.forEach { permission ->
            if (grants[permission] == true) clearDenial(permission)
            else markDenied(permission)
        }
        request.completed = true
        if (granted) request.callback.grant() else request.callback.reject()
    }

    /** Rejects queued requests and marks an active prompt terminal without reordering it. */
    fun cancelSession(session: GeckoSession) {
        val iterator = queued.iterator()
        while (iterator.hasNext()) {
            val request = iterator.next()
            if (request.session === session) {
                request.completed = true
                request.callback.reject()
                iterator.remove()
            }
        }
        active?.takeIf { it.session === session && !it.completed }?.let { request ->
            request.completed = true
            request.callback.reject()
        }
    }

    /** Rejects only the request whose Android launcher failed, preserving later queued requests. */
    fun rejectActive() {
        val request = active
        active = null
        if (request != null && !request.completed) {
            request.completed = true
            request.callback.reject()
        }
    }

    /** Rejects all outstanding Gecko callbacks during Activity teardown. */
    fun rejectAll() {
        queued.toList().forEach { request ->
            if (!request.completed) {
                request.completed = true
                request.callback.reject()
            }
        }
        queued.clear()
        active?.let { request ->
            if (!request.completed) {
                request.completed = true
                request.callback.reject()
            }
        }
        active = null
    }
}
