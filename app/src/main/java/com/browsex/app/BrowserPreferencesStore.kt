package com.browsex.app

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Owns browser-wide preferences and persisted per-origin permission decisions.
 *
 * Kept behind a small store boundary so the current SharedPreferences backend can
 * later be migrated independently to DataStore without coupling feature code to it.
 */
internal class BrowserPreferencesStore(private val prefs: SharedPreferences) {
    var adBlockEnabled: Boolean
        get() = prefs.getBoolean("ad_block_enabled", false)
        set(value) { prefs.edit().putBoolean("ad_block_enabled", value).apply() }

    var adBlockInstalled: Boolean
        get() = prefs.getBoolean("ad_block_installed", false)
        set(value) { prefs.edit().putBoolean("ad_block_installed", value).apply() }

    /** True only when the user explicitly allowed uBlock Origin in private tabs. */
    var adBlockPrivateEnabled: Boolean
        get() = prefs.getBoolean("ad_block_private_enabled", false)
        set(value) { prefs.edit().putBoolean("ad_block_private_enabled", value).apply() }

    var themeMode: String
        get() = BrowserPreferencePolicy.normalizeTheme(prefs.getString("theme_mode", "system"))
        set(value) { prefs.edit().putString("theme_mode", BrowserPreferencePolicy.normalizeTheme(value)).apply() }

    /** BCP 47 app language tag, or system to follow the device locale. */
    var languageMode: String
        get() = BrowserPreferencePolicy.normalizeLanguageTag(prefs.getString("language_mode", "system"))
        set(value) { prefs.edit().putString("language_mode", BrowserPreferencePolicy.normalizeLanguageTag(value)).apply() }

    var siteNotificationsEnabled: Boolean
        get() = prefs.getBoolean("site_notifications_enabled", true)
        set(value) { prefs.edit().putBoolean("site_notifications_enabled", value).apply() }

    var homeShowQuickLinks: Boolean
        get() = prefs.getBoolean("home_show_quick_links", true)
        set(value) { prefs.edit().putBoolean("home_show_quick_links", value).apply() }

    var homeShowDiscovery: Boolean
        get() = prefs.getBoolean("home_show_discovery", true)
        set(value) { prefs.edit().putBoolean("home_show_discovery", value).apply() }

    var homeLayout: String
        get() = BrowserPreferencePolicy.normalizeHomeLayout(prefs.getString("home_layout", "grid"))
        set(value) { prefs.edit().putString("home_layout", BrowserPreferencePolicy.normalizeHomeLayout(value)).apply() }

    var searchEngine: String
        get() = BrowserStorePolicy.normalizeSearchEngine(prefs.getString("search_engine", "Google"))
        set(value) { prefs.edit().putString("search_engine", BrowserStorePolicy.normalizeSearchEngine(value)).apply() }

    fun resetHomePreferences() {
        prefs.edit().remove("home_show_quick_links").remove("home_show_discovery").remove("home_layout").apply()
    }

    private fun platformPermissionDenials(): MutableSet<String> =
        runCatching {
            prefs.getStringSet("denied_platform_permissions", emptySet()).orEmpty().toMutableSet()
        }.getOrDefault(mutableSetOf())

    fun wasPlatformPermissionDenied(permission: String): Boolean =
        permission in platformPermissionDenials()

    /**
     * SharedPreferences protects individual reads and writes, not a read-modify-write
     * sequence. Synchronizing on the shared instance prevents concurrent permission
     * callbacks from overwriting each other's additions.
     */
    fun markPlatformPermissionDenied(permission: String) = synchronized(prefs) {
        val values = platformPermissionDenials()
        values.add(permission)
        prefs.edit().putStringSet("denied_platform_permissions", values).apply()
    }

    fun clearPlatformPermissionDenial(permission: String) = synchronized(prefs) {
        val values = platformPermissionDenials()
        if (values.remove(permission)) prefs.edit().putStringSet("denied_platform_permissions", values).apply()
    }

    fun siteMediaDecision(origin: String, feature: String): String? {
        val canonicalOrigin = canonicalPermissionOrigin(origin) ?: return null
        if (feature !in MEDIA_FEATURES) return null
        return try {
            JSONObject(prefs.getString("site_media_permissions", "{}") ?: "{}")
                .optString("$canonicalOrigin|$feature").takeIf { it == "allow" || it == "deny" }
        } catch (_: Exception) { null }
    }

    fun setSiteMediaDecision(origin: String, feature: String, decision: String) {
        val canonicalOrigin = canonicalPermissionOrigin(origin) ?: return
        if (feature !in MEDIA_FEATURES || decision !in PERMISSION_DECISIONS) return
        synchronized(prefs) {
            val key = "$canonicalOrigin|$feature"
            val json = try { JSONObject(prefs.getString("site_media_permissions", "{}") ?: "{}") }
            catch (_: Exception) { JSONObject() }
            if (decision == "ask") json.remove(key) else json.put(key, decision)
            while (json.length() > MAX_ORIGIN_MEDIA_DECISIONS) {
                val oldest = json.keys().asSequence().firstOrNull() ?: break
                json.remove(oldest)
            }
            prefs.edit().putString("site_media_permissions", json.toString()).apply()
        }
    }

    fun clearSiteMediaDecisions(origin: String) {
        val canonicalOrigin = canonicalPermissionOrigin(origin) ?: return
        synchronized(prefs) {
            val json = try { JSONObject(prefs.getString("site_media_permissions", "{}") ?: "{}") }
            catch (_: Exception) { return }
            json.keys().asSequence().filter { it.startsWith("$canonicalOrigin|") }.toList().forEach(json::remove)
            prefs.edit().putString("site_media_permissions", json.toString()).apply()
        }
    }

    fun clearAllSiteMediaDecisions() = synchronized(prefs) {
        prefs.edit().remove("site_media_permissions").apply()
    }

    private fun canonicalPermissionOrigin(origin: String): String? =
        BrowserSiteOriginPolicy.canonicalOrigin(origin)

    private companion object {
        val MEDIA_FEATURES = setOf("camera", "microphone", "notifications", "geolocation")
        val PERMISSION_DECISIONS = setOf("allow", "deny", "ask")
        const val MAX_ORIGIN_MEDIA_DECISIONS = 100
    }
}
