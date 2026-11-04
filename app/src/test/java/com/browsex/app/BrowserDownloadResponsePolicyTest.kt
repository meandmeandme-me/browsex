package com.browsex.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDownloadResponsePolicyTest {
    @Test fun acceptsOnlySuccessfulHttpResponsesWithBodies() {
        assertTrue(BrowserDownloadResponsePolicy.isSupported("https://example.com/file.zip", 200, true))
        assertTrue(BrowserDownloadResponsePolicy.isSupported("http://example.com/file.zip", 206, true))
        assertFalse(BrowserDownloadResponsePolicy.isSupported("https://example.com/file.zip", 404, true))
        assertFalse(BrowserDownloadResponsePolicy.isSupported("https://example.com/file.zip", 200, false))
    }

    @Test fun rejectsUnsafeOrMalformedUris() {
        assertFalse(BrowserDownloadResponsePolicy.isSupported("file:///sdcard/file.zip", 200, true))
        assertFalse(BrowserDownloadResponsePolicy.isSupported("javascript:/download", 200, true))
        assertFalse(BrowserDownloadResponsePolicy.isSupported("https://user:pass@example.com/file.zip", 200, true))
        assertFalse(BrowserDownloadResponsePolicy.isSupported("not a uri", 200, true))
    }
}
