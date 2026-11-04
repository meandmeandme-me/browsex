package com.browsex.app

import org.junit.Assert.*
import org.junit.Test

class BrowserNotificationPermissionCoordinatorTest {
    @Test fun coalescesConcurrentRequestsAndResolvesEveryCallback() {
        var launched = 0
        var cleared = 0
        val results = mutableListOf<Boolean>()
        val coordinator = coordinator(launch = { launched++ }, clear = { cleared++ })
        coordinator.request(results::add)
        coordinator.request(results::add)
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
        coordinator.request(results::add)
        coordinator.onResult(false)
        coordinator.request(results::add)
        assertEquals(listOf(false, false), results)
        assertEquals(1, launches)
    }

    @Test fun olderAndroidChecksSystemNotificationStateWithoutLaunching() {
        var launches = 0
        val results = mutableListOf<Boolean>()
        coordinator(api = 32, systemEnabled = { false }, launch = { launches++ }).request(results::add)
        assertEquals(listOf(false), results)
        assertEquals(0, launches)
    }

    @Test fun disabledFeatureRejectsWithoutLaunching() {
        var launches = 0
        val results = mutableListOf<Boolean>()
        coordinator(enabled = { false }, launch = { launches++ }).request(results::add)
        assertEquals(listOf(false), results)
        assertEquals(0, launches)
    }

    @Test fun teardownRejectsAllPendingCallbacks() {
        val results = mutableListOf<Boolean>()
        val coordinator = coordinator()
        coordinator.request(results::add)
        coordinator.rejectAll()
        assertEquals(listOf(false), results)
        assertFalse(coordinator.isInFlight)
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
