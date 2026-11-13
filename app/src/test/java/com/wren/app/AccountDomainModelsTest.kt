package com.wren.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDomainModelsTest {
    @Test
    fun accountIdentityUsesStableProviderSubject() {
        val identity = AccountIdentity(
            provider = IdentityProvider.GOOGLE,
            providerSubject = "provider-subject-123",
            email = "person@example.test"
        )

        assertEquals("provider-subject-123", identity.providerSubject)
        assertEquals(IdentityProvider.GOOGLE, identity.provider)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankProviderSubjectIsRejected() {
        AccountIdentity(
            provider = IdentityProvider.OPENID_CONNECT,
            providerSubject = " "
        )
    }

    @Test
    fun syncCategoriesDoNotExposePrivateDataOrCredentials() {
        val categories = SyncCategory.values().toSet()

        assertTrue(categories.contains(SyncCategory.BOOKMARKS))
        assertTrue(categories.contains(SyncCategory.BROWSER_PREFERENCES))
        assertFalse(categories.any { it.name.contains("PRIVATE") })
        assertFalse(categories.any { it.name.contains("COOKIE") })
        assertFalse(categories.any { it.name.contains("PASSWORD") })
        assertFalse(categories.any { it.name.contains("TOKEN") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSyncConsentVersionIsRejected() {
        SyncConsentEntry(
            category = SyncCategory.BOOKMARKS,
            enabled = true,
            updatedAtEpochMillis = 1L,
            policyVersion = 0
        )
    }
}
