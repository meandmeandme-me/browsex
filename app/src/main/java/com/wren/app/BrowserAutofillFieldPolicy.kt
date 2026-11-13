package com.wren.app

/** Restricts system Autofill to conventional username/email and existing-password fields. */
internal object BrowserAutofillFieldPolicy {
    fun isUsername(hints: Iterable<String>): Boolean =
        hints.any { it == "username" || it == "emailAddress" }

    fun isExistingPassword(hints: Iterable<String>): Boolean =
        hints.any { it == "password" }
}
