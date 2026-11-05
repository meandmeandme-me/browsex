package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BrowserPreferencesStoreTest {
    private lateinit var store: BrowserPreferencesStore

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("browser-preferences-test", 0)
        preferences.edit().clear().commit()
        store = BrowserPreferencesStore(preferences)
    }

    @Test
    fun sitePermissionKeysUseCanonicalOrigin() {
        store.setSiteMediaDecision("HTTPS://EXAMPLE.COM:443/path?q=1", "camera", "allow")

        assertEquals("allow", store.siteMediaDecision("https://example.com/another-page", "camera"))
        assertNull(store.siteMediaDecision("https://sub.example.com", "camera"))
    }

    @Test
    fun clearingOriginRemovesAllItsFeatureDecisions() {
        store.setSiteMediaDecision("https://example.com/a", "camera", "allow")
        store.setSiteMediaDecision("https://example.com/b", "microphone", "deny")
        store.setSiteMediaDecision("https://other.example", "camera", "allow")

        store.clearSiteMediaDecisions("https://EXAMPLE.COM:443/settings")

        assertNull(store.siteMediaDecision("https://example.com", "camera"))
        assertNull(store.siteMediaDecision("https://example.com", "microphone"))
        assertEquals("allow", store.siteMediaDecision("https://other.example", "camera"))
    }

    @Test
    fun rejectsCredentialBearingAndNonWebOrigins() {
        store.setSiteMediaDecision("https://user:password@example.com", "camera", "allow")
        store.setSiteMediaDecision("file:///tmp/page.html", "camera", "allow")

        assertNull(store.siteMediaDecision("https://example.com", "camera"))
        assertNull(store.siteMediaDecision("file:///tmp/page.html", "camera"))
    }

    @Test
    fun rejectsUnsupportedFeaturesAndDecisions() {
        store.setSiteMediaDecision("https://example.com", "geolocation", "allow")
        store.setSiteMediaDecision("https://example.com", "camera", "prompt")

        assertNull(store.siteMediaDecision("https://example.com", "geolocation"))
        assertNull(store.siteMediaDecision("https://example.com", "camera"))
    }
}
