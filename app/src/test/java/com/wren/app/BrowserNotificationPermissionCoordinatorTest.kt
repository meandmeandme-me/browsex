package com.wren.app

import org.junit.Assert.*
import org.junit.Test

class BrowserNotificationPermissionCoordinatorTest {
    @Test fun websiteNotificationsRequireAnExplicitPersistedGrant() {
        assertTrue(BrowserSiteNotificationPolicy.isAllowed("allow"))
        assertFalse(BrowserSiteNotificationPolicy.isAllowed("deny"))
        assertFalse(BrowserSiteNotificationPolicy.isAllowed("ask"))
        assertFalse(BrowserSiteNotificationPolicy.isAllowed(null))
        assertFalse(BrowserSiteNotificationPolicy.isAllowed(""))
    }

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

    @Test fun lateAndroidResultAfterTeardownDoesNotResolveCallbacksTwice() {
        val results = mutableListOf<Boolean>()
        val coordinator = coordinator()
        coordinator.request { value -> results.add(value) }
        coordinator.rejectAll()
        coordinator.onResult(true)
        assertEquals(listOf(false), results)
        assertFalse(coordinator.isInFlight)
    }

    @Test fun pendingNotificationPermissionCallbacksAreBounded() {
        var launches = 0
        var rejected = 0
        var grantedCount = 0
        val coordinator = coordinator(launch = { launches++ })
        repeat(65) {
            coordinator.request { granted ->
                if (granted) grantedCount++ else rejected++
            }
        }
        assertEquals(1, launches)
        assertEquals(1, rejected)
        coordinator.onResult(true)
        assertEquals(1, rejected)
        assertEquals(64, grantedCount)
    }

    @Test fun systemNotificationToggleCanRejectGrantedRuntimePermission() {
        val results = mutableListOf<Boolean>()
        val coordinator = coordinator(systemEnabled = { false })
        coordinator.request { granted -> results.add(granted) }
        coordinator.onResult(true)
        assertEquals(listOf(false), results)
    }

    @Test fun callbackFailureDoesNotPreventOtherCallbacksFromResolving() {
        val results = mutableListOf<Boolean>()
        val coordinator = coordinator()
        coordinator.request { throw IllegalStateException("consumer callback failed") }
        coordinator.request { granted -> results.add(granted) }
        coordinator.onResult(true)
        assertEquals(listOf(true), results)
        assertFalse(coordinator.isInFlight)
    }

    @Test fun unavailableHostRejectsWithoutLaunching() {
        var launches = 0
        val results = mutableListOf<Boolean>()
        val coordinator = BrowserNotificationPermissionCoordinator(
            apiLevel = 35,
            featureEnabled = { true },
            permissionGranted = { false },
            notificationsEnabled = { true },
            previouslyDenied = { false },
            clearDenial = {},
            markDenied = {},
            hostAvailable = { false },
            launchRequest = { launches++ },
            afterResolved = {}
        )
        coordinator.request { value -> results.add(value) }
        assertEquals(listOf(false), results)
        assertEquals(0, launches)
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
