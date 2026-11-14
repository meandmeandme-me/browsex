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
            assertFalse(
                repository.recordVisit("javascript:alert(1)", "Unsafe", privateMode = false, visitedAt = 1L)
            )
            assertTrue(
                repository.recordVisit("https://example.test", "First", privateMode = false, visitedAt = 10L)
            )
            assertTrue(
                repository.recordVisit("https://other.test", "Other", privateMode = false, visitedAt = 20L)
            )
            assertTrue(
                repository.recordVisit("https://example.test", "Updated", privateMode = false, visitedAt = 30L)
            )
            val entries = repository.recent()
            assertEquals(2, entries.size)
            assertEquals("https://example.test", entries.first().url)
            assertEquals("Updated", entries.first().title)
            assertEquals(30L, entries.first().visitedAt)
            assertEquals(2, entries.first().visitCount)
        } finally {
            database.close()
        }
    }

    @Test
    fun privateVisitsAreRejectedWithoutChangingExistingHistory() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomHistoryRepository(database.storageDao())
            assertTrue(
                repository.recordVisit("https://same.example", "Regular visit", privateMode = false, visitedAt = 10L)
            )
            assertFalse(
                repository.recordVisit(
                    "https://same.example",
                    "Private visit",
                    privateMode = true,
                    visitedAt = 20L
                )
            )

            val entries = repository.recent()
            assertEquals(1, entries.size)
            assertEquals("Regular visit", entries.single().title)
            assertEquals(10L, entries.single().visitedAt)
            assertEquals(1, entries.single().visitCount)
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
                assertTrue(
                    repository.recordVisit(
                        "https://page-$index.example",
                        "Page $index",
                        privateMode = false,
                        visitedAt = index.toLong()
                    )
                )
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
