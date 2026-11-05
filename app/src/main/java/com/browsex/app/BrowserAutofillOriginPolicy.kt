package com.browsex.app

/** Binds a system Autofill credential lookup to the origin that owns the password field. */
internal object BrowserAutofillOriginPolicy {
    data class Match(
        val passwordOrigin: String,
        val usernameMatchesPasswordOrigin: Boolean
    )

    fun canonicalOrigin(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() }?.let(CredentialOriginPolicy::canonicalHttpsOrigin)

    fun resolve(passwordDomain: String?, usernameDomain: String?): Match? {
        val passwordOrigin = canonicalOrigin(passwordDomain) ?: return null
        val usernameOrigin = canonicalOrigin(usernameDomain)
        return Match(
            passwordOrigin = passwordOrigin,
            usernameMatchesPasswordOrigin = usernameOrigin == passwordOrigin
        )
    }
}
