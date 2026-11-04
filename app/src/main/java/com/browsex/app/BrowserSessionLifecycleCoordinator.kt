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
        closeCurrentSession(tab)
        tab.session = createSession(tab.privateMode)
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

    /** Closes a tab session permanently before the host removes its tab record. */
    fun closeAndDiscard(tab: BrowserTab) {
        closeCurrentSession(tab)
    }

    /** Closes and replaces a tab's session, preserving its URL and private-mode setting. */
    fun resetSession(tab: BrowserTab) {
        closeCurrentSession(tab)
        tab.session = createSession(tab.privateMode)
    }

    /** Releases all opened sessions and clears per-session delegates before host teardown. */
    fun closeAllSessions() {
        registry.tabs.forEach(::closeCurrentSession)
    }

    /**
     * Runs host-owned permission cancellation before closing GeckoSession.
     * Clears session-scoped state even if Gecko's close operation throws.
     */
    private fun closeCurrentSession(tab: BrowserTab) {
        try {
            runCatching { beforeSessionClose(tab) }
                .onFailure { android.util.Log.w("BrowseX", "Could not cancel pending tab permissions", it) }
            if (tab.sessionOpened) {
                runCatching {
                    tab.session.setFocused(false)
                    tab.session.setActive(false)
                    tab.session.close()
                }.onFailure { android.util.Log.w("BrowseX", "Could not close GeckoSession cleanly", it) }
            }
        } finally {
            // Cleanup remains deterministic even if a host callback or Gecko close fails.
            tab.sessionOpened = false
            tab.permissionDelegate = null
            tab.sitePermissions = emptyList()
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
