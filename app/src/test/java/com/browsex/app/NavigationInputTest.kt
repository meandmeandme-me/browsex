package com.browsex.app

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

    @Test fun onlyAllowsTheKnownInternalHomePage() {
        assertEquals(home, NavigationInput.normalize(home, "Google", home))
        assertEquals("about:blank", NavigationInput.normalize("about:blank", "Google", home))
        assertTrue(NavigationInput.normalize("resource://other/private", "Google", home).startsWith("https://www.google.com/search?q="))
    }
}
