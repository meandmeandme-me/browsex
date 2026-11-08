package com.browsex.app

import java.util.Locale

/** Pure normalization rules for persisted browser preferences. */
internal object BrowserPreferencePolicy {
    private val themes = setOf("system", "light", "dark")
    private val homeLayouts = setOf("grid", "list")
    private val addressBarPositions = setOf("auto", "top", "bottom")

    fun normalizeTheme(value: String?): String =
        value?.takeIf(themes::contains) ?: "system"

    fun normalizeHomeLayout(value: String?): String =
        value?.takeIf(homeLayouts::contains) ?: "grid"

    fun normalizeAddressBarPosition(value: String?): String =
        value?.trim()?.lowercase(Locale.ROOT)?.takeIf(addressBarPositions::contains) ?: "auto"

    fun normalizeLanguageTag(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.equals("system", ignoreCase = true)) return "system"
        if (raw.length !in 2..35 || !raw.matches(Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*"))) return "system"
        val tag = Locale.forLanguageTag(raw).toLanguageTag()
        return tag.takeIf { it.isNotBlank() && it != "und" } ?: "system"
    }
}
