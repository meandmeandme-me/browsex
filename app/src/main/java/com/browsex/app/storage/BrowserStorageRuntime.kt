package com.browsex.app.storage

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Application-scoped entry point for Room storage.
 *
 * BrowseX starts with a clean Room database baseline before its first public release.
 * Legacy SharedPreferences browsing data is intentionally not imported. Schema changes
 * must use explicit, fixture-tested Room migrations; destructive fallback is disabled.
 */
internal class BrowserStorageRuntime(context: Context) {
    private val appContext = context.applicationContext
    private val graph = BrowserStorageGraph(appContext)
    val settings = BrowserSettingsDataStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initializationMutex = Mutex()
    @Volatile private var readyGraph: BrowserStorageGraph? = null
    private val tabSnapshotMutex = Mutex()
    private val tabSnapshotGeneration = AtomicLong(0L)

    suspend fun awaitReady(): BrowserStorageGraph {
        readyGraph?.let { return it }
        initializationMutex.lock()
        try {
            readyGraph?.let { return it }
            // Keep initialization retryable: a transient SQLite/I/O failure must not
            // permanently poison this process. The mutex ensures only one recovery pass
            // runs at a time, while a failed attempt releases the lock for the next caller.
            // This operation is idempotent: only queued/running downloads are transitioned.
            graph.downloads.recoverInterrupted()
            readyGraph = graph
            return graph
        } finally {
            initializationMutex.unlock()
        }
    }

    suspend fun restoreOpenTabs(): TabSessionSnapshot =
        awaitReady().tabs.restore()

    /**
     * Tab state is captured by the Activity, then persisted on the application scope so
     * onStop/onDestroy cannot cancel the final snapshot with the Activity lifecycle.
     * Privacy flags travel with the snapshot so Room excludes private URLs at the storage boundary.
     */
    fun persistOpenTabs(
        urls: List<String>,
        privateFlags: List<Boolean>,
        activeIndex: Int,
        homeUrl: String,
        onFailure: () -> Unit = {}
    ) {
        val safeUrls = urls.toList()
        val safePrivateFlags = privateFlags.toList()
        val generation = tabSnapshotGeneration.incrementAndGet()
        scope.launch {
            runCatching {
                val storage = awaitReady()
                tabSnapshotMutex.withLock {
                    // Activity lifecycle callbacks can enqueue snapshots close together. Only
                    // the newest requested snapshot may commit, even if older IO starts late.
                    if (generation == tabSnapshotGeneration.get()) {
                        storage.tabs.save(safeUrls, safePrivateFlags, activeIndex, homeUrl)
                    }
                }
            }.onFailure { error ->
                android.util.Log.e("BrowseX", "Could not persist regular tab snapshot", error)
                onFailure()
            }
        }
    }

    fun recordRecentlyClosed(
        url: String,
        title: String,
        privateMode: Boolean,
        onFailure: () -> Unit = {}
    ) {
        if (privateMode) return
        scope.launch {
            runCatching { awaitReady().sessions.recordRecentlyClosed(url, title, privateMode) }
                .onFailure { error ->
                    android.util.Log.e("BrowseX", "Could not persist recently closed tab", error)
                    onFailure()
                }
        }
    }
}
