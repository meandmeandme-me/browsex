package com.wren.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BrowserNotificationIdentityPolicyTest {
    @Test
    fun sameOriginAndNonEmptyWebTagReplaceTheSameAndroidNotification() {
        val first = BrowserNotificationIdentityPolicy.androidTag(
            "https://example.com", "updates", "first-id"
        )
        val second = BrowserNotificationIdentityPolicy.androidTag(
            "https://example.com", "updates", "second-id"
        )
        assertEquals(first, second)
    }

    @Test
    fun differentOriginsDoNotShareNotificationIdentity() {
        val first = BrowserNotificationIdentityPolicy.androidTag(
            "https://one.example", "updates", "same-id"
        )
        val second = BrowserNotificationIdentityPolicy.androidTag(
            "https://two.example", "updates", "same-id"
        )
        assertNotEquals(first, second)
    }

    @Test
    fun untaggedNotificationsRemainIndependent() {
        val first = BrowserNotificationIdentityPolicy.androidTag(
            "https://example.com", "", "unique-1"
        )
        val second = BrowserNotificationIdentityPolicy.androidTag(
            "https://example.com", null, "unique-2"
        )
        assertNotEquals(first, second)
        assertEquals("wren-web-unique-1", first)
    }
}
