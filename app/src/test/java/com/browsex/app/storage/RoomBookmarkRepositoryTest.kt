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
class RoomBookmarkRepositoryTest {
    @Test
    fun bookmarkCrudUsesRoomConstraintsAndValidatesInputs() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val dao = database.storageDao()
            dao.putFolders(listOf(
                BookmarkFolderEntity(id = "unfiled", name = "Unfiled"),
                BookmarkFolderEntity(id = "work", name = "Work")
            ))
            val repository = RoomBookmarkRepository(dao)

            assertFalse(repository.add("javascript:alert(1)", "Unsafe"))
            assertFalse(repository.add("https://missing-folder.test", "Missing", "unknown"))
            assertTrue(repository.add("https://example.test/docs", "  Docs  ", createdAt = 10L))
            assertFalse(repository.add("https://example.test/docs", "Duplicate", createdAt = 11L))
            assertEquals("Docs", repository.find("https://example.test/docs")?.title)
            assertTrue(repository.move("https://example.test/docs", "work"))
            assertEquals("work", repository.find("https://example.test/docs")?.folderId)
            assertFalse(repository.move("https://example.test/docs", "deleted-folder"))
            assertEquals(1, repository.all().size)
            assertTrue(repository.remove("https://example.test/docs"))
            assertFalse(repository.remove("https://example.test/docs"))
            assertNull(repository.find("https://example.test/docs"))

            val longTitle = "x".repeat(200)
            assertTrue(repository.add("https://example.test/long-title", longTitle, createdAt = 12L))
            assertEquals(160, repository.find("https://example.test/long-title")?.title?.length)
        } finally {
            database.close()
        }
    }
}
