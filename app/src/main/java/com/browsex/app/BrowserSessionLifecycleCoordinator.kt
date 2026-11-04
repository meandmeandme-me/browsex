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


/**
 * Owns the bounded recovery attempt for a crashed Gecko content process.
 *
 * Android UI dispatch, runtime ownership, permission queues and view activation
 * remain host responsibilities and are supplied as callbacks.
 */
internal class BrowserContentProcessRecoveryCoordinator(
    private val dispatchToMain: (() -> Unit) -> Unit,
    private val isHostUnavailable: () -> Boolean,
    private val containsTab: (BrowserTab) -> Boolean,
    private val isActiveTab: (BrowserTab) -> Boolean,
    private val homeUrl: () -> String,
    private val runtime: () -> org.mozilla.geckoview.GeckoRuntime,
    private val cancelPlatformPermissions: (org.mozilla.geckoview.GeckoSession) -> Unit,
    private val attachDelegates: (BrowserTab) -> Unit,
    private val activateTab: (BrowserTab) -> Unit,
    private val updateUi: () -> Unit,
    private val showMessage: (String) -> Unit
) {
    fun onContentProcessCrash(tab: BrowserTab, session: org.mozilla.geckoview.GeckoSession) {
        dispatchToMain {
            if (isHostUnavailable() || !containsTab(tab) || tab.session !== session) return@dispatchToMain

            if (tab.recoveryAttempts >= MAX_AUTOMATIC_RECOVERY_ATTEMPTS) {
                tab.crashed = true
                tab.progress = 0
                if (isActiveTab(tab)) showMessage("This tab crashed again. Reopen it to continue.")
                updateUi()
                return@dispatchToMain
            }

            tab.recoveryAttempts += 1
            val restoreUrl = tab.url.ifBlank { homeUrl() }
            val wasActive = isActiveTab(tab)
            tab.permissionDelegate?.cancelPending()
            cancelPlatformPermissions(session)
            attachDelegates(tab)

            try {
                session.open(runtime())
                tab.sessionOpened = true
                if (wasActive) {
                    activateTab(tab)
                    session.loadUri(restoreUrl)
                } else {
                    session.setFocused(false)
                    session.setActive(false)
                    session.loadUri(restoreUrl)
                }
            } catch (error: RuntimeException) {
                tab.sessionOpened = false
                tab.crashed = true
                android.util.Log.e("BrowseX", "Tab recovery failed", error)
                if (wasActive) showMessage("Could not restore this tab. Open a new tab.")
            }
        }
    }

    private companion object {
        const val MAX_AUTOMATIC_RECOVERY_ATTEMPTS = 1
    }
}
