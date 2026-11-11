package com.browsex.app

import org.mozilla.geckoview.StorageController
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the multi-step browser-data clearing transaction.
 *
 * It snapshots regular tabs, closes Gecko sessions before storage deletion, restores
 * the selected tab afterwards, and waits for app-owned history/permission cleanup before
 * reporting success. Private tabs are discarded and never restored.
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
    private val clearHistory: ((Boolean) -> Unit) -> Unit,
    private val clearSiteMediaDecisions: (String?, (Boolean) -> Unit) -> Unit,
    private val dispatchToMain: (() -> Unit) -> Unit,
    private val isHostUnavailable: () -> Boolean,
    private val onBusyChanged: (Boolean) -> Unit,
    private val onFinished: (Boolean) -> Unit
) {
    private var isClearing = false

    fun clear(flags: Long, clearHistory: Boolean, host: String?, mediaOrigin: String?) {
        if (isClearing) return
        if (flags == 0L) {
            if (!clearHistory) {
                onFinished(true)
                return
            }
            val completed = AtomicBoolean(false)
            runCatching {
                this.clearHistory { success ->
                    if (completed.compareAndSet(false, true)) {
                        dispatchToMain {
                            if (!isHostUnavailable()) onFinished(success)
                        }
                    }
                }
            }.onFailure {
                if (completed.compareAndSet(false, true) && !isHostUnavailable()) onFinished(false)
            }
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

        runCatching { releaseViewSession() }
            .onFailure { android.util.Log.w("BrowseX", "Could not detach GeckoView before clearing data", it) }
        tabs().forEach { tab ->
            if (tab.privateMode) sessionLifecycle.closeAndDiscard(tab)
            else sessionLifecycle.resetSession(tab)
        }
        replaceTabs(tabsToRestore, snapshot.activeIndex)
        isClearing = true
        onBusyChanged(true)

        fun finalize(completed: Boolean) {
            if (isHostUnavailable()) {
                isClearing = false
                return
            }
            isClearing = false
            onBusyChanged(false)
            var result = completed
            runCatching { activateRestoredSelection(snapshot.activeIndex) }
                .onFailure {
                    android.util.Log.e("BrowseX", "Could not activate restored tab", it)
                    result = false
                }
            runCatching { persistTabs() }
                .onFailure {
                    android.util.Log.e("BrowseX", "Could not persist restored tabs", it)
                    result = false
                }
            onFinished(result)
        }

        fun runAppOwnedCleanup(initialSuccess: Boolean = true) {
            val shouldClearPermissions = mediaOrigin != null ||
                (flags and StorageController.ClearFlags.PERMISSIONS) != 0L ||
                (flags and StorageController.ClearFlags.SITE_DATA) != 0L
            if (!shouldClearPermissions) {
                finalize(initialSuccess)
                return
            }
            val completed = AtomicBoolean(false)
            runCatching {
                clearSiteMediaDecisions(mediaOrigin) { success ->
                    if (completed.compareAndSet(false, true)) {
                        dispatchToMain { finalize(initialSuccess && success) }
                    }
                }
            }.onFailure {
                android.util.Log.e("BrowseX", "Could not clear site permission decisions", it)
                if (completed.compareAndSet(false, true)) dispatchToMain { finalize(false) }
            }
        }

        fun finish(success: Boolean) {
            dispatchToMain {
                if (!isClearing || isHostUnavailable()) {
                    if (isHostUnavailable()) isClearing = false
                    return@dispatchToMain
                }
                if (!success) {
                    finalize(false)
                    return@dispatchToMain
                }
                if (!clearHistory) {
                    runAppOwnedCleanup()
                    return@dispatchToMain
                }
                val completed = AtomicBoolean(false)
                runCatching {
                    this.clearHistory { historyCleared ->
                        if (completed.compareAndSet(false, true)) {
                            dispatchToMain {
                                runAppOwnedCleanup(historyCleared)
                            }
                        }
                    }
                }.onFailure {
                    android.util.Log.e("BrowseX", "Could not clear browsing history", it)
                    if (completed.compareAndSet(false, true)) dispatchToMain { finalize(false) }
                }
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

    private companion object {
        const val HOME_URL = "resource://android/assets/home.html"
    }
}
