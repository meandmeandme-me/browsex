package com.browsex.app.storage

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun cleanStartDoesNotImportLegacySharedPreferences(): Unit = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val legacyName = "browsex.browser.test.${UUID.randomUUID()}"
        val legacy = context.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
        legacy.edit()
            .putBoolean("ad_block_enabled", true)
            .putString("theme_mode", "dark")
            .putString("language_mode", "ar-MA")
            .putString("search_engine", "DuckDuckGo")
            .putString("bookmark_items_v1", """[{"url":"https://legacy.test"}]""")
            .commit()

        val isolatedStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = {
                File(context.filesDir, "datastore/browsex-clean-${UUID.randomUUID()}.preferences_pb")
            }
        )
        val settings = BrowserSettingsDataStore(context, isolatedStore).snapshot()

        assertFalse(settings.adBlockEnabled)
        assertEquals("system", settings.themeMode)
        assertEquals("system", settings.languageMode)
        assertEquals("Google", settings.searchEngine)
        assertEquals("auto", settings.addressBarPosition)
        assertFalse(settings.reduceTransparency)
        assertFalse(settings.reduceMotion)
        assertFalse(settings.highContrast)
        assertTrue(legacy.contains("bookmark_items_v1"))
        assertTrue(legacy.getBoolean("ad_block_enabled", false))
        legacy.edit().clear().commit()
    }

    @Test
    fun updatesAreAtomicAndValuesAreNormalized() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val isolatedStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = {
                File(context.filesDir, "datastore/browsex-update-${UUID.randomUUID()}.preferences_pb")
            }
        )
        val repository = BrowserSettingsDataStore(context, isolatedStore)

        repository.update { it.copy(themeMode = "dark", searchEngine = "Bing", addressBarPosition = "bottom", reduceTransparency = true, reduceMotion = true, highContrast = true) }
        repository.update { it.copy(languageMode = "fr-FR") }
        val updated = repository.snapshot()
        assertEquals("dark", updated.themeMode)
        assertEquals("fr-FR", updated.languageMode)
        assertEquals("Bing", updated.searchEngine)
        assertEquals("bottom", updated.addressBarPosition)
        assertTrue(updated.reduceTransparency)
        assertTrue(updated.reduceMotion)
        assertTrue(updated.highContrast)

        repository.update(updated.copy(
            themeMode = "neon",
            addressBarPosition = "floating",
            languageMode = "not a locale!",
            homeLayout = "carousel",
            searchEngine = "Unknown"
        ))
        val normalized = repository.snapshot()
        assertEquals("system", normalized.themeMode)
        assertEquals("system", normalized.languageMode)
        assertEquals("grid", normalized.homeLayout)
        assertEquals("Google", normalized.searchEngine)
        assertEquals("auto", normalized.addressBarPosition)
        assertTrue(normalized.reduceTransparency)
        assertTrue(normalized.reduceMotion)
        assertTrue(normalized.highContrast)
    }
    @Test
    fun concurrentPartialUpdatesDoNotOverwriteUnrelatedSettings() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val isolatedStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = {
                File(context.filesDir, "datastore/browsex-concurrent-${UUID.randomUUID()}.preferences_pb")
            }
        )
        val repository = BrowserSettingsDataStore(context, isolatedStore)

        (0 until 40).map { index ->
            async(Dispatchers.IO) {
                if (index % 2 == 0) {
                    repository.update { it.copy(themeMode = "dark") }
                } else {
                    repository.update { it.copy(languageMode = "ar-MA") }
                }
            }
        }.awaitAll()

        val result = repository.snapshot()
        assertEquals("dark", result.themeMode)
        assertEquals("ar-MA", result.languageMode)
    }

}
