package com.wren.app

import android.app.Application
import com.wren.app.storage.BrowserStorageRuntime

/** Owns process-wide browser services whose lifetime must outlive an Activity. */
class WrenApplication : Application() {
    internal lateinit var storageRuntime: BrowserStorageRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        storageRuntime = BrowserStorageRuntime(this)
    }
}
