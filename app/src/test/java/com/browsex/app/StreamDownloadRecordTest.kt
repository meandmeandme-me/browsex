package com.browsex.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamDownloadRecordTest {
    private fun record(status: String) = StreamDownloadRecord(
        id = "stream_download_test",
        displayName = "sample.bin",
        uri = "",
        mimeType = "application/octet-stream",
        bytesWritten = 0L,
        createdAt = 1L,
        status = status
    )

    @Test
    fun queuedAndRunningRecordsAreActive() {
        assertTrue(record("queued").isActive)
        assertTrue(record("running").isActive)
    }

    @Test
    fun terminalRecordsAreNotActive() {
        assertFalse(record("completed").isActive)
        assertFalse(record("failed").isActive)
        assertFalse(record("cancelled").isActive)
    }

    @Test
    fun failureStatusIsRecognizedEvenWhenLegacyBooleanIsNotSet() {
        assertTrue(record("failed").isFailed)
        assertFalse(record("completed").isFailed)
    }

    @Test
    fun cancellationIsDistinctFromFailure() {
        assertTrue(record("cancelled").isCancelled)
        assertFalse(record("failed").isCancelled)
        assertFalse(record("cancelled").isFailed)
    }
}
