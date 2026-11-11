package com.browsex.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFileNamePolicyTest {
    @Test fun removesPathSeparatorsAndControlCharacters() {
        assertEquals("_folder_name_evil.txt", DownloadFileNamePolicy.sanitize("../folder/name|evil.txt\u0000"))
    }

    @Test fun preservesReadableUnicodeNames() {
        assertEquals("ملف تجريبي.pdf", DownloadFileNamePolicy.sanitize("ملف تجريبي.pdf"))
    }

    @Test fun removesBidiOverridesAndTrimsUnsafeEdges() {
        assertEquals("report.exe", DownloadFileNamePolicy.sanitize("..report\u202E.exe..."))
    }

    @Test fun boundsLengthAndProvidesFallback() {
        assertEquals(140, DownloadFileNamePolicy.sanitize("a".repeat(200)).length)
        assertEquals("download", DownloadFileNamePolicy.sanitize("..."))
        assertEquals("download", DownloadFileNamePolicy.sanitize(null))
    }

    @Test fun reservesUniqueDestinationWithoutOverwritingExistingFile() {
        val directory = createTempDir(prefix = "browsex-download-")
        try {
            val original = File(directory, "report.txt")
            assertTrue(original.writeText("keep me").let { original.exists() })

            val reserved = reserveUniqueDownloadFile(directory, "report.txt")

            assertEquals("report (1).txt", reserved.name)
            assertTrue(reserved.exists())
            assertEquals(0L, reserved.length())
            assertEquals("keep me", original.readText())
            assertFalse(reserveUniqueDownloadFile(directory, "report.txt") == reserved)
        } finally {
            directory.deleteRecursively()
        }
    }
}
