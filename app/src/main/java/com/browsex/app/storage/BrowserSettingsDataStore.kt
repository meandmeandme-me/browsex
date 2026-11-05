package com.browsex.app.storage

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.content.SharedPreferences
import com.browsex.app.BrowserPreferencePolicy
import com.browsex.app.BrowserStorePolicy
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
internal class BrowserSettingsDataStore(
    context: Context,
    storeOverride: DataStore<Preferences>? = null
) {
    private val store = storeOverride ?: context.applicationContext.browserSettingsDataStore

    val data: Flow<BrowserSettings> = store.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map(::decodeSettings)

    suspend fun snapshot(): BrowserSettings = data.first()

    /** Full replacement for controlled settings screens. */
    suspend fun update(settings: BrowserSettings) {
        update { settings }
    }

    /**
     * Atomic read-modify-write for one or more settings. Feature controllers should
     * use this form so concurrent updates do not overwrite unrelated preferences.
     */
    suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        store.edit { preferences ->
            writeSettings(preferences, transform(decodeSettings(preferences)))
        }
    }

    private fun decodeSettings(p: Preferences): BrowserSettings = BrowserSettings(
        adBlockEnabled = p[Keys.adBlockEnabled] ?: false,
        adBlockInstalled = p[Keys.adBlockInstalled] ?: false,
        adBlockPrivateEnabled = p[Keys.adBlockPrivateEnabled] ?: false,
        themeMode = BrowserPreferencePolicy.normalizeTheme(p[Keys.themeMode]),
        languageMode = BrowserPreferencePolicy.normalizeLanguageTag(p[Keys.languageMode]),
        siteNotificationsEnabled = p[Keys.siteNotificationsEnabled] ?: true,
        homeShowQuickLinks = p[Keys.homeShowQuickLinks] ?: true,
        homeShowDiscovery = p[Keys.homeShowDiscovery] ?: true,
        homeLayout = BrowserPreferencePolicy.normalizeHomeLayout(p[Keys.homeLayout]),
        searchEngine = BrowserStorePolicy.normalizeSearchEngine(p[Keys.searchEngine]),
        deniedPlatformPermissions = p[Keys.deniedPlatformPermissions] ?: emptySet()
    )

    private fun writeSettings(
        p: androidx.datastore.preferences.core.MutablePreferences,
        settings: BrowserSettings
    ) {
        p[Keys.adBlockEnabled] = settings.adBlockEnabled
        p[Keys.adBlockInstalled] = settings.adBlockInstalled
        p[Keys.adBlockPrivateEnabled] = settings.adBlockPrivateEnabled
        p[Keys.themeMode] = BrowserPreferencePolicy.normalizeTheme(settings.themeMode)
        p[Keys.languageMode] = BrowserPreferencePolicy.normalizeLanguageTag(settings.languageMode)
        p[Keys.siteNotificationsEnabled] = settings.siteNotificationsEnabled
        p[Keys.homeShowQuickLinks] = settings.homeShowQuickLinks
        p[Keys.homeShowDiscovery] = settings.homeShowDiscovery
        p[Keys.homeLayout] = BrowserPreferencePolicy.normalizeHomeLayout(settings.homeLayout)
        p[Keys.searchEngine] = BrowserStorePolicy.normalizeSearchEngine(settings.searchEngine)
        p[Keys.deniedPlatformPermissions] = settings.deniedPlatformPermissions
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

/**
 * Copies only missing scalar preferences and deliberately keeps the legacy file intact.
 * DataStore's built-in SharedPreferencesMigration removes migrated keys during cleanup,
 * which is unsafe while older code paths may still read those keys during staged cutover.
 */
internal class LegacySettingsMigration(
    context: Context,
    private val legacyName: String = "browsex.browser"
) : DataMigration<Preferences> {
    private val legacy: SharedPreferences =
        context.applicationContext.getSharedPreferences(legacyName, Context.MODE_PRIVATE)

    private val booleanKeys = setOf(
        "ad_block_enabled", "ad_block_installed", "ad_block_private_enabled",
        "site_notifications_enabled", "home_show_quick_links", "home_show_discovery"
    )
    private val stringKeys = setOf(
        "theme_mode", "language_mode", "home_layout", "search_engine"
    )
    private val setKeys = setOf("denied_platform_permissions")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        (booleanKeys + stringKeys + setKeys).any { key ->
            legacy.contains(key) && when (key) {
                in booleanKeys -> currentData[booleanPreferencesKey(key)] == null
                in stringKeys -> currentData[stringPreferencesKey(key)] == null
                else -> currentData[stringSetPreferencesKey(key)] == null
            }
        }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val migrated = currentData.toMutablePreferences()
        booleanKeys.forEach { key ->
            if (migrated[booleanPreferencesKey(key)] == null && legacy.contains(key)) {
                migrated[booleanPreferencesKey(key)] = runCatching { legacy.getBoolean(key, false) }.getOrDefault(false)
            }
        }
        stringKeys.forEach { key ->
            if (migrated[stringPreferencesKey(key)] == null && legacy.contains(key)) {
                runCatching { legacy.getString(key, null) }.getOrNull()?.let { migrated[stringPreferencesKey(key)] = it }
            }
        }
        setKeys.forEach { key ->
            if (migrated[stringSetPreferencesKey(key)] == null && legacy.contains(key)) {
                migrated[stringSetPreferencesKey(key)] = runCatching { legacy.getStringSet(key, emptySet()).orEmpty().toSet() }.getOrDefault(emptySet())
            }
        }
        return migrated
    }

    override suspend fun cleanUp() = Unit
}

private val Context.browserSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "browsex.settings",
    produceMigrations = { context -> listOf(LegacySettingsMigration(context)) }
)
