package com.browsex.app

import android.graphics.Color
import com.browsex.app.storage.BrowserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserPaletteTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun reducedTransparencyMakesAllNativeSurfacesOpaqueInLightAndDarkThemes() {
        val light = BrowseXColors.resolve(
            context,
            BrowserSettings(themeMode = "light", reduceTransparency = true)
        )
        val dark = BrowseXColors.resolve(
            context,
            BrowserSettings(themeMode = "dark", reduceTransparency = true)
        )

        listOf(light, dark).forEach { palette ->
            assertEquals(255, Color.alpha(palette.panel))
            assertEquals(255, Color.alpha(palette.card))
            assertEquals(255, Color.alpha(palette.field))
        }
    }

    @Test
    fun highContrastStrengthensNativeTextAndBorderColorsInBothThemes() {
        for (theme in listOf("light", "dark")) {
            val normal = BrowseXColors.resolve(context, BrowserSettings(themeMode = theme))
            val highContrast = BrowseXColors.resolve(
                context,
                BrowserSettings(themeMode = theme, highContrast = true)
            )

            assertEquals(255, Color.alpha(highContrast.line))
            assertNotEquals(normal.line, highContrast.line)
            assertNotEquals(normal.muted, highContrast.muted)
            assertEquals(255, Color.alpha(highContrast.panel))
            assertEquals(255, Color.alpha(highContrast.card))
            assertEquals(255, Color.alpha(highContrast.field))
        }
    }

    @Test
    fun accentColorSelectionIsAppliedAndHighContrastKeepsItsAccessibleOverride() {
        val violet = BrowseXColors.resolve(
            context,
            BrowserSettings(themeMode = "light", accentColor = "violet")
        )
        assertEquals(0xFF6844D6.toInt(), violet.accent)
        assertEquals(0xFFECE5FF.toInt(), violet.selected)

        val accessible = BrowseXColors.resolve(
            context,
            BrowserSettings(themeMode = "light", accentColor = "violet", highContrast = true)
        )
        assertEquals(0xFF164FAF.toInt(), accessible.accent)
        assertEquals(0xFFD3E2FF.toInt(), accessible.selected)
    }

    @Test
    fun standardPaletteUsesTheNewNeutralOpaqueSurfaceHierarchy() {
        val light = BrowseXColors.resolve(context, BrowserSettings(themeMode = "light"))
        val dark = BrowseXColors.resolve(context, BrowserSettings(themeMode = "dark"))

        assertEquals(0xFFF5F5F7.toInt(), light.canvas)
        assertEquals(0xFFFFFFFF.toInt(), light.panel)
        assertEquals(0xFFFFFFFF.toInt(), light.card)
        assertEquals(0xFFF1F1F4.toInt(), light.field)
        assertEquals(0xFF1C1B22.toInt(), dark.canvas)
        assertEquals(0xFF292830.toInt(), dark.panel)
        assertEquals(0xFF35343D.toInt(), dark.card)
        assertEquals(0xFF202027.toInt(), dark.field)

        listOf(light, dark).forEach { palette ->
            assertEquals(255, Color.alpha(palette.panel))
            assertEquals(255, Color.alpha(palette.card))
            assertEquals(255, Color.alpha(palette.field))
        }
    }
}
