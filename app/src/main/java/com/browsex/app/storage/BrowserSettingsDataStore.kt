package com.browsex.app.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.toMutablePreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore preferences backend. The legacy migration deliberately copies
 * settings without deleting SharedPreferences keys: the current synchronous
 * compatibility facade still reads them until the complete storage cutover.
 */
internal class BrowserSettingsDataStore(context: Context) {
    private val store = context.applicationContext.browserSettingsDataStore

    val data: Flow<BrowserSettings> = store.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { p ->
            BrowserSettings(
                adBlockEnabled = p[Keys.adBlockEnabled] ?: false,
                adBlockInstalled = p[Keys.adBlockInstalled] ?: false,
                adBlockPrivateEnabled = p[Keys.adBlockPrivateEnabled] ?: false,
                themeMode = p[Keys.themeMode] ?: "system",
                languageMode = p[Keys.languageMode] ?: "system",
                siteNotificationsEnabled = p[Keys.siteNotificationsEnabled] ?: true,
                homeShowQuickLinks = p[Keys.homeShowQuickLinks] ?: true,
                homeShowDiscovery = p[Keys.homeShowDiscovery] ?: true,
                homeLayout = p[Keys.homeLayout] ?: "grid",
                searchEngine = p[Keys.searchEngine] ?: "Google",
                deniedPlatformPermissions = p[Keys.deniedPlatformPermissions] ?: emptySet()
            )
        }

    suspend fun snapshot(): BrowserSettings = data.first()

    suspend fun update(settings: BrowserSettings) {
        store.edit { p ->
            p[Keys.adBlockEnabled] = settings.adBlockEnabled
            p[Keys.adBlockInstalled] = settings.adBlockInstalled
            p[Keys.adBlockPrivateEnabled] = settings.adBlockPrivateEnabled
            p[Keys.themeMode] = settings.themeMode
            p[Keys.languageMode] = settings.languageMode
            p[Keys.siteNotificationsEnabled] = settings.siteNotificationsEnabled
            p[Keys.homeShowQuickLinks] = settings.homeShowQuickLinks
            p[Keys.homeShowDiscovery] = settings.homeShowDiscovery
            p[Keys.homeLayout] = settings.homeLayout
            p[Keys.searchEngine] = settings.searchEngine
            p[Keys.deniedPlatformPermissions] = settings.deniedPlatformPermissions
        }
    }

    private object Keys {
        val adBlockEnabled = booleanPreferencesKey("ad_block_enabled")
        val adBlockInstalled = booleanPreferencesKey("ad_block_installed")
        val adBlockPrivateEnabled = booleanPreferencesKey("ad_block_private_enabled")
        val themeMode = stringPreferencesKey("theme_mode")
        val languageMode = stringPreferencesKey("language_mode")
        val siteNotificationsEnabled = booleanPreferencesKey("site_notifications_enabled")
        val homeShowQuickLinks = booleanPreferencesKey("home_show_quick_links")
        val homeShowDiscovery = booleanPreferencesKey("home_show_discovery")
        val homeLayout = stringPreferencesKey("home_layout")
        val searchEngine = stringPreferencesKey("search_engine")
        val deniedPlatformPermissions = stringSetPreferencesKey("denied_platform_permissions")
        val legacyMigrationCompleted = booleanPreferencesKey("legacy_settings_migration_completed")
    }

    private class LegacySettingsMigration(context: Context) : DataMigration<Preferences> {
        private val legacy: SharedPreferences =
            context.getSharedPreferences("browsex.browser", Context.MODE_PRIVATE)

        override suspend fun shouldMigrate(currentData: Preferences): Boolean =
            currentData[Keys.legacyMigrationCompleted] != true

        override suspend fun migrate(currentData: Preferences): Preferences {
            val values = legacy.all
            return currentData.toMutablePreferences().apply {
                (values["ad_block_enabled"] as? Boolean)?.let { this[Keys.adBlockEnabled] = it }
                (values["ad_block_installed"] as? Boolean)?.let { this[Keys.adBlockInstalled] = it }
                (values["ad_block_private_enabled"] as? Boolean)?.let { this[Keys.adBlockPrivateEnabled] = it }
                (values["theme_mode"] as? String)?.let { this[Keys.themeMode] = it }
                (values["language_mode"] as? String)?.let { this[Keys.languageMode] = it }
                (values["site_notifications_enabled"] as? Boolean)?.let { this[Keys.siteNotificationsEnabled] = it }
                (values["home_show_quick_links"] as? Boolean)?.let { this[Keys.homeShowQuickLinks] = it }
                (values["home_show_discovery"] as? Boolean)?.let { this[Keys.homeShowDiscovery] = it }
                (values["home_layout"] as? String)?.let { this[Keys.homeLayout] = it }
                (values["search_engine"] as? String)?.let { this[Keys.searchEngine] = it }
                (values["denied_platform_permissions"] as? Set<*>)?.filterIsInstance<String>()
                    ?.let { this[Keys.deniedPlatformPermissions] = it.toSet() }
                this[Keys.legacyMigrationCompleted] = true
            }
        }

        // Keep the old keys until every production reader/writer has moved off SharedPreferences.
        override suspend fun cleanUp() = Unit
    }

    companion object {
        fun migrations(context: Context): List<DataMigration<Preferences>> =
            listOf(LegacySettingsMigration(context.applicationContext))
    }
}

internal data class BrowserSettings(
    val adBlockEnabled: Boolean = false,
    val adBlockInstalled: Boolean = false,
    val adBlockPrivateEnabled: Boolean = false,
    val themeMode: String = "system",
    val languageMode: String = "system",
    val siteNotificationsEnabled: Boolean = true,
    val homeShowQuickLinks: Boolean = true,
    val homeShowDiscovery: Boolean = true,
    val homeLayout: String = "grid",
    val searchEngine: String = "Google",
    val deniedPlatformPermissions: Set<String> = emptySet()
)

private val Context.browserSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "browsex.settings",
    produceMigrations = BrowserSettingsDataStore::migrations
)
