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
    fun bookmarkBackupRoundTripsAndMergesWithoutOverwritingExistingUrls() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val dao = database.storageDao()
            dao.putFolders(listOf(BookmarkFolderEntity(id = "unfiled", name = "Unfiled")))
            val repository = RoomBookmarkRepository(dao)
            assertTrue(repository.add("https://existing.example", "Original", createdAt = 1L))
            assertTrue(repository.add("https://portable.example", "Portable", createdAt = 2L))

            val backup = repository.exportBookmarks()
            val importedDatabase = Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                BrowseXDatabase::class.java
            ).allowMainThreadQueries().build()
            try {
                val importedDao = importedDatabase.storageDao()
                importedDao.putFolders(listOf(BookmarkFolderEntity(id = "unfiled", name = "Unfiled")))
                val importedRepository = RoomBookmarkRepository(importedDao)
                assertTrue(importedRepository.add("https://existing.example", "Keep me", createdAt = 3L))
                val result = importedRepository.importBookmarks(backup)
                assertEquals(1, result.importedBookmarks)
                assertEquals(1, result.skippedBookmarks)
                assertEquals("Keep me", importedRepository.find("https://existing.example")?.title)
                assertEquals("Portable", importedRepository.find("https://portable.example")?.title)
                val secondImport = importedRepository.importBookmarks(backup)
                assertEquals(0, secondImport.importedBookmarks)
            } finally {
                importedDatabase.close()
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun bookmarkBackupMapsFoldersByNameAndRespectsBookmarkCapacity() = runBlocking {
        val sourceDb = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        val targetDb = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val sourceDao = sourceDb.storageDao()
            sourceDao.putFolders(listOf(
                BookmarkFolderEntity("unfiled", "Unfiled"),
                BookmarkFolderEntity("source-work", "Work")
            ))
            val source = RoomBookmarkRepository(sourceDao)
            assertTrue(source.add("https://foldered.example", "Foldered", "source-work", 1L))
            val backup = source.exportBookmarks()

            val targetDao = targetDb.storageDao()
            targetDao.putFolders(listOf(
                BookmarkFolderEntity("unfiled", "Unfiled"),
                BookmarkFolderEntity("local-work", "work")
            ))
            val target = RoomBookmarkRepository(targetDao)
            val result = target.importBookmarks(backup)
            assertEquals(1, result.importedBookmarks)
            assertEquals("local-work", target.find("https://foldered.example")?.folderId)

            repeat(199) { index ->
                assertTrue(target.add("https://capacity-$index.example", "Item $index", createdAt = index + 10L))
            }
            assertEquals(200, target.all().size)
            assertFalse(target.add("https://over-capacity.example", "Too many"))
            val overflow = target.importBookmarks(backup)
            assertEquals(0, overflow.importedBookmarks)
            assertEquals(200, target.all().size)
        } finally {
            sourceDb.close()
            targetDb.close()
        }
    }

    @Test
    fun bookmarkBackupRejectsUnsupportedOrOversizedPayloads() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomBookmarkRepository(database.storageDao())
            assertTrue(runCatching { repository.importBookmarks("""{"format":"other","version":1}""") }.isFailure)
            assertTrue(runCatching { repository.importBookmarks("x".repeat(1_000_001)) }.isFailure)
        } finally {
            database.close()
        }
    }

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

            assertEquals(40, repository.createFolder("N".repeat(60), createdAt = 23L)
                ?.let { id -> repository.folders().first { it.id == id }.name.length })
            // The legacy limit is 50 folders including the protected "unfiled" folder.
            repeat(47) { index ->
                assertTrue(repository.createFolder("Folder $index", createdAt = 30L + index) != null)
            }
            assertNull(repository.createFolder("One too many", createdAt = 100L))
            assertEquals(50, repository.folders().size)
        } finally {
            database.close()
        }
    }

}
