package com.browsex.app.storage

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomTabStateRepositoryTest {
    @Test
    fun tabSnapshotPersistsValidatedTabsAndActiveSelection() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomTabStateRepository(database.storageDao())
            assertTrue(repository.save(
                listOf("https://one.example", "file:///private", "https://two.example"),
                2,
                42L
            ))
            val restored = repository.restore()
            assertEquals(listOf("https://one.example", "https://two.example"), restored.urls)
            assertEquals(1, restored.activeIndex)
            assertEquals(42L, restored.savedAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun emptyTabSnapshotRestoresSafeHomeAndClearResetsState() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomTabStateRepository(database.storageDao())
            assertEquals(listOf("resource://android/assets/home.html"), repository.restore().urls)
            repository.save(listOf("https://example.test"), 0, 1L)
            repository.clear(2L)
            val restored = repository.restore()
            assertEquals(listOf("resource://android/assets/home.html"), restored.urls)
            assertEquals(0, restored.activeIndex)
            assertEquals(2L, restored.savedAt)
        } finally {
            database.close()
        }
    }
}
