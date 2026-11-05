package com.browsex.app.storage

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
}
