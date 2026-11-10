package com.browsex.app.storage

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomSitePermissionRepositoryTest {
    @Test
    fun permissionDecisionsUseCanonicalOriginsAndAskClearsTheSavedChoice() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSitePermissionRepository(database.storageDao())
            assertTrue(repository.setDecision("HTTPS://EXAMPLE.COM:443/path", "camera", "allow", 1L))
            assertEquals("allow", repository.decision("https://example.com/another", "camera"))
            assertNull(repository.decision("https://sub.example.com", "camera"))
            assertTrue(repository.setDecision("https://example.com", "camera", "ask", 2L))
            assertNull(repository.decision("https://example.com", "camera"))
        } finally {
            database.close()
        }
    }

    @Test
    fun websiteNotificationsHaveIndependentPerOriginDecisions() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSitePermissionRepository(database.storageDao())
            assertTrue(repository.setDecision("https://news.example/article", "notifications", "deny", 10L))
            assertEquals("deny", repository.decision("https://news.example", "notifications"))
            assertNull(repository.decision("https://other.example", "notifications"))
            assertTrue(repository.setDecision("https://news.example", "notifications", "ask", 11L))
            assertNull(repository.decision("https://news.example", "notifications"))
        } finally {
            database.close()
        }
    }

    @Test
    fun rejectsUnsafeOriginsFeaturesAndDecisions() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSitePermissionRepository(database.storageDao())
            assertFalse(repository.setDecision("https://user:pass@example.com", "camera", "allow"))
            assertFalse(repository.setDecision("file:///private", "camera", "allow"))
            assertTrue(repository.setDecision("https://example.com", "geolocation", "allow", 3L))
            assertEquals("allow", repository.decision("https://example.com", "geolocation"))
            assertFalse(repository.setDecision("https://example.com", "location", "allow"))
            assertFalse(repository.setDecision("https://example.com", "camera", "prompt"))
            assertNull(repository.decision("file:///private", "camera"))
        } finally {
            database.close()
        }
    }

    @Test
    fun permissionChoicesAreBoundedAndCanBeClearedByOrigin() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSitePermissionRepository(database.storageDao())
            repeat(110) { index ->
                assertTrue(repository.setDecision("https://site-$index.example", "camera", "deny", index.toLong()))
            }
            assertEquals(100, repository.all().size)
            assertNull(repository.decision("https://site-0.example", "camera"))
            assertEquals("deny", repository.decision("https://site-109.example", "camera"))
            assertTrue(repository.clearOrigin("https://site-109.example/path"))
            assertNull(repository.decision("https://site-109.example", "camera"))
            repository.clearAll()
            assertTrue(repository.all().isEmpty())
        } finally {
            database.close()
        }
    }
}
