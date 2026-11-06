package com.browsex.app

import android.app.Application
import com.browsex.app.storage.BrowserStorageRuntime

/** Owns process-wide browser services whose lifetime must outlive an Activity. */
class BrowseXApplication : Application() {
    internal lateinit var storageRuntime: BrowserStorageRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        storageRuntime = BrowserStorageRuntime(this)
    }
}
