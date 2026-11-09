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
        if (id == 0) return fallback

        val languageMode = (context as? MainActivity)?.currentBrowserSettings?.languageMode
            ?.takeIf { it.isNotBlank() && it != "system" }
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
        return runCatching { localizedContext.getString(id) }.getOrDefault(fallback)
    }
}
