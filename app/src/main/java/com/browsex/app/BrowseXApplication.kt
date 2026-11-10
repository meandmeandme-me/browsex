package com.browsex.app

import android.app.Application
import com.browsex.app.storage.BrowserStorageRuntime

/** Owns process-wide browser services whose lifetime must outlive an Activity. */
class BrowseXApplication : Application() {
    internal lateinit var storageRuntime: BrowserStorageRuntime
        private set

    /** Process-scoped download owner: Activity recreation must not cancel active response streams. */
    internal lateinit var streamDownloads: StreamDownloadCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        storageRuntime = BrowserStorageRuntime(this)
        streamDownloads = StreamDownloadCoordinator(storageRuntime, DownloadResponseWriter(this))
    }
}
