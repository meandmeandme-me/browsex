package com.browsex.app

import java.io.File
import org.junit.Assert.assertEquals
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
        val directory = java.nio.file.Files.createTempDirectory("browsex-download-").toFile()
        try {
            val original = File(directory, "report.txt")
            original.writeText("keep me")

            val firstReserved = reserveUniqueDownloadFile(directory, "report.txt")
            val secondReserved = reserveUniqueDownloadFile(directory, "report.txt")

            assertEquals("report (1).txt", firstReserved.name)
            assertEquals("report (2).txt", secondReserved.name)
            assertTrue(firstReserved.exists())
            assertTrue(secondReserved.exists())
            assertEquals(0L, firstReserved.length())
            assertEquals("keep me", original.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
