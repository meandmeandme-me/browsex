package com.wren.app

import android.content.ComponentCallbacks2

/**
 * Coordinates Activity visibility and Android memory-pressure events with browser sessions.
 *
 * The host injects tab/session operations so this policy stays independent of Activity UI,
 * GeckoView widgets and persistent storage implementations.
 */
internal class BrowserHostLifecycleCoordinator<T : Any>(
    private val tabs: () -> List<T>,
    private val activeTab: () -> T?,
    private val isSessionOpened: (T) -> Boolean,
    private val persistTabs: () -> Unit,
    private val setSessionActive: (T, Boolean) -> Unit,
    private val releaseForMemoryPressure: () -> Unit
) {
    fun onStart() {
        activeTab()?.takeIf(isSessionOpened)?.let { setSessionActive(it, true) }
    }

    fun onStop() {
        persistTabs()
        tabs().filter(isSessionOpened).forEach { setSessionActive(it, false) }
    }

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            persistTabs()
            tabs().filter { it !== activeTab() && isSessionOpened(it) }
                .forEach { setSessionActive(it, false) }
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            releaseForMemoryPressure()
        }
    }

    fun onLowMemory() {
        persistTabs()
        releaseForMemoryPressure()
    }
}
