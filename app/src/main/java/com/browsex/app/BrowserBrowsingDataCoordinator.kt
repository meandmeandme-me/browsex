package com.browsex.app

import org.mozilla.geckoview.StorageController

/**
 * Owns the multi-step browser-data clearing transaction.
 *
 * It snapshots regular tabs, closes Gecko sessions before storage deletion, restores
 * the selected tab afterwards, and keeps Activity dialogs/toasts behind callbacks.
 */
internal class BrowserBrowsingDataCoordinator(
    private val tabs: () -> List<BrowserTab>,
    private val activeTab: () -> BrowserTab?,
    private val sessionLifecycle: BrowserSessionLifecycleCoordinator,
    private val releaseViewSession: () -> Unit,
    private val replaceTabs: (List<BrowserTab>, Int) -> Unit,
    private val activateRestoredSelection: (Int) -> Unit,
    private val persistTabs: () -> Unit,
    private val storageController: () -> StorageController?,
    private val clearHistory: () -> Unit,
    private val clearSiteMediaDecisions: (String?) -> Unit,
    private val dispatchToMain: (() -> Unit) -> Unit,
    private val isHostUnavailable: () -> Boolean,
    private val onBusyChanged: (Boolean) -> Unit,
    private val onFinished: (Boolean) -> Unit
) {
    private var isClearing = false

    fun clear(flags: Long, clearHistory: Boolean, host: String?, mediaOrigin: String?) {
        if (isClearing) return
        if (flags == 0L) {
            if (clearHistory) this.clearHistory()
            onFinished(true)
            return
        }
        if (host != null && host.isBlank()) {
            onFinished(false)
            return
        }

        val regularTabs = tabs().filterNot { it.privateMode }
        val snapshot = TabSessionState.normalize(
            regularTabs.map { it.url },
            regularTabs.indexOfFirst { it === activeTab() }.takeIf { it >= 0 } ?: 0,
            HOME_URL
        )
        val tabsToRestore = snapshot.urls.mapIndexed { index, url ->
            regularTabs.getOrNull(index)?.also { it.url = url } ?: BrowserTab(url = url)
        }

        releaseViewSession()
        tabs().forEach(sessionLifecycle::resetSession)
        replaceTabs(tabsToRestore, snapshot.activeIndex)
        isClearing = true
        onBusyChanged(true)

        fun finish(success: Boolean) {
            dispatchToMain {
                if (!isClearing) return@dispatchToMain
                if (isHostUnavailable()) {
                    isClearing = false
                    return@dispatchToMain
                }
                isClearing = false
                onBusyChanged(false)
                if (success) {
                    if (clearHistory) this.clearHistory()
                    when {
                        mediaOrigin != null -> clearSiteMediaDecisions(mediaOrigin)
                        (flags and StorageController.ClearFlags.PERMISSIONS) != 0L ||
                            (flags and StorageController.ClearFlags.SITE_DATA) != 0L ->
                            clearSiteMediaDecisions(null)
                    }
                }
                activateRestoredSelection(snapshot.activeIndex)
                persistTabs()
                onFinished(success)
            }
        }

        val storage = storageController()
        if (storage == null) {
            finish(false)
            return
        }
        try {
            val operation = if (host.isNullOrBlank()) storage.clearData(flags)
                else storage.clearDataFromHost(host, flags)
            operation.accept(
                { finish(true) },
                { error ->
                    android.util.Log.e("BrowseX", "Browsing data clearing failed", error)
                    finish(false)
                }
            )
        } catch (error: RuntimeException) {
            android.util.Log.e("BrowseX", "Could not start browsing data clearing", error)
            finish(false)
        }
    }
}
