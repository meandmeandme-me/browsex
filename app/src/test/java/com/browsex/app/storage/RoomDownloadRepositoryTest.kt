package com.browsex.app.storage

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomDownloadRepositoryTest {
    @Test
    fun downloadLifecycleRejectsInvalidTransitionsAndRecoversInterruptedWork() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomDownloadRepository(database.storageDao())
            val id = repository.beginStream(" video.mp4 ", "video/mp4", 100L, 1L)
            assertNotNull(id)
            assertFalse(repository.complete(id!!, "", 10L))
            assertFalse(repository.complete(id, "javascript:alert(1)", 10L))
            assertTrue(repository.markRunning(id))
            assertTrue(repository.updateProgress(id, 40L))
            assertFalse(repository.updateProgress(id, 20L))
            assertEquals(40L, repository.all().single().bytesWritten)
            assertEquals(1, repository.recoverInterrupted())
            assertEquals("failed", repository.all().single().status)
            assertFalse(repository.complete(id, "content://downloads/item", 100L))
            assertTrue(repository.remove(id))
            assertFalse(repository.remove(id))
        } finally {
            database.close()
        }
    }

    @Test
    fun completedDownloadsAndReservationsRemainUniqueAndBounded() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomDownloadRepository(database.storageDao())
            val first = repository.reserveFileName("video.mp4", 1L)
            val second = repository.reserveFileName("video.mp4", 2L)
            assertEquals("video.mp4", first)
            assertEquals("video (1).mp4", second)
            assertNotEquals(first, second)
            assertEquals("_private.txt", repository.reserveFileName("../private.txt", 3L))

            val id = repository.beginStream("video.mp4", "video/mp4", createdAt = 3L)!!
            assertTrue(repository.markRunning(id))
            assertTrue(repository.complete(id, "content://download/video", 99L))
            assertFalse(repository.fail(id, "late failure"))
            assertFalse(repository.cancel(id))
            assertFalse(repository.markRunning(id))
            assertFalse(repository.updateProgress(id, 120L))
            assertEquals("completed", repository.all().single().status)
            assertEquals(99L, repository.all().single().bytesWritten)
            assertTrue(repository.trackPlatformDownload(123L, 4L))
            assertTrue(repository.trackPlatformDownload(123L, 5L))
            assertEquals(setOf(123L), repository.trackedPlatformDownloadIds())
            assertTrue(repository.removeTrackedPlatformDownload(123L))
            assertEquals(emptySet<Long>(), repository.trackedPlatformDownloadIds())
            assertFalse(repository.removeTrackedPlatformDownload(123L))
        } finally {
            database.close()
        }
    }

    @Test
    fun failingOrCancellingDownloadPreservesLastPersistedProgressWhenByteCountIsUnknown() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomDownloadRepository(database.storageDao())
            val failedId = repository.beginStream("partial-failure.bin", "application/octet-stream", createdAt = 1L)!!
            assertTrue(repository.markRunning(failedId))
            assertTrue(repository.updateProgress(failedId, 4096L))
            assertTrue(repository.fail(failedId, "network disconnected"))

            val cancelledId = repository.beginStream("partial-cancel.bin", "application/octet-stream", createdAt = 2L)!!
            assertTrue(repository.markRunning(cancelledId))
            assertTrue(repository.updateProgress(cancelledId, 8192L))
            assertTrue(repository.cancel(cancelledId))

            val records = repository.all().associateBy { it.id }
            assertEquals(4096L, records[failedId]?.bytesWritten)
            assertEquals(8192L, records[cancelledId]?.bytesWritten)
            assertEquals("failed", records[failedId]?.status)
            assertEquals("cancelled", records[cancelledId]?.status)
        } finally {
            database.close()
        }
    }

    @Test
    fun downloadHistoryHasThreeHundredRecordLimit() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), BrowseXDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomDownloadRepository(database.storageDao())
            repeat(301) { index ->
                assertNotNull(repository.beginStream("file-$index.bin", "application/octet-stream", createdAt = index.toLong()))
            }
            assertEquals(300, repository.all().size)
            assertTrue(repository.all().none { it.displayName == "file-0.bin" })
            assertTrue(repository.all().any { it.displayName == "file-300.bin" })
        } finally {
            database.close()
        }
    }
}
