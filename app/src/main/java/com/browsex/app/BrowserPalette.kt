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
                // Firefox-inspired dark surfaces with a clear, low-glare hierarchy.
                0xFF1C1B22.toInt(), 0xFF292830.toInt(), 0xFF35343D.toInt(), 0xFF202027.toInt(),
                0xFFFBFBFE.toInt(), 0xFFB8B7C1.toInt(), 0xFF8CB4FF.toInt(), 0xFF484750.toInt(),
                0xFFFF9AA2.toInt(), 0xFF8BD5A8.toInt(), 0xFF303C54.toInt(), true
            ) else BrowseXColors(
                // Firefox-inspired light surfaces with neutral canvas and accessible blue actions.
                0xFFF5F5F7.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFF1F1F4.toInt(),
                0xFF202124.toInt(), 0xFF5F6368.toInt(), 0xFF0060DF.toInt(), 0xFFDADCE0.toInt(),
                0xFFC5221F.toInt(), 0xFF188038.toInt(), 0xFFE8F0FE.toInt(), false
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
            // borders and selected states. Keep semantic colors and the base Wren identity.
            val opaqueSurfaces = settings.reduceTransparency || settings.highContrast
            return base.copy(
                panel = if (opaqueSurfaces) {
                    if (dark) 0xFF292830.toInt() else 0xFFFFFFFF.toInt()
                } else base.panel,
                card = if (opaqueSurfaces) {
                    if (dark) 0xFF35343D.toInt() else 0xFFFFFFFF.toInt()
                } else base.card,
                field = if (opaqueSurfaces) {
                    if (dark) 0xFF202027.toInt() else 0xFFF1F1F4.toInt()
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
