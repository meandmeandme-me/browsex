package com.browsex.app

import org.junit.Assert.assertEquals
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
}
