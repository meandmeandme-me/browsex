package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePresentationPolicyTest {

    @Test
    fun compactPhoneUsesBottomAddressAndTintedGlassWhenBlurIsUnavailable() {
        val result = AdaptivePresentationPolicy.resolve(snapshot())

        assertEquals(WrenWindowLayout.PHONE_COMPACT, result.windowLayout)
        assertEquals(WrenAddressPlacement.BOTTOM, result.addressPlacement)
        assertEquals(WrenMaterialMode.GLASS_TINTED, result.materialMode)
        assertTrue(result.reasonCodes.contains("blur-fallback"))
    }

    @Test
    fun apiAvailabilityAloneDoesNotEnableBackdropBlur() {
        val result = AdaptivePresentationPolicy.resolve(
            snapshot(
                sdkInt = 35,
                isHardwareAccelerated = true,
                blurCurrentlyEnabled = true,
                hasVerifiedBackdropBlurPath = false,
                ramTier = DeviceRamTier.HIGH
            )
        )

        assertEquals(WrenMaterialMode.GLASS_TINTED, result.materialMode)
    }

    @Test
    fun verifiedBlurIsAllowedOnlyOnCapableUnpressuredHighTierDevice() {
        val result = AdaptivePresentationPolicy.resolve(
            snapshot(
                sdkInt = 35,
                isHardwareAccelerated = true,
                blurCurrentlyEnabled = true,
                hasVerifiedBackdropBlurPath = true,
                ramTier = DeviceRamTier.HIGH
            )
        )

        assertEquals(WrenMaterialMode.GLASS_BLUR, result.materialMode)
        assertTrue(result.allowNonEssentialAnimation)
    }

    @Test
    fun batterySaverKeepsTintedGlassAndReducesMotion() {
        val result = AdaptivePresentationPolicy.resolve(
            snapshot(
                sdkInt = 35,
                isHardwareAccelerated = true,
                blurCurrentlyEnabled = true,
                hasVerifiedBackdropBlurPath = true,
                ramTier = DeviceRamTier.HIGH,
                batterySaverEnabled = true
            )
        )

        assertEquals(WrenMaterialMode.GLASS_TINTED, result.materialMode)
        assertTrue(result.reduceMotion)
        assertFalse(result.allowNonEssentialAnimation)
        assertFalse(result.allowSpeculativeVisualWork)
        assertTrue(result.reasonCodes.contains("battery-saver"))
    }

    @Test
    fun constrainedMemoryDisablesExpensiveVisualWorkWithoutBreakingGlassIdentity() {
        val result = AdaptivePresentationPolicy.resolve(
            snapshot(ramTier = DeviceRamTier.CONSTRAINED)
        )

        assertEquals(WrenMaterialMode.GLASS_TINTED, result.materialMode)
        assertTrue(result.reduceMotion)
        assertFalse(result.allowSpeculativeVisualWork)
    }

    @Test
    fun measuredJankSelectsMinimalMode() {
        val result = AdaptivePresentationPolicy.resolve(
            snapshot(measuredJankPressure = true)
        )

        assertEquals(WrenMaterialMode.PERFORMANCE_MINIMAL, result.materialMode)
        assertFalse(result.allowNonEssentialAnimation)
    }

    @Test
    fun accessibilityOverridesBlurAndVisualPerformanceTier() {
        val highContrast = AdaptivePresentationPolicy.resolve(
            snapshot(
                sdkInt = 35,
                isHardwareAccelerated = true,
                blurCurrentlyEnabled = true,
                hasVerifiedBackdropBlurPath = true,
                ramTier = DeviceRamTier.HIGH,
                systemHighContrast = true
            )
        )
        val reducedTransparency = AdaptivePresentationPolicy.resolve(
            snapshot(reduceTransparency = true)
        )

        assertEquals(WrenMaterialMode.HIGH_CONTRAST, highContrast.materialMode)
        assertEquals(WrenMaterialMode.HIGH_CONTRAST, reducedTransparency.materialMode)
    }

    @Test
    fun mediumAndExpandedWindowsDoNotUsePhoneBottomDockByDefault() {
        val medium = AdaptivePresentationPolicy.resolve(snapshot(windowWidthDp = 700))
        val expanded = AdaptivePresentationPolicy.resolve(snapshot(windowWidthDp = 1000))

        assertEquals(WrenWindowLayout.MEDIUM, medium.windowLayout)
        assertEquals(WrenAddressPlacement.TOP, medium.addressPlacement)
        assertEquals(WrenWindowLayout.EXPANDED, expanded.windowLayout)
        assertEquals(WrenAddressPlacement.TOP, expanded.addressPlacement)
    }

    @Test
    fun extraLargeWindowCanUseSideAddressPlacement() {
        val result = AdaptivePresentationPolicy.resolve(snapshot(windowWidthDp = 1400))

        assertEquals(WrenWindowLayout.LARGE, result.windowLayout)
        assertEquals(WrenAddressPlacement.SIDE, result.addressPlacement)
    }

    @Test
    fun compactLandscapeUsesShortToolbarPlacement() {
        val result = AdaptivePresentationPolicy.resolve(
            snapshot(windowWidthDp = 420, windowHeightDp = 430)
        )

        assertEquals(WrenWindowLayout.COMPACT_LANDSCAPE, result.windowLayout)
        assertEquals(WrenAddressPlacement.TOP, result.addressPlacement)
    }

    @Test
    fun tvUsesRemoteFirstLayoutRegardlessOfWindowWidth() {
        val result = AdaptivePresentationPolicy.resolve(
            snapshot(
                windowWidthDp = 1920,
                windowHeightDp = 1080,
                isLeanbackDevice = true,
                hasTouchscreen = false,
                hasDpad = true
            )
        )

        assertEquals(WrenWindowLayout.TV, result.windowLayout)
        assertEquals(WrenAddressPlacement.TV_REMOTE, result.addressPlacement)
    }

    @Test
    fun userCanChooseTopBarOnPhone() {
        val result = AdaptivePresentationPolicy.resolve(
            snapshot(addressBarPreference = AddressBarPreference.TOP)
        )

        assertEquals(WrenAddressPlacement.TOP, result.addressPlacement)
    }

    private fun snapshot(
        sdkInt: Int = 29,
        windowWidthDp: Int = 411,
        windowHeightDp: Int = 850,
        isLeanbackDevice: Boolean = false,
        hasTouchscreen: Boolean = true,
        hasDpad: Boolean = false,
        isHardwareAccelerated: Boolean = false,
        hasVerifiedBackdropBlurPath: Boolean = false,
        blurCurrentlyEnabled: Boolean = false,
        ramTier: DeviceRamTier = DeviceRamTier.BALANCED,
        batterySaverEnabled: Boolean = false,
        thermalStatus: Int = 0,
        systemHighContrast: Boolean = false,
        reduceTransparency: Boolean = false,
        reduceMotion: Boolean = false,
        measuredJankPressure: Boolean = false,
        addressBarPreference: AddressBarPreference = AddressBarPreference.AUTO
    ) = PresentationCapabilitySnapshot(
        sdkInt = sdkInt,
        windowWidthDp = windowWidthDp,
        windowHeightDp = windowHeightDp,
        isLeanbackDevice = isLeanbackDevice,
        hasTouchscreen = hasTouchscreen,
        hasDpad = hasDpad,
        isHardwareAccelerated = isHardwareAccelerated,
        hasVerifiedBackdropBlurPath = hasVerifiedBackdropBlurPath,
        blurCurrentlyEnabled = blurCurrentlyEnabled,
        ramTier = ramTier,
        batterySaverEnabled = batterySaverEnabled,
        thermalStatus = thermalStatus,
        systemHighContrast = systemHighContrast,
        reduceTransparency = reduceTransparency,
        reduceMotion = reduceMotion,
        measuredJankPressure = measuredJankPressure,
        addressBarPreference = addressBarPreference
    )
}
