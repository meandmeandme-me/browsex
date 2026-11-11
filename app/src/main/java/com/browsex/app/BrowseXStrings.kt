package com.browsex.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Shared resource-backed UI translation lookup with the app's selected locale and English fallback. */
internal object BrowseXStrings {
    fun text(context: Context, english: String, fallback: String = english): String {
        val key = if (english == "Tabs") "ui_tabs_title" else "ui_" + english.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        val id = context.resources.getIdentifier(key, "string", context.packageName)
        val languageMode = (context as? MainActivity)?.currentBrowserSettings?.languageMode
            ?.takeIf { it.isNotBlank() && it != "system" }
        val requestedLanguage = languageMode?.let { Locale.forLanguageTag(it).language }
            ?: context.resources.configuration.locales[0]?.language.orEmpty()
        val safeFallback = fallbackForLanguage(requestedLanguage, english, fallback)
        if (id == 0) return safeFallback

        val localizedContext = if (languageMode == null) {
            context
        } else {
            val locale = Locale.forLanguageTag(languageMode)
            if (locale.language.isBlank()) context else {
                val configuration = Configuration(context.resources.configuration).apply {
                    setLocale(locale)
                }
                context.createConfigurationContext(configuration)
            }
        }
        return runCatching { localizedContext.getString(id) }.getOrDefault(safeFallback)
    }

    /** Missing resource keys fall back to Arabic only in Arabic; all other locales use English. */
    internal fun fallbackForLanguage(languageTag: String?, english: String, arabic: String): String =
        if (Locale.forLanguageTag(languageTag.orEmpty()).language == "ar") arabic else english
}
