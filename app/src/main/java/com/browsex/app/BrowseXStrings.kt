package com.browsex.app

import android.content.Context
import java.util.Locale

/** Shared resource-backed UI translation lookup with a safe English fallback. */
internal object BrowseXStrings {
    fun text(context: Context, english: String, fallback: String = english): String {
        val key = "ui_" + english.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        val id = context.resources.getIdentifier(key, "string", context.packageName)
        return if (id != 0) runCatching { context.getString(id) }.getOrDefault(fallback) else fallback
    }
}
