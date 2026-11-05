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

/**
 * Application-scoped entry point for Room storage.
 *
 * Migration is lazy: legacy SharedPreferences remain authoritative until a feature
 * explicitly requests the Room graph. The immutable legacy snapshot is then imported
 * before that feature performs its first Room read/write.
 */
internal class BrowserStorageRuntime(context: Context) {
    private val appContext = context.applicationContext
    private val graph = BrowserStorageGraph(appContext)
    private val migrator = LegacyStorageMigrator(appContext, databaseOverride = graph.database)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val ready = CompletableDeferred<Result<BrowserStorageGraph>>()
    private val tabSnapshotMutex = Mutex()

    suspend fun awaitReady(): BrowserStorageGraph {
        if (started.compareAndSet(false, true)) {
            scope.launch {
                val result = runCatching {
                    migrator.migrateIfNeeded()
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
    fun persistOpenTabs(urls: List<String>, activeIndex: Int) {
        val safeUrls = urls.toList()
        scope.launch {
            runCatching {
                val storage = awaitReady()
                tabSnapshotMutex.withLock {
                    storage.tabs.save(safeUrls, activeIndex)
                }
            }.onFailure { error ->
                android.util.Log.e("BrowseX", "Could not persist regular tab snapshot", error)
            }
        }
    }

    fun recordRecentlyClosed(url: String, title: String) {
        scope.launch {
            runCatching { awaitReady().sessions.recordRecentlyClosed(url, title) }
                .onFailure { error ->
                    android.util.Log.e("BrowseX", "Could not persist recently closed tab", error)
                }
        }
    }
}
