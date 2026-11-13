package com.wren.app

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserContentProtectionPolicyTest {
    @After
    fun resetPolicy() {
        BrowserContentProtectionPolicy.configure(enabled = false, privateAllowed = false)
    }

    @Test
    fun geckoTrackingProtectionRemainsOnWhenUBlockIsNotInstalledOrEnabled() {
        BrowserContentProtectionPolicy.configure(enabled = false, privateAllowed = false)

        assertTrue(BrowserContentProtectionPolicy.useGeckoTrackingProtection(privateMode = false))
        assertTrue(BrowserContentProtectionPolicy.useGeckoTrackingProtection(privateMode = true))
    }

    @Test
    fun uBlockReplacesGeckoTrackerBlockingInRegularTabs() {
        BrowserContentProtectionPolicy.configure(enabled = true, privateAllowed = false)

        assertFalse(BrowserContentProtectionPolicy.useGeckoTrackingProtection(privateMode = false))
        assertTrue(BrowserContentProtectionPolicy.useGeckoTrackingProtection(privateMode = true))
    }

    @Test
    fun privateTabsKeepGeckoProtectionUnlessPrivateUseWasExplicitlyAllowed() {
        BrowserContentProtectionPolicy.configure(enabled = true, privateAllowed = false)
        assertTrue(BrowserContentProtectionPolicy.useGeckoTrackingProtection(privateMode = true))

        BrowserContentProtectionPolicy.configure(enabled = true, privateAllowed = true)
        assertFalse(BrowserContentProtectionPolicy.useGeckoTrackingProtection(privateMode = true))
    }

    @Test
    fun disablingUBlockRestoresGeckoTrackingProtectionEverywhere() {
        BrowserContentProtectionPolicy.configure(enabled = true, privateAllowed = true)
        BrowserContentProtectionPolicy.configure(enabled = false, privateAllowed = false)

        assertTrue(BrowserContentProtectionPolicy.useGeckoTrackingProtection(privateMode = false))
        assertTrue(BrowserContentProtectionPolicy.useGeckoTrackingProtection(privateMode = true))
    }
}
