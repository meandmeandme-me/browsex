package com.browsex.app

/**
 * Owns GeckoSession open/close/release lifecycle for the tab registry.
 *
 * UI selection and GeckoView attachment remain with the Activity. Cleanup of
 * permission requests is injected because those queues belong to the host.
 * All calls are expected on the Android main thread.
 */
internal class BrowserSessionLifecycleCoordinator(
    private val registry: BrowserTabRegistry<BrowserTab>,
    private val beforeSessionClose: (BrowserTab) -> Unit,
    private val createSession: (privateMode: Boolean) -> org.mozilla.geckoview.GeckoSession,
    private val maxLiveSessions: Int = 3
) {
    fun releaseInactive(tab: BrowserTab) {
        if (tab === registry.activeTab || !tab.sessionOpened) return
        beforeSessionClose(tab)
        runCatching {
            tab.session.setFocused(false)
            tab.session.setActive(false)
            tab.session.close()
        }
        tab.session = createSession(tab.privateMode)
        tab.sessionOpened = false
        tab.permissionDelegate = null
        tab.sitePermissions = emptyList()
    }

    fun enforceLiveSessionBudget() {
        releaseByIndexes(
            TabLifecyclePolicy.sessionsToReleaseForBudget(
                candidates = lifecycleCandidates(),
                maxLiveSessions = maxLiveSessions
            )
        )
    }

    fun releaseForMemoryPressure() {
        releaseByIndexes(
            TabLifecyclePolicy.sessionsToReleaseForMemoryPressure(lifecycleCandidates())
        )
    }

    /** Closes and replaces a tab's session, preserving its URL and private-mode setting. */
    fun resetSession(tab: BrowserTab) {
        beforeSessionClose(tab)
        if (tab.sessionOpened) {
            runCatching {
                tab.session.setFocused(false)
                tab.session.setActive(false)
                tab.session.close()
            }
        }
        tab.session = createSession(tab.privateMode)
        tab.sessionOpened = false
        tab.permissionDelegate = null
        tab.sitePermissions = emptyList()
    }

    /** Releases all opened sessions without removing tab records from the registry. */
    fun closeAllSessions() {
        registry.tabs.forEach { tab ->
            beforeSessionClose(tab)
            if (tab.sessionOpened) {
                runCatching {
                    tab.session.setFocused(false)
                    tab.session.setActive(false)
                    tab.session.close()
                }
            }
            tab.sessionOpened = false
        }
    }

    private fun lifecycleCandidates(): List<TabLifecyclePolicy.Candidate> =
        registry.tabs.mapIndexed { index, tab ->
            TabLifecyclePolicy.Candidate(
                index = index,
                active = tab === registry.activeTab,
                opened = tab.sessionOpened,
                lastActiveAt = tab.lastActiveAt
            )
        }

    private fun releaseByIndexes(indexes: List<Int>) {
        // Resolve indexes against one snapshot so removal/reordering cannot skew the policy.
        val snapshot = registry.tabs
        indexes.forEach { index -> snapshot.getOrNull(index)?.let(::releaseInactive) }
    }
}
