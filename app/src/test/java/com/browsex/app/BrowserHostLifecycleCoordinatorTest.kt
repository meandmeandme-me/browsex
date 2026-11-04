package com.browsex.app

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserHostLifecycleCoordinatorTest {
    private data class FakeTab(val id: String, val opened: Boolean = true)

    @Test
    fun startActivatesOnlyTheSelectedOpenedSession() {
        val first = FakeTab("first")
        val second = FakeTab("second", opened = false)
        val active = mutableListOf<Pair<String, Boolean>>()
        val coordinator = coordinator(listOf(first, second), first, active)

        coordinator.onStart()

        assertEquals(listOf("first" to true), active)
    }

    @Test
    fun stopPersistsAndDeactivatesEveryOpenedSession() {
        val first = FakeTab("first")
        val second = FakeTab("second")
        val calls = mutableListOf<String>()
        val coordinator = coordinator(listOf(first, second), first, mutableListOf(), calls)

        coordinator.onStop()

        assertEquals(listOf("persist", "first:false", "second:false"), calls)
    }

    @Test
    fun runningLowPersistsAndBackgroundAlsoReleasesInactiveSessions() {
        val first = FakeTab("first")
        val second = FakeTab("second")
        val active = mutableListOf<Pair<String, Boolean>>()
        val calls = mutableListOf<String>()
        val coordinator = coordinator(listOf(first, second), first, active, calls)

        coordinator.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(listOf("persist", "second:false"), calls)

        calls.clear()
        coordinator.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        assertEquals(listOf("persist", "second:false", "release"), calls)
    }

    @Test
    fun lowMemoryPersistsAndReleasesInactiveSessions() {
        val first = FakeTab("first")
        val second = FakeTab("second")
        val calls = mutableListOf<String>()
        val coordinator = coordinator(listOf(first, second), first, mutableListOf(), calls)

        coordinator.onLowMemory()

        assertEquals(listOf("persist", "release"), calls)
    }

    private fun coordinator(
        tabs: List<FakeTab>,
        selected: FakeTab,
        active: MutableList<Pair<String, Boolean>>,
        calls: MutableList<String> = mutableListOf()
    ) = BrowserHostLifecycleCoordinator(
        tabs = { tabs },
        activeTab = { selected },
        isSessionOpened = { it.opened },
        persistTabs = { calls.add("persist") },
        setSessionActive = { tab, value ->
            active.add(tab.id to value)
            calls.add("${tab.id}:$value")
        },
        releaseForMemoryPressure = { calls.add("release") }
    )
}
