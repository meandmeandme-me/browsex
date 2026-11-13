package com.wren.app.storage

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
            RuntimeEnvironment.getApplication(), WrenDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSessionRepository(database.storageDao())
            assertFalse(repository.saveSession("   ", listOf("https://safe.example"), listOf(false), 0))
            assertTrue(repository.saveSession(
                "Research",
                listOf("https://one.example", "file:///private", "https://two.example"),
                listOf(false, false, false),
                1,
                10L
            ))
            val first = repository.sessions().single()
            assertEquals(listOf("https://one.example", "https://two.example"), first.urls)
            assertEquals(1, first.activeIndex)

            assertTrue(repository.saveSession(" research ", listOf("https://replacement.example"), listOf(false), 0, 20L))
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
    fun namedSessionsExcludePrivateTabsAtTheStorageBoundary() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), WrenDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSessionRepository(database.storageDao())
            assertTrue(repository.saveSession(
                "Mixed",
                listOf("https://one.example", "https://private.example", "https://two.example"),
                listOf(false, true, false),
                2,
                30L
            ))

            val saved = repository.sessions().single()
            assertEquals(listOf("https://one.example", "https://two.example"), saved.urls)
            assertEquals(1, saved.activeIndex)

            assertFalse(repository.saveSession(
                "Private only",
                listOf("https://private-only.example"),
                listOf(true),
                0,
                31L
            ))
            assertEquals(listOf("Mixed"), repository.sessions().map { it.name })
            assertEquals(2, database.storageDao().sessionTabCount())
        } finally {
            database.close()
        }
    }

    @Test
    fun clearingAllSavedSessionsRemovesSessionsAndChildTabsAtomically() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), WrenDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSessionRepository(database.storageDao())
            assertTrue(repository.saveSession(
                "First", listOf("https://one.example", "https://two.example"), listOf(false, false), 1, 1L
            ))
            assertTrue(repository.saveSession(
                "Second", listOf("https://three.example"), listOf(false), 0, 2L
            ))
            assertEquals(2, repository.sessions().size)
            assertEquals(3, database.storageDao().sessionTabCount())

            repository.clearAllSessions()

            assertTrue(repository.sessions().isEmpty())
            assertEquals(0, database.storageDao().sessionTabCount())
        } finally {
            database.close()
        }
    }

    @Test
    fun importingSameNamedSessionPreservesOriginalAndAddsUniqueName() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), WrenDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSessionRepository(database.storageDao())
            assertTrue(repository.saveSession(
                "Research", listOf("https://original.example"), listOf(false), 0, 1L
            ))

            val importedName = repository.importSession(
                "research", listOf("https://backup.example"), 0, 2L
            )

            assertEquals("research (2)", importedName)
            val sessions = repository.sessions().associateBy { it.name }
            assertEquals(setOf("Research", "research (2)"), sessions.keys)
            assertEquals(listOf("https://original.example"), sessions["Research"]?.urls)
            assertEquals(listOf("https://backup.example"), sessions["research (2)"]?.urls)
        } finally {
            database.close()
        }
    }

    @Test
    fun importingSessionRespectsTenSessionLimitWithoutReplacingAnySession() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), WrenDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSessionRepository(database.storageDao())
            repeat(10) { index ->
                assertTrue(repository.saveSession("Session $index", listOf("https://$index.example"), listOf(false), 0, index.toLong()))
            }

            assertNull(repository.importSession("Session 0", listOf("https://replacement.example"), 0))
            assertEquals(10, repository.sessions().size)
            assertEquals(
                listOf("https://0.example"),
                repository.sessions().single { it.name == "Session 0" }.urls
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun namedSessionsRespectTenSessionLimit() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), WrenDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repository = RoomSessionRepository(database.storageDao())
            repeat(10) { index ->
                assertTrue(repository.saveSession("Session $index", listOf("https://$index.example"), listOf(false), 0, index.toLong()))
            }
            assertFalse(repository.saveSession("Session 11", listOf("https://eleven.example"), listOf(false), 0))
            assertEquals(10, repository.sessions().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun clearingRecentlyClosedRemovesEveryPersistedEntry() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), WrenDatabase::class.java
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
            RuntimeEnvironment.getApplication(), WrenDatabase::class.java
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
            RuntimeEnvironment.getApplication(), WrenDatabase::class.java
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
