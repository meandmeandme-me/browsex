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
        fun resolve(context: Context, store: BrowserStore): BrowseXColors =
            resolve(context, BrowserSettings(
                themeMode = store.themeMode,
                languageMode = store.languageMode
            ))

        fun isArabic(store: BrowserStore): Boolean = when {
            store.languageMode == "system" -> Locale.getDefault().language.equals("ar", ignoreCase = true)
            else -> store.languageMode.substringBefore('-').equals("ar", ignoreCase = true)
        }

        fun isRtl(store: BrowserStore): Boolean {
            val locale = if (store.languageMode == "system") Locale.getDefault() else Locale.forLanguageTag(store.languageMode)
            return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
        }

        fun applyDirection(view: View, store: BrowserStore) {
            view.layoutDirection = if (isRtl(store)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            view.textDirection = View.TEXT_DIRECTION_LOCALE
        }

        fun resolve(context: Context, settings: BrowserSettings): BrowseXColors {
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = when (settings.themeMode) { "dark" -> true; "light" -> false; else -> night }
            return if (dark) BrowseXColors(
                0xFF101820.toInt(), 0xFF182635.toInt(), 0xFF203246.toInt(), 0xFF293D52.toInt(),
                0xFFEAF3FC.toInt(), 0xFFB2C2D2.toInt(), 0xFF83BFFF.toInt(), 0xFF3B5269.toInt(),
                0xFFFF929B.toInt(), 0xFF79D7AE.toInt(), 0xFF294A6B.toInt(), true
            ) else BrowseXColors(
                0xFFEFF5FA.toInt(), 0xFFF3F7FB.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
                0xFF132B40.toInt(), 0xFF53697B.toInt(), 0xFF0866D9.toInt(), 0xFFD7E3EC.toInt(),
                0xFFBE414B.toInt(), 0xFF1C7D53.toInt(), 0xFFE1EFFF.toInt(), false
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
