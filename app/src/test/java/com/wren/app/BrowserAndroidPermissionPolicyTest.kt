package com.wren.app

import org.junit.Assert.*
import org.junit.Test

class BrowserAndroidPermissionPolicyTest {
    private val supported = setOf("camera", "microphone", "location")

    @Test fun rejectsEmptyAndUnsupportedRequests() {
        assertTrue(BrowserAndroidPermissionPolicy.evaluate(emptyList(), supported, emptySet(), emptySet()).rejected)
        assertTrue(BrowserAndroidPermissionPolicy.evaluate(listOf("camera", "contacts"), supported, emptySet(), emptySet()).rejected)
    }

    @Test fun requestsOnlyMissingDistinctPermissions() {
        val result = BrowserAndroidPermissionPolicy.evaluate(
            listOf("camera", "microphone", "camera"), supported,
            granted = setOf("camera"), previouslyDenied = emptySet()
        )
        assertFalse(result.rejected)
        assertEquals(listOf("microphone"), result.missingPermissions)
    }

    @Test fun rejectsPreviouslyDeniedMissingPermission() {
        val result = BrowserAndroidPermissionPolicy.evaluate(
            listOf("camera", "microphone"), supported,
            granted = setOf("camera"), previouslyDenied = setOf("microphone")
        )
        assertTrue(result.rejected)
    }

    @Test fun returnsEmptyMissingListWhenAllAreGranted() {
        val result = BrowserAndroidPermissionPolicy.evaluate(
            listOf("camera"), supported,
            granted = setOf("camera"), previouslyDenied = emptySet()
        )
        assertFalse(result.rejected)
        assertTrue(result.missingPermissions.isEmpty())
    }
}
