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

    @Test fun convertsInternationalizedDomainNamesToIdna() {
        assertEquals(
            "https://xn--mgbh0fb.xn--kgbechtv",
            BrowserSiteOriginPolicy.canonicalOrigin("https://مثال.إختبار/صفحة")
        )
    }

    @Test fun preservesBracketedIpv6Origins() {
        assertEquals(
            "https://[2001:db8::1]",
            BrowserSiteOriginPolicy.canonicalOrigin("https://[2001:DB8::1]:443/path")
        )
    }

    @Test fun rejectsCredentialsAndNonWebSchemes() {
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("https://user:secret@example.com/path"))
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("javascript:alert(1)"))
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("file:///tmp/page.html"))
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("https:///missing-host"))
    }

    @Test fun rejectsMalformedAndOutOfRangePorts() {
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("https://example.com:99999"))
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("https://example.com:"))
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin("not a url"))
        assertNull(BrowserSiteOriginPolicy.canonicalOrigin(null))
    }
}
