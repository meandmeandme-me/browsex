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
class RoomSessionRepositoryTest {
    @Test
    fun namedSessionsReplaceByNameAndRestoreOnlyValidatedTabs() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSessionRepository(database.storageDao())
            assertFalse(repository.saveSession("   ", listOf("https://safe.example"), 0))
            assertTrue(repository.saveSession(
                "Research",
                listOf("https://one.example", "file:///private", "https://two.example"),
                1,
                10L
            ))
            val first = repository.sessions().single()
            assertEquals(listOf("https://one.example", "https://two.example"), first.urls)
            assertEquals(1, first.activeIndex)

            assertTrue(repository.saveSession(" research ", listOf("https://replacement.example"), 0, 20L))
            val updated = repository.sessions().single()
            assertEquals(first.id, updated.id)
            assertEquals(listOf("https://replacement.example"), updated.urls)
            assertEquals(20L, updated.savedAt)
            assertTrue(repository.deleteSession(updated.id))
            assertTrue(repository.sessions().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun namedSessionsRespectTenSessionLimit() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSessionRepository(database.storageDao())
            repeat(10) { index ->
                assertTrue(repository.saveSession("Session $index", listOf("https://$index.example"), 0, index.toLong()))
            }
            assertFalse(repository.saveSession("Session 11", listOf("https://eleven.example"), 0))
            assertEquals(10, repository.sessions().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun clearingRecentlyClosedRemovesEveryPersistedEntry() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSessionRepository(database.storageDao())
            val now = System.currentTimeMillis()
            assertTrue(repository.recordRecentlyClosed("https://one.example", "One", privateMode = false, closedAt = now))
            assertTrue(repository.recordRecentlyClosed("https://two.example", "Two", privateMode = false, closedAt = now + 1))
            assertEquals(2, repository.recentlyClosed().size)
            repository.clearRecentlyClosed()
            assertTrue(repository.recentlyClosed().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun recentlyClosedTabsAreBoundedDeduplicatedAndConsumedOnce() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSessionRepository(database.storageDao())
            assertFalse(repository.recordRecentlyClosed("file:///private", "Private", privateMode = false))
            assertFalse(repository.recordRecentlyClosed("resource://android/assets/home.html", "Home", privateMode = false))
            val baseTime = System.currentTimeMillis()
            repeat(11) { index ->
                assertTrue(repository.recordRecentlyClosed(
                    "https://$index.example",
                    "Page $index",
                    privateMode = false,
                    closedAt = baseTime + index
                ))
            }
            assertEquals(10, repository.recentlyClosed().size)
            val selected = repository.recentlyClosed().first()
            assertEquals("https://10.example", selected.url)
            assertEquals(selected, repository.consumeRecentlyClosed(selected.id))
            assertNull(repository.consumeRecentlyClosed(selected.id))
            assertEquals(9, repository.recentlyClosed().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun privateTabsAreRejectedFromRecentlyClosedStorage() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSessionRepository(database.storageDao())
            assertFalse(repository.recordRecentlyClosed(
                "https://private.example", "Private", privateMode = true
            ))
            assertTrue(repository.recentlyClosed().isEmpty())

            assertTrue(repository.recordRecentlyClosed(
                "https://regular.example", "Regular", privateMode = false
            ))
            assertEquals(listOf("https://regular.example"), repository.recentlyClosed().map { it.url })
        } finally {
            database.close()
        }
    }

}
