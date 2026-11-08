package com.browsex.app

import android.content.Context
import android.content.res.Configuration
import android.text.TextUtils
import android.view.View
import java.util.Locale
import com.browsex.app.storage.BrowserSettings

internal data class BrowseXColors(
    val canvas: Int, val panel: Int, val card: Int, val field: Int,
    val ink: Int, val muted: Int, val accent: Int, val line: Int,
    val danger: Int, val success: Int, val selected: Int, val isDark: Boolean
) {
    companion object {
        fun resolve(context: Context, store: BrowserStore): BrowseXColors {
            val settings = (context as? MainActivity)?.currentBrowserSettings ?: BrowserSettings(
                themeMode = store.themeMode,
                languageMode = store.languageMode
            )
            return resolve(context, settings)
        }

        fun isArabic(context: Context, store: BrowserStore): Boolean =
            (context as? MainActivity)?.currentBrowserSettings?.let(::isArabic) ?: isArabic(store)

        fun isArabic(store: BrowserStore): Boolean = when {
            store.languageMode == "system" -> Locale.getDefault().language.equals("ar", ignoreCase = true)
            else -> store.languageMode.substringBefore('-').equals("ar", ignoreCase = true)
        }

        fun isRtl(store: BrowserStore): Boolean {
            val locale = if (store.languageMode == "system") Locale.getDefault() else Locale.forLanguageTag(store.languageMode)
            return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
        }

        fun applyDirection(view: View, store: BrowserStore) {
            val settings = (view.context as? MainActivity)?.currentBrowserSettings ?: BrowserSettings(
                languageMode = store.languageMode
            )
            applyDirection(view, settings)
        }

        fun resolve(context: Context, settings: BrowserSettings): BrowseXColors {
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = when (settings.themeMode) { "dark" -> true; "light" -> false; else -> night }
            return if (dark) BrowseXColors(
                // Deep navy surfaces with translucent glass layers for the bottom browser dock.
                0xFF080F1E.toInt(), 0xD91A2940.toInt(), 0xE51E2D46.toInt(), 0xD91B2A41.toInt(),
                0xFFF4F7FF.toInt(), 0xFF9DAFC8.toInt(), 0xFF78A9FF.toInt(), 0x665F7898.toInt(),
                0xFFFF929B.toInt(), 0xFF79D7AE.toInt(), 0xFF2D426A.toInt(), true
            ) else BrowseXColors(
                // Cool neutral canvas, pearl glass, and a clearer blue accent.
                0xFFF4F7FC.toInt(), 0xDDFDFFFF.toInt(), 0xF0FFFFFF.toInt(), 0xEFFFFFFF.toInt(),
                0xFF151B2D.toInt(), 0xFF667085.toInt(), 0xFF4263EB.toInt(), 0x66A8B8D0.toInt(),
                0xFFBE414B.toInt(), 0xFF1C7D53.toInt(), 0xFFE5EDFF.toInt(), false
            )
        }

        fun isArabic(settings: BrowserSettings): Boolean = when {
            settings.languageMode == "system" -> Locale.getDefault().language.equals("ar", ignoreCase = true)
            else -> settings.languageMode.substringBefore('-').equals("ar", ignoreCase = true)
        }

        /** Layout direction follows the actual locale, independently of translation availability. */
        fun isRtl(settings: BrowserSettings): Boolean {
            val locale = if (settings.languageMode == "system") {
                Locale.getDefault()
            } else {
                Locale.forLanguageTag(settings.languageMode)
            }
            return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
        }

        fun applyDirection(view: View, settings: BrowserSettings) {
            view.layoutDirection = if (isRtl(settings)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            view.textDirection = View.TEXT_DIRECTION_LOCALE
        }
    }
}
