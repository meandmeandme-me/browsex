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
import com.browsex.app.BrowserPreferencePolicy
import com.browsex.app.BrowserStorePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** DataStore preferences backend for the clean-start browser runtime. */
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
        addressBarPosition = BrowserPreferencePolicy.normalizeAddressBarPosition(p[Keys.addressBarPosition]),
        reduceTransparency = p[Keys.reduceTransparency] ?: false,
        reduceMotion = p[Keys.reduceMotion] ?: false,
        highContrast = p[Keys.highContrast] ?: false,
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
        p[Keys.addressBarPosition] = BrowserPreferencePolicy.normalizeAddressBarPosition(settings.addressBarPosition)
        p[Keys.reduceTransparency] = settings.reduceTransparency
        p[Keys.reduceMotion] = settings.reduceMotion
        p[Keys.highContrast] = settings.highContrast
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
        val addressBarPosition = stringPreferencesKey("address_bar_position")
        val reduceTransparency = booleanPreferencesKey("reduce_transparency")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val highContrast = booleanPreferencesKey("high_contrast")
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
    val addressBarPosition: String = "auto",
    val reduceTransparency: Boolean = false,
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    val searchEngine: String = "Google",
    val deniedPlatformPermissions: Set<String> = emptySet()
)

private val Context.browserSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "browsex.settings"
)
