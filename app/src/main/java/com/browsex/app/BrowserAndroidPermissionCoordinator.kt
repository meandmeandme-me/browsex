package com.browsex.app

import org.mozilla.geckoview.GeckoSession

internal object BrowserAndroidPermissionPolicy {
    data class Decision(val rejected: Boolean, val missingPermissions: List<String>)

    fun evaluate(
        requested: List<String>,
        supported: Set<String>,
        granted: Set<String>,
        previouslyDenied: Set<String>
    ): Decision {
        val unique = requested.distinct()
        if (unique.isEmpty() || unique.any { it !in supported }) return Decision(true, emptyList())
        val missing = unique.filterNot { it in granted }
        if (missing.any { it in previouslyDenied }) return Decision(true, emptyList())
        return Decision(false, missing)
    }
}

/**
 * Owns the complete Android runtime-permission request lifecycle for Gecko sessions.
 * The Activity still owns the AndroidX launcher, but request validation, queueing,
 * stale-session checks, result delivery and teardown are centralized here.
 */
internal class BrowserAndroidPermissionCoordinator(
    private val activeSession: () -> GeckoSession?,
    private val containsSession: (GeckoSession) -> Boolean,
    private val isHostAvailable: () -> Boolean,
    private val anotherPromptInFlight: () -> Boolean,
    private val isGranted: (String) -> Boolean,
    private val wasDenied: (String) -> Boolean,
    private val clearDenial: (String) -> Unit,
    private val markDenied: (String) -> Unit,
    private val launchRequest: (Array<String>) -> Unit
) {
    private val queue = BrowserAndroidPermissionQueue()

    fun request(
        session: GeckoSession,
        requested: Array<String>,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        if (!isHostAvailable() || session !== activeSession()) {
            callback.reject()
            return
        }
        val supported = SUPPORTED_PERMISSIONS
        val decision = BrowserAndroidPermissionPolicy.evaluate(
            requested = requested.toList(),
            supported = supported,
            granted = supported.filterTo(mutableSetOf(), isGranted),
            previouslyDenied = supported.filterTo(mutableSetOf(), wasDenied)
        )
        if (decision.rejected) {
            callback.reject()
            return
        }
        if (decision.missingPermissions.isEmpty()) {
            requested.distinct().forEach(clearDenial)
            callback.grant()
            return
        }
        queue.enqueue(PendingPlatformPermission(session, decision.missingPermissions, callback))
        launchNext()
    }

    fun onResult(grants: Map<String, Boolean>) {
        queue.completeActive(
            grants = grants,
            activeSession = activeSession(),
            containsSession = containsSession,
            clearDenial = clearDenial,
            markDenied = markDenied
        )
        launchNext()
    }

    fun launchNext() {
        if (anotherPromptInFlight() || queue.hasActiveRequest || !isHostAvailable()) return
        val next = queue.takeNext(activeSession(), containsSession) ?: return
        launchRequest(next.permissions.toTypedArray())
    }

    fun cancelSession(session: GeckoSession) = queue.cancelSession(session)

    fun rejectAll() = queue.rejectAll()

    private companion object {
        val SUPPORTED_PERMISSIONS = setOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
    }
}
