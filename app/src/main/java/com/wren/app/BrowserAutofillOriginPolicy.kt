package com.wren.app

/** Binds a system Autofill credential lookup to the origin that owns the password field. */
internal object BrowserAutofillOriginPolicy {
    data class Match(
        val passwordOrigin: String,
        val usernameMatchesPasswordOrigin: Boolean
    )

    fun canonicalOrigin(raw: String?): String? {
        val domain = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val candidate = if (domain.contains("://")) domain else "https://$domain"
        return CredentialOriginPolicy.canonicalHttpsOrigin(candidate)
    }

    fun resolve(passwordDomain: String?, usernameDomain: String?): Match? {
        val passwordOrigin = canonicalOrigin(passwordDomain) ?: return null
        val usernameOrigin = canonicalOrigin(usernameDomain)
        return Match(
            passwordOrigin = passwordOrigin,
            usernameMatchesPasswordOrigin = usernameOrigin == passwordOrigin
        )
    }
}
