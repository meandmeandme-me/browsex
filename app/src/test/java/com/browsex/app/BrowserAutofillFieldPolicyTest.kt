package com.browsex.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAutofillFieldPolicyTest {
    @Test
    fun acceptsOnlyUsernameOrEmailAsTheAccountField() {
        assertTrue(BrowserAutofillFieldPolicy.isUsername(listOf("username")))
        assertTrue(BrowserAutofillFieldPolicy.isUsername(listOf("emailAddress")))
        assertFalse(BrowserAutofillFieldPolicy.isUsername(listOf("name")))
        assertFalse(BrowserAutofillFieldPolicy.isUsername(listOf("oneTimeCode")))
    }

    @Test
    fun acceptsExistingPasswordButNotNewPasswordOrOtherSecrets() {
        assertTrue(BrowserAutofillFieldPolicy.isExistingPassword(listOf("password")))
        assertFalse(BrowserAutofillFieldPolicy.isExistingPassword(listOf("newPassword")))
        assertFalse(BrowserAutofillFieldPolicy.isExistingPassword(listOf("creditCardSecurityCode")))
    }
}
