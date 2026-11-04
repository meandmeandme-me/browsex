package com.browsex.app

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

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
    private val runtime: () -> GeckoRuntime,
    private val cancelPlatformPermissions: (GeckoSession) -> Unit,
    private val attachDelegates: (BrowserTab) -> Unit,
    private val activateTab: (BrowserTab) -> Unit,
    private val updateUi: () -> Unit,
    private val showMessage: (String) -> Unit
) {
    fun onContentProcessCrash(tab: BrowserTab, session: GeckoSession) {
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
