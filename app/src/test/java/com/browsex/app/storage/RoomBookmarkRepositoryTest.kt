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
    @Test
    fun bookmarkFoldersSupportSafeLifecycleAndOrdering() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val dao = database.storageDao()
            dao.putFolders(listOf(BookmarkFolderEntity(id = "unfiled", name = "Unfiled")))
            val repository = RoomBookmarkRepository(dao)

            val workId = repository.createFolder("  Work  ", createdAt = 20L)
            val personalId = repository.createFolder("Personal", createdAt = 21L)
            assertTrue(workId != null)
            assertTrue(personalId != null)
            assertNull(repository.createFolder("work", createdAt = 22L))
            assertNull(repository.createFolder("   ", createdAt = 23L))
            assertEquals(40, repository.createFolder("N".repeat(60), createdAt = 23L)
                ?.let { id -> repository.folders().first { it.id == id }.name.length })

            // The legacy limit is 50 folders including the protected "unfiled" folder.
            repeat(46) { index ->
                assertTrue(repository.createFolder("Folder $index", createdAt = 30L + index) != null)
            }
            assertNull(repository.createFolder("One too many", createdAt = 100L))
            assertEquals(50, repository.folders().size)

            assertTrue(repository.add("https://example.test/task", "Task", workId!!, 24L))
            assertTrue(repository.renameFolder(workId, "Projects"))
            assertFalse(repository.renameFolder(personalId!!, "Projects"))
            assertEquals("Projects", repository.folders().first { it.id == workId }.name)

            assertFalse(repository.deleteFolder("unfiled"))
            assertTrue(repository.deleteFolder(workId))
            assertEquals("unfiled", repository.find("https://example.test/task")?.folderId)
            assertFalse(repository.deleteFolder(workId))

            assertTrue(repository.reorderFolders(listOf(personalId, "unfiled")))
            assertEquals(listOf(personalId, "unfiled"), repository.folders().map { it.id })
            assertFalse(repository.reorderFolders(listOf(personalId, personalId)))
            assertFalse(repository.reorderFolders(listOf(personalId)))
        } finally {
            database.close()
        }
    }

}
