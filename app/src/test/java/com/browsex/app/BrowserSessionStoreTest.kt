package com.browsex.app

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class BrowserSessionStoreTest {
    private val home = "resource://android/assets/home.html"

    @Test fun regularTabSnapshotRoundTripsWithSelection() {
        val store = BrowserSessionStore(MemoryPreferences())
        store.saveOpenTabs(listOf("https://one.example", "https://two.example"), 1)
        assertEquals(TabSessionState.Snapshot(listOf("https://one.example", "https://two.example"), 1),
            store.restoreOpenTabs())
    }

    @Test fun malformedStoredSnapshotFallsBackToHome() {
        val prefs = MemoryPreferences()
        prefs.edit().putString("open_tabs", "{broken").putInt("active_tab_index", 7).apply()
        assertEquals(TabSessionState.Snapshot(listOf(home), 0), BrowserSessionStore(prefs).restoreOpenTabs())
    }

    @Test fun namedSessionReplacementIsCaseInsensitiveAndBounded() {
        val store = BrowserSessionStore(MemoryPreferences())
        assertTrue(store.saveNamedSession("Work", listOf("https://one.example"), 0))
        assertTrue(store.saveNamedSession("work", listOf("https://two.example"), 0))
        assertEquals(1, store.savedSessions().size)
        assertEquals(listOf("https://two.example"), store.savedSessions().single().urls)
        assertTrue(store.removeNamedSession(store.savedSessions().single().id))
        assertTrue(store.savedSessions().isEmpty())
    }

    @Test fun recentlyClosedEntriesCanBeConsumedOnlyOnce() {
        val store = BrowserSessionStore(MemoryPreferences())
        store.recordRecentlyClosedTab("https://example.com", "Example")
        val item = store.recentlyClosedTabs().single()
        assertEquals("https://example.com", item.url)
        assertNotNull(store.consumeRecentlyClosedTab(item.id))
        assertNull(store.consumeRecentlyClosedTab(item.id))
    }

    private class MemoryPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = MemoryEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class MemoryEditor : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false
            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { changes[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
                apply { changes[key] = values?.toSet() }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { changes[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { changes[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { changes[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { changes[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = apply { removals.add(key) }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean { applyChanges(); return true }
            override fun apply() = applyChanges()
            private fun applyChanges() {
                if (clearAll) values.clear()
                removals.forEach(values::remove)
                changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
        }
    }
}
