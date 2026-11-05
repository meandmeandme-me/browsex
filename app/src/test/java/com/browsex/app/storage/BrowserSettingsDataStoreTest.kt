package com.browsex.app.storage

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserSettingsDataStoreTest {
    @Test
    fun importsOnlySettingsAndKeepsLegacyKeysUntilFullCutover() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val legacyName = "browsex.browser.test.${UUID.randomUUID()}"
        val legacy = context.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
        val legacyBookmarkJson = """[{"url":"https://kept.test"}]"""

        legacy.edit()
            .putBoolean("ad_block_enabled", true)
            .putBoolean("ad_block_private_enabled", true)
            .putString("theme_mode", "dark")
            .putString("language_mode", "ar-MA")
            .putString("search_engine", "DuckDuckGo")
            .putStringSet("denied_platform_permissions", setOf("android.permission.CAMERA"))
            .putString("bookmark_items_v1", legacyBookmarkJson)
            .commit()

        val isolatedFile = File(
            context.filesDir,
            "datastore/browsex-test-${UUID.randomUUID()}.preferences_pb"
        )
        val isolatedStore = PreferenceDataStoreFactory.create(
            migrations = listOf(LegacySettingsMigration(context, legacyName)),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { isolatedFile }
        )
        val repository = BrowserSettingsDataStore(context, isolatedStore)
        val initial = repository.snapshot()

        assertTrue(initial.adBlockEnabled)
        assertTrue(initial.adBlockPrivateEnabled)
        assertEquals("dark", initial.themeMode)
        assertEquals("ar-MA", initial.languageMode)
        assertEquals("DuckDuckGo", initial.searchEngine)
        assertEquals(setOf("android.permission.CAMERA"), initial.deniedPlatformPermissions)

        assertTrue(legacy.getBoolean("ad_block_enabled", false))
        assertTrue(legacy.getBoolean("ad_block_private_enabled", false))
        assertEquals("dark", legacy.getString("theme_mode", null))
        assertEquals(legacyBookmarkJson, legacy.getString("bookmark_items_v1", null))

        repository.update(initial.copy(themeMode = "light", searchEngine = "Bing"))
        val updated = repository.snapshot()
        assertEquals("light", updated.themeMode)
        assertEquals("Bing", updated.searchEngine)

        repository.update(
            updated.copy(
                themeMode = "neon",
                languageMode = "not a locale!",
                homeLayout = "carousel",
                searchEngine = "Unknown"
            )
        )
        val normalized = repository.snapshot()
        assertEquals("system", normalized.themeMode)
        assertEquals("system", normalized.languageMode)
        assertEquals("grid", normalized.homeLayout)
        assertEquals("Google", normalized.searchEngine)

        legacy.edit().clear().commit()
        Unit
    }
    @Test
    fun malformedLegacyPreferenceTypesFallBackToSafeDefaults() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val legacyName = "browsex.browser.invalid.\${UUID.randomUUID()}"
        val legacy = context.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
        legacy.edit()
            .putString("ad_block_enabled", "not-a-boolean")
            .putInt("theme_mode", 42)
            .putString("denied_platform_permissions", "not-a-set")
            .commit()

        val isolatedFile = File(
            context.filesDir,
            "datastore/browsex-invalid-\${UUID.randomUUID()}.preferences_pb"
        )
        val isolatedStore = PreferenceDataStoreFactory.create(
            migrations = listOf(LegacySettingsMigration(context, legacyName)),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { isolatedFile }
        )
        val settings = BrowserSettingsDataStore(context, isolatedStore).snapshot()

        assertEquals(false, settings.adBlockEnabled)
        assertEquals("system", settings.themeMode)
        assertTrue(settings.deniedPlatformPermissions.isEmpty())

        legacy.edit().clear().commit()
    }

}
