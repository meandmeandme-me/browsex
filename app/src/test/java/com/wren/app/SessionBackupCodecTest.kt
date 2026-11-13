package com.wren.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionBackupCodecTest {
    @Test
    fun versionedBackupRoundTripsDuplicateTabsAndActiveIndex() {
        val urls = listOf(
            "https://example.com",
            "https://example.com",
            "https://mozilla.org"
        )

        val encoded = SessionBackupCodec.encode("Research", urls, 1)
        val decoded = SessionBackupCodec.decode(encoded)

        assertEquals("Research", decoded.name)
        assertEquals(urls, decoded.urls)
        assertEquals(1, decoded.activeIndex)
    }

    @Test
    fun preservesWrenHomeTabsInBackup() {
        val home = HOME_URL
        val decoded = SessionBackupCodec.decode(
            SessionBackupCodec.encode("Home", listOf(home, "https://example.com"), 0)
        )

        assertEquals(listOf(home, "https://example.com"), decoded.urls)
        assertEquals(0, decoded.activeIndex)
    }

    @Test
    fun activeIndexIsClampedToAvailableTabs() {
        val payload = SessionBackupCodec.encode(
            "Research",
            listOf("https://one.example", "https://two.example"),
            99
        )

        assertEquals(1, SessionBackupCodec.decode(payload).activeIndex)
    }

    @Test
    fun rejectsUnsupportedFormatAndVersion() {
        val wrongFormat = """{"format":"other","version":1,"name":"A","urls":["https://example.com"]}"""
        val wrongVersion = """{"format":"wren-session","version":2,"name":"A","urls":["https://example.com"]}"""

        assertThrows(IllegalArgumentException::class.java) {
            SessionBackupCodec.decode(wrongFormat)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SessionBackupCodec.decode(wrongVersion)
        }
    }

    @Test
    fun rejectsUnsafeSchemesAndOversizedTabLists() {
        val unsafe = """{"format":"wren-session","version":1,"name":"A","urls":["file:///private"]}"""
        assertThrows(IllegalArgumentException::class.java) {
            SessionBackupCodec.decode(unsafe)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SessionBackupCodec.encode(
                "Too many",
                (0..TabSessionState.MAX_TABS).map { "https://$it.example" },
                0
            )
        }
    }

    @Test
    fun importsLegacyNumberedUrlTextAndRemovesDuplicates() {
        val decoded = SessionBackupCodec.decode(
            "1. https://one.example\n2) https://two.example\n3. https://one.example\nfile:///private",
            "Legacy"
        )

        assertEquals("Legacy", decoded.name)
        assertEquals(listOf("https://one.example", "https://two.example"), decoded.urls)
        assertEquals(0, decoded.activeIndex)
    }

    @Test
    fun rejectsOversizedBackupPayloadsBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionBackupCodec.decode("x".repeat(100_001))
        }
    }

    @Test
    fun rejectsEmptyOrNamelessBackups() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionBackupCodec.decode("  ")
        }
        val noName = """{"format":"wren-session","version":1,"urls":["https://example.com"]}"""
        assertThrows(IllegalArgumentException::class.java) {
            SessionBackupCodec.decode(noName)
        }
        assertTrue(SessionBackupCodec.decode(noName, "Fallback").name == "Fallback")
    }
}
