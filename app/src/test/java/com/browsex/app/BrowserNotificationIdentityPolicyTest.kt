package com.browsex.app

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
    fun whitespaceOnlyWebTagsAreNonEmptyAndReplaceWithinTheSameOrigin() {
        val first = BrowserNotificationIdentityPolicy.androidTag(
            "https://example.com", " ", "first-id"
        )
        val second = BrowserNotificationIdentityPolicy.androidTag(
            "https://example.com", " ", "second-id"
        )
        val untagged = BrowserNotificationIdentityPolicy.androidTag(
            "https://example.com", "", "unique-id"
        )

        assertEquals(first, second)
        assertNotEquals(first, untagged)
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
        assertEquals("browsex-web-unique-1", first)
    }
}
