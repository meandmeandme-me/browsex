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
        fun resolve(context: Context, settings: BrowserSettings): BrowseXColors {
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = when (settings.themeMode) { "dark" -> true; "light" -> false; else -> night }
            val base = if (dark) BrowseXColors(
                // Deep navy surfaces with translucent glass layers for the browser dock.
                0xFF080F1E.toInt(), 0xD91A2940.toInt(), 0xE51E2D46.toInt(), 0xD91B2A41.toInt(),
                0xFFF4F7FF.toInt(), 0xFF9DAFC8.toInt(), 0xFF78A9FF.toInt(), 0x665F7898.toInt(),
                0xFFFF929B.toInt(), 0xFF79D7AE.toInt(), 0xFF2D426A.toInt(), true
            ) else BrowseXColors(
                // Cool neutral canvas, pearl glass, and a clearer blue accent.
                0xFFF4F7FC.toInt(), 0xDDFDFFFF.toInt(), 0xF0FFFFFF.toInt(), 0xEFFFFFFF.toInt(),
                0xFF151B2D.toInt(), 0xFF667085.toInt(), 0xFF4263EB.toInt(), 0x66A8B8D0.toInt(),
                0xFFBE414B.toInt(), 0xFF1C7D53.toInt(), 0xFFE5EDFF.toInt(), false
            )

            val accentPair = when (settings.accentColor) {
                "violet" -> if (dark) 0xFFB8A5FF.toInt() to 0xFF40336A.toInt() else 0xFF6844D6.toInt() to 0xFFECE5FF.toInt()
                "emerald" -> if (dark) 0xFF75DDB0.toInt() to 0xFF244E43.toInt() else 0xFF16835A.toInt() to 0xFFDDF5E9.toInt()
                "rose" -> if (dark) 0xFFFF9DB8.toInt() to 0xFF5B3043.toInt() else 0xFFC13F70.toInt() to 0xFFFFE5EE.toInt()
                "amber" -> if (dark) 0xFFFFD477.toInt() to 0xFF594627.toInt() else 0xFFA66A00.toInt() to 0xFFFFF0D1.toInt()
                else -> base.accent to base.selected
            }

            // Accessibility preferences apply to every native page, not just the main dock.
            // Reduced transparency removes alpha layers; high contrast also strengthens text,
            // borders and selected states. Keep semantic colors and the base BrowseX identity.
            val opaqueSurfaces = settings.reduceTransparency || settings.highContrast
            return base.copy(
                panel = if (opaqueSurfaces) {
                    if (dark) 0xFF17283E.toInt() else 0xFFFFFFFF.toInt()
                } else base.panel,
                card = if (opaqueSurfaces) {
                    if (dark) 0xFF1E324B.toInt() else 0xFFFFFFFF.toInt()
                } else base.card,
                field = if (opaqueSurfaces) {
                    if (dark) 0xFF101F32.toInt() else 0xFFF0F4FA.toInt()
                } else base.field,
                ink = if (settings.highContrast) {
                    if (dark) 0xFFFFFFFF.toInt() else 0xFF080D18.toInt()
                } else base.ink,
                muted = if (settings.highContrast) {
                    if (dark) 0xFFE0E9F5.toInt() else 0xFF39465A.toInt()
                } else base.muted,
                accent = if (settings.highContrast) {
                    if (dark) 0xFFB5D4FF.toInt() else 0xFF164FAF.toInt()
                } else accentPair.first,
                line = if (settings.highContrast) {
                    if (dark) 0xFFB2C5DD.toInt() else 0xFF4A5A70.toInt()
                } else base.line,
                selected = if (settings.highContrast) {
                    if (dark) 0xFF34547D.toInt() else 0xFFD3E2FF.toInt()
                } else accentPair.second
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
