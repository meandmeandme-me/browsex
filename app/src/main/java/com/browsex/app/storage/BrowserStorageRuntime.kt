package com.browsex.app.storage

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Application-scoped entry point for Room storage.
 *
 * BrowseX starts with a clean Room database. There is no legacy user-data import:
 * the product is being rebuilt before any user data exists. Room's own schema
 * migrations remain enabled for future app upgrades.
 */
internal class BrowserStorageRuntime(context: Context) {
    private val appContext = context.applicationContext
    private val graph = BrowserStorageGraph(appContext)
    val settings = BrowserSettingsDataStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val ready = CompletableDeferred<Result<BrowserStorageGraph>>()
    private val tabSnapshotMutex = Mutex()
    private val tabSnapshotGeneration = AtomicLong(0L)

    suspend fun awaitReady(): BrowserStorageGraph {
        if (started.compareAndSet(false, true)) {
            scope.launch {
                val result = runCatching {
                    // Any work interrupted before process death is made terminal on startup.
                    graph.downloads.recoverInterrupted()
                    graph
                }
                ready.complete(result)
            }
        }
        return ready.await().getOrThrow()
    }

    suspend fun restoreOpenTabs(): TabSessionSnapshot =
        awaitReady().tabs.restore()

    /**
     * Tab state is captured by the Activity, then persisted on the application scope so
     * onStop/onDestroy cannot cancel the final snapshot with the Activity lifecycle.
     */
    fun persistOpenTabs(urls: List<String>, activeIndex: Int, onFailure: () -> Unit = {}) {
        val safeUrls = urls.toList()
        val generation = tabSnapshotGeneration.incrementAndGet()
        scope.launch {
            runCatching {
                val storage = awaitReady()
                tabSnapshotMutex.withLock {
                    // Activity lifecycle callbacks can enqueue snapshots close together. Only
                    // the newest requested snapshot may commit, even if older IO starts late.
                    if (generation == tabSnapshotGeneration.get()) {
                        storage.tabs.save(safeUrls, activeIndex)
                    }
                }
            }.onFailure { error ->
                android.util.Log.e("BrowseX", "Could not persist regular tab snapshot", error)
                onFailure()
            }
        }
    }

    fun recordRecentlyClosed(url: String, title: String, onFailure: () -> Unit = {}) {
        scope.launch {
            runCatching { awaitReady().sessions.recordRecentlyClosed(url, title) }
                .onFailure { error ->
                    android.util.Log.e("BrowseX", "Could not persist recently closed tab", error)
                    onFailure()
                }
        }
    }
}
