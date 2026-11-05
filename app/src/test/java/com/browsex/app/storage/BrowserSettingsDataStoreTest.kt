package com.browsex.app.storage

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserSettingsDataStoreTest {
    @Test
    fun importsOnlySettingsAndKeepsLegacyKeysUntilFullCutover() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val legacy = context.getSharedPreferences("browsex.browser", Context.MODE_PRIVATE)
        legacy.edit()
            .putBoolean("ad_block_enabled", true)
            .putBoolean("ad_block_private_enabled", true)
            .putString("theme_mode", "dark")
            .putString("language_mode", "ar-MA")
            .putString("search_engine", "DuckDuckGo")
            .putStringSet("denied_platform_permissions", setOf("android.permission.CAMERA"))
            .putString("bookmark_items_v1", "[{\"url\":\"https://kept.test\"}]")
            .commit()

        // The test uses a fresh DataStore file so its migration is deterministic.
        File(context.filesDir, "datastore/browsex.settings.preferences_pb").delete()
        val repository = BrowserSettingsDataStore(context)
        val initial = repository.snapshot()

        assertTrue(initial.adBlockEnabled)
        assertTrue(initial.adBlockPrivateEnabled)
        assertEquals("dark", initial.themeMode)
        assertEquals("ar-MA", initial.languageMode)
        assertEquals("DuckDuckGo", initial.searchEngine)
        assertEquals(setOf("android.permission.CAMERA"), initial.deniedPlatformPermissions)

        // The compatibility facade still reads these keys during staged cutover.
        assertTrue(legacy.getBoolean("ad_block_enabled", false))
        assertTrue(legacy.getBoolean("ad_block_private_enabled", false))
        assertEquals("dark", legacy.getString("theme_mode", null))
        assertEquals("[{\"url\":\"https://kept.test\"}]", legacy.getString("bookmark_items_v1", null))

        repository.update(initial.copy(themeMode = "light", searchEngine = "Bing"))
        val updated = repository.snapshot()
        assertEquals("light", updated.themeMode)
        assertEquals("Bing", updated.searchEngine)
    }
}
