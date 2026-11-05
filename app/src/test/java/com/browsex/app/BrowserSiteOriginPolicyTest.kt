package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserSiteOriginPolicyTest {
    @Test fun stripsPathQueryAndFragment() {
        assertEquals(
            "https://example.com",
            BrowserSiteOriginPolicy.canonicalOrigin("https://example.com/a/b?q=1#section")
        )
    }

    @Test fun normalizesSchemeHostAndDefaultPorts() {
        assertEquals("https://example.com", BrowserSiteOriginPolicy.canonicalOrigin("HTTPS://EXAMPLE.COM:443/path"))
        assertEquals("http://example.com", BrowserSiteOriginPolicy.canonicalOrigin("HTTP://Example.com:80"))
        assertEquals("https://example.com:8443", BrowserSiteOriginPolicy.canonicalOrigin("https://Example.com:8443"))
    }

    @Test fun rejectsCredentialsAndNonWebSchemes() {
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("https://user:secret@example.com/path"))
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("javascript:alert(1)"))
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("file:///tmp/page.html"))
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("https:///missing-host"))
    }

    @Test fun rejectsMalformedAndOutOfRangePorts() {
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("https://example.com:99999"))
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("not a url"))
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin(null))
    }
}
