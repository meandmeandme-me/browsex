package com.browsex.app.storage

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomHistoryRepositoryTest {
    @Test
    fun historyValidatesUrlsDeduplicatesAndKeepsLatestVisit() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomHistoryRepository(database.storageDao())
            assertFalse(repository.recordVisit("javascript:alert(1)", "Unsafe", 1L))
            assertTrue(repository.recordVisit("https://example.test", "First", 10L))
            assertTrue(repository.recordVisit("https://other.test", "Other", 20L))
            assertTrue(repository.recordVisit("https://example.test", "Updated", 30L))
            val entries = repository.recent()
            assertEquals(2, entries.size)
            assertEquals("https://example.test", entries.first().url)
            assertEquals("Updated", entries.first().title)
            assertEquals(30L, entries.first().visitedAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun historyIsBoundedToThreeHundredAndSupportsRemovalAndClear() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomHistoryRepository(database.storageDao())
            repeat(305) { index ->
                assertTrue(repository.recordVisit("https://page-$index.example", "Page $index", index.toLong()))
            }
            assertEquals(300, repository.recent().size)
            assertEquals("https://page-304.example", repository.recent().first().url)
            assertTrue(repository.remove("https://page-304.example"))
            assertFalse(repository.remove("https://page-304.example"))
            repository.clear()
            assertTrue(repository.recent().isEmpty())
        } finally {
            database.close()
        }
    }
}
