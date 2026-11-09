package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserDownloadStoreTest {
    private lateinit var store: BrowserDownloadStore

    @Before
    fun setUp() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("browser-download-store-test", 0)
        preferences.edit().clear().commit()
        store = BrowserDownloadStore(preferences)
    }

    @Test
    fun terminalDownloadStateCannotBeOverwrittenByLateCallbacks() {
        val id = store.beginStreamDownload("report.pdf", "application/pdf")
        store.updateStreamDownloadProgress(id, 120L)
        store.completeStreamDownload(id, "content://downloads/report", 180L)
        store.failStreamDownload(id, "late failure")
        store.updateStreamDownloadProgress(id, 240L)

        val record = store.streamDownloads().single()
        assertEquals("completed", record.status)
        assertEquals("content://downloads/report", record.uri)
        assertEquals(180L, record.bytesWritten)
        assertTrue(!record.failed)
    }

    @Test
    fun completionRejectsUnsafeUrisAndNegativeProgress() {
        val id = store.beginStreamDownload("report.pdf", "application/pdf")
        store.updateStreamDownloadProgress(id, -1L)
        store.completeStreamDownload(id, "https://example.com/file.pdf", 10L)

        val record = store.streamDownloads().single()
        assertEquals("queued", record.status)
        assertEquals(0L, record.bytesWritten)
        assertEquals("", record.uri)
    }

    @Test
    fun cancellationIsTerminalAndKeepsMonotonicProgress() {
        val id = store.beginStreamDownload("video.mp4", "video/mp4")
        store.updateStreamDownloadProgress(id, 50L)
        store.failStreamDownload(id, "Cancelled by user", cancelled = true, bytesWritten = 70L)
        store.failStreamDownload(id, "late error", bytesWritten = 90L)

        val record = store.streamDownloads().single()
        assertEquals("cancelled", record.status)
        assertEquals(70L, record.bytesWritten)
        assertTrue(!record.failed)
    }
}
