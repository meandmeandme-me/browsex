package com.wren.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSyncContractsTest {
    private fun metadata(id: String = "bookmark-1") = SyncRecordMetadata(
        recordId = id,
        category = SyncCategory.BOOKMARKS,
        schemaVersion = 1,
        revision = 1L,
        operation = SyncOperation.CREATE,
        modifiedAtEpochMillis = 10L,
        originDeviceId = "device-local"
    )

    @Test
    fun encryptedRecordDefensivelyCopiesCiphertext() {
        val source = byteArrayOf(1, 2, 3, 4)
        val record = EncryptedSyncRecord(metadata(), source, SyncPayloadIntegrity.digest(source))
        source[0] = 99

        val firstRead = record.ciphertext
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), firstRead)
        firstRead[1] = 88
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), record.ciphertext)
        assertTrue(SyncPayloadIntegrity.matches(record))
    }

    @Test(expected = IllegalArgumentException::class)
    fun encryptedRecordRejectsDigestMismatch() {
        EncryptedSyncRecord(metadata(), byteArrayOf(1, 2, 3), "0".repeat(64))
    }

    @Test
    fun syncConsentIsOptInAndPolicyVersioned() {
        val initial = SyncConsentPolicy.initialChoices(100L)
        assertEquals(SyncCategory.values().toSet(), initial.map { it.category }.toSet())
        assertTrue(initial.none { it.enabled })
        assertFalse(SyncConsentPolicy.isEnabled(SyncCategory.BOOKMARKS, initial))

        val optedIn = initial.map {
            if (it.category == SyncCategory.BOOKMARKS) it.copy(enabled = true) else it
        }
        assertTrue(SyncConsentPolicy.isEnabled(SyncCategory.BOOKMARKS, optedIn))
        assertFalse(SyncConsentPolicy.isEnabled(SyncCategory.BROWSING_HISTORY, optedIn))
    }

    @Test
    fun conflictingOrOldConsentFailsClosed() {
        val conflicting = listOf(
            SyncConsentEntry(SyncCategory.BOOKMARKS, true, 200L, SyncConsentPolicy.CURRENT_POLICY_VERSION),
            SyncConsentEntry(SyncCategory.BOOKMARKS, false, 200L, SyncConsentPolicy.CURRENT_POLICY_VERSION)
        )
        val oldPolicy = listOf(
            SyncConsentEntry(SyncCategory.BOOKMARKS, true, 300L, SyncConsentPolicy.CURRENT_POLICY_VERSION + 1)
        )
        assertFalse(SyncConsentPolicy.isEnabled(SyncCategory.BOOKMARKS, conflicting))
        assertFalse(SyncConsentPolicy.isEnabled(SyncCategory.BOOKMARKS, oldPolicy))
    }

    @Test
    fun syncBatchSnapshotsCallerOwnedRecordList() {
        val record = EncryptedSyncRecord(
            metadata(),
            byteArrayOf(7),
            SyncPayloadIntegrity.digest(byteArrayOf(7))
        )
        val source = mutableListOf(record)
        val batch = SyncBatch(source, SyncCursor("next"))
        source.clear()

        assertEquals(1, batch.records.size)
        assertEquals("bookmark-1", batch.records.single().metadata.recordId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun syncBatchRejectsDuplicateRecordIds() {
        SyncBatch(
            records = listOf(
                EncryptedSyncRecord(metadata(), byteArrayOf(1), SyncPayloadIntegrity.digest(byteArrayOf(1))),
                EncryptedSyncRecord(metadata(), byteArrayOf(2), SyncPayloadIntegrity.digest(byteArrayOf(2)))
            ),
            nextCursor = SyncCursor(null)
        )
    }
}
