package com.browsex.app

import org.junit.Assert.*
import org.junit.Test

class BrowserNotificationPermissionCoordinatorTest {
    @Test fun coalescesConcurrentRequestsAndResolvesEveryCallback() {
        var launched = 0
        var cleared = 0
        val results = mutableListOf<Boolean>()
        val coordinator = coordinator(launch = { launched++ }, clear = { cleared++ })
        coordinator.request({ value -> results.add(value) })
        coordinator.request({ value -> results.add(value) })
        assertEquals(1, launched)
        assertTrue(coordinator.isInFlight)
        coordinator.onResult(true)
        assertEquals(listOf(true, true), results)
        assertEquals(1, cleared)
        assertFalse(coordinator.isInFlight)
    }

    @Test fun deniedRequestIsRememberedAndRejectedOnNextAttempt() {
        var denied = false
        var launches = 0
        val results = mutableListOf<Boolean>()
        val coordinator = coordinator(previouslyDenied = { denied }, mark = { denied = true }, launch = { launches++ })
        coordinator.request({ value -> results.add(value) })
        coordinator.onResult(false)
        coordinator.request({ value -> results.add(value) })
        assertEquals(listOf(false, false), results)
        assertEquals(1, launches)
    }

    @Test fun olderAndroidChecksSystemNotificationStateWithoutLaunching() {
        var launches = 0
        val results = mutableListOf<Boolean>()
        coordinator(api = 32, systemEnabled = { false }, launch = { launches++ }).request({ value -> results.add(value) })
        assertEquals(listOf(false), results)
        assertEquals(0, launches)
    }

    @Test fun disabledFeatureRejectsWithoutLaunching() {
        var launches = 0
        val results = mutableListOf<Boolean>()
        coordinator(enabled = { false }, launch = { launches++ }).request({ value -> results.add(value) })
        assertEquals(listOf(false), results)
        assertEquals(0, launches)
    }

    @Test fun teardownRejectsAllPendingCallbacks() {
        val results = mutableListOf<Boolean>()
        val coordinator = coordinator()
        coordinator.request({ value -> results.add(value) })
        coordinator.rejectAll()
        assertEquals(listOf(false), results)
        assertFalse(coordinator.isInFlight)
    }

    @Test fun launcherFailureRejectsPendingCallbacksAndAllowsFutureRequests() {
        var attempts = 0
        var fail = true
        val results = mutableListOf<Boolean>()
        val coordinator = coordinator(launch = {
            attempts++
            if (fail) throw IllegalStateException("launcher unavailable")
        })
        coordinator.request { value -> results.add(value) }
        assertEquals(listOf(false), results)
        assertFalse(coordinator.isInFlight)

        fail = false
        coordinator.request { value -> results.add(value) }
        assertTrue(coordinator.isInFlight)
        coordinator.onResult(true)
        assertEquals(listOf(false, true), results)
        assertEquals(2, attempts)
    }

    private fun coordinator(
        api: Int = 35,
        enabled: () -> Boolean = { true },
        granted: () -> Boolean = { false },
        systemEnabled: () -> Boolean = { true },
        previouslyDenied: () -> Boolean = { false },
        clear: () -> Unit = {},
        mark: () -> Unit = {},
        launch: () -> Unit = {}
    ) = BrowserNotificationPermissionCoordinator(
        apiLevel = api,
        featureEnabled = enabled,
        permissionGranted = granted,
        notificationsEnabled = systemEnabled,
        previouslyDenied = previouslyDenied,
        clearDenial = clear,
        markDenied = mark,
        hostAvailable = { true },
        launchRequest = launch,
        afterResolved = {}
    )
}
