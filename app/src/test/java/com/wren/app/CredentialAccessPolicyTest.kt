package com.wren.app

import org.junit.Assert.*
import org.junit.Test

class CredentialAccessPolicyTest {
    @Test fun credentialsAreLockedByDefaultAndAfterExpiry() {
        var now = 100L
        val policy = CredentialAccessPolicy({ now }, unlockWindowMillis = 60_000L)
        assertFalse(policy.canFetchCredentials(privateTabsOpen = false))
        policy.unlock()
        assertTrue(policy.canFetchCredentials(privateTabsOpen = false))
        now += 60_000L
        assertFalse(policy.canFetchCredentials(privateTabsOpen = false))
    }

    @Test fun privateTabsOverrideAnActiveUnlockWindow() {
        val policy = CredentialAccessPolicy({ 100L })
        policy.unlock()
        assertFalse(policy.canFetchCredentials(privateTabsOpen = true))
        assertTrue(policy.canFetchCredentials(privateTabsOpen = false))
    }

    @Test fun explicitLockImmediatelyClosesTheWindow() {
        val policy = CredentialAccessPolicy({ 100L })
        policy.unlock()
        policy.lock()
        assertFalse(policy.canFetchCredentials(privateTabsOpen = false))
    }

    @Test fun rejectsAnInvalidUnlockWindow() {
        try {
            CredentialAccessPolicy({ 0L }, unlockWindowMillis = 0L)
            fail("Expected invalid unlock window to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
