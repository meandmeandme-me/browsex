package com.browsex.app.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.migrations.SharedPreferencesMigration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore preferences backend. It imports only scalar browser settings from
 * the legacy preference file. Structured collections remain in their own
 * migration path and are never discarded by this migration.
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
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context,
                "browsex.browser",
                keysToMigrate = setOf(
                    "ad_block_enabled",
                    "ad_block_installed",
                    "ad_block_private_enabled",
                    "theme_mode",
                    "language_mode",
                    "site_notifications_enabled",
                    "home_show_quick_links",
                    "home_show_discovery",
                    "home_layout",
                    "search_engine",
                    "denied_platform_permissions"
                )
            )
        )
    }
)
