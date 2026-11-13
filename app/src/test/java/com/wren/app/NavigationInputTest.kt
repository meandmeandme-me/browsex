package com.wren.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationInputTest {
    private val home = "resource://android/assets/home.html"

    @Test fun blankInputOpensHome() {
        assertEquals(home, NavigationInput.normalize("  ", "Google", home))
    }

    @Test fun preservesValidHttpAndHttpsUrls() {
        assertEquals("https://example.com/path?q=hello", NavigationInput.normalize("https://example.com/path?q=hello", "Google", home))
        assertEquals("http://localhost:8080", NavigationInput.normalize("http://localhost:8080", "Google", home))
    }

    @Test fun addsHttpsToHostLikeInput() {
        assertEquals("https://example.com", NavigationInput.normalize("example.com", "Google", home))
        assertEquals("https://localhost", NavigationInput.normalize("localhost", "Google", home))
    }

    @Test fun searchesOrdinaryTextAndEncodesIt() {
        assertEquals("https://www.google.com/search?q=hello%20world", NavigationInput.normalize("hello world", "Google", home))
        assertEquals("https://www.bing.com/search?q=hello%20world", NavigationInput.normalize("hello world", "Bing", home))
        assertEquals("https://html.duckduckgo.com/html/?q=hello%20world", NavigationInput.normalize("hello world", "DuckDuckGo", home))
    }

    @Test fun supportsAdditionalPrivacyFocusedAndRegionalSearchProviders() {
        assertEquals("https://search.brave.com/search?q=privacy%20browser", NavigationInput.normalize("privacy browser", "Brave", home))
        assertEquals("https://www.ecosia.org/search?q=privacy%20browser", NavigationInput.normalize("privacy browser", "Ecosia", home))
        assertEquals("https://www.startpage.com/sp/search?query=privacy%20browser", NavigationInput.normalize("privacy browser", "Startpage", home))
        assertEquals("https://search.yahoo.com/search?p=privacy%20browser", NavigationInput.normalize("privacy browser", "Yahoo", home))
    }

    @Test fun dangerousSchemesAreSearchTextNotNavigated() {
        val result = NavigationInput.normalize("javascript:alert(1)", "Google", home)
        assertTrue(result.startsWith("https://www.google.com/search?q="))
        assertFalse(result.startsWith("javascript:"))
        assertTrue(NavigationInput.normalize("file:///etc/passwd", "Google", home).startsWith("https://www.google.com/search?q="))
    }

    @Test fun rejectsMalformedAndCredentialBearingWebUrls() {
        assertFalse(NavigationInput.isValidWebUrl("https:///missing-host"))
        assertFalse(NavigationInput.isValidWebUrl("https://user:pass@example.com"))
        assertFalse(NavigationInput.isValidWebUrl("javascript:alert(1)"))
    }

    @Test fun externalIntentsOnlyAcceptCredentialFreeHttpAndHttpsUrls() {
        assertEquals("https://example.com/path", NavigationInput.externalWebUrl("https://example.com/path"))
        assertEquals("http://localhost:8080", NavigationInput.externalWebUrl("http://localhost:8080"))
        assertEquals(null, NavigationInput.externalWebUrl("javascript:alert(1)"))
        assertEquals(null, NavigationInput.externalWebUrl("file:///etc/passwd"))
        assertEquals(null, NavigationInput.externalWebUrl("https://user:pass@example.com"))
        assertEquals(null, NavigationInput.externalWebUrl("https:///missing-host"))
    }

    @Test fun onlyAllowsTheKnownInternalHomePage() {
        assertEquals(home, NavigationInput.normalize(home, "Google", home))
        assertEquals("about:blank", NavigationInput.normalize("about:blank", "Google", home))
        assertTrue(NavigationInput.normalize("resource://other/private", "Google", home).startsWith("https://www.google.com/search?q="))
    }
}
