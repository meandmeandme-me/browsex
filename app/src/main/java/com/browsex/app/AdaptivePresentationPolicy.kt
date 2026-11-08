package com.browsex.app

/** User choice for where the URL/search field belongs when the current layout allows it. */
internal enum class AddressBarPreference { AUTO, TOP, BOTTOM }

/** Material choices are presentation strategies, not promises about a particular GPU API. */
internal enum class WrenMaterialMode {
    GLASS_BLUR,
    GLASS_TINTED,
    MATTE,
    HIGH_CONTRAST,
    PERFORMANCE_MINIMAL
}

/** Current-window layout classes; these are not physical device/model classifications. */
internal enum class WrenWindowLayout {
    PHONE_COMPACT,
    COMPACT_LANDSCAPE,
    MEDIUM,
    EXPANDED,
    LARGE,
    EXTRA_LARGE,
    TV
}

internal enum class WrenAddressPlacement { TOP, BOTTOM, SIDE, TV_REMOTE }

internal data class PresentationCapabilitySnapshot(
    val sdkInt: Int,
    val windowWidthDp: Int,
    val windowHeightDp: Int,
    val isLeanbackDevice: Boolean = false,
    val hasTouchscreen: Boolean = true,
    val hasDpad: Boolean = false,
    val isHardwareAccelerated: Boolean = false,
    /**
     * True only when a concrete backdrop-blur implementation has been validated
     * for this window/rendering path. API availability alone must never set this.
     */
    val hasVerifiedBackdropBlurPath: Boolean = false,
    /** Runtime availability for the specific blur API used by that implementation. */
    val blurCurrentlyEnabled: Boolean = false,
    val ramTier: DeviceRamTier = DeviceRamTier.BALANCED,
    val batterySaverEnabled: Boolean = false,
    /** Android PowerManager thermal status: 0 is none, larger values mean hotter. */
    val thermalStatus: Int = 0,
    val highContrastEnabled: Boolean = false,
    val reduceTransparency: Boolean = false,
    val reduceMotion: Boolean = false,
    val measuredJankPressure: Boolean = false,
    val severeMemoryPressure: Boolean = false,
    val imeVisible: Boolean = false,
    val addressBarPreference: AddressBarPreference = AddressBarPreference.AUTO
) {
    init {
        require(sdkInt >= 1)
        require(windowWidthDp >= 0)
        require(windowHeightDp >= 0)
        require(thermalStatus >= 0)
    }
}

internal data class AdaptivePresentationConfig(
    val windowLayout: WrenWindowLayout,
    val addressPlacement: WrenAddressPlacement,
    val materialMode: WrenMaterialMode,
    val reduceMotion: Boolean,
    val allowNonEssentialAnimation: Boolean,
    val allowSpeculativeVisualWork: Boolean,
    val showNavigationActions: Boolean,
    val reasonCodes: Set<String>
)

/**
 * Pure UI policy. It does not call Android APIs and it never decides browser security,
 * permissions, or Gecko session budgets.
 *
 * Blur is opt-in only when the exact in-window backdrop implementation has been verified.
 * A recent API level or cross-window-blur flag by itself is insufficient.
 */
internal object AdaptivePresentationPolicy {
    fun resolve(snapshot: PresentationCapabilitySnapshot): AdaptivePresentationConfig {
        val layout = resolveLayout(snapshot)
        val placement = resolveAddressPlacement(snapshot, layout)
        val reasons = linkedSetOf<String>()

        if (snapshot.highContrastEnabled) reasons += "high-contrast"
        if (snapshot.reduceTransparency) reasons += "reduced-transparency"
        if (snapshot.isLeanbackDevice) reasons += "leanback-device"
        if (!snapshot.hasTouchscreen && snapshot.hasDpad) reasons += "remote-input-present"
        if (snapshot.windowWidthDp < 600) reasons += "compact-window"
        if (snapshot.windowHeightDp in 1 until 480) reasons += "compact-height"
        if (snapshot.batterySaverEnabled) reasons += "battery-saver"
        if (snapshot.thermalStatus >= THERMAL_MODERATE) reasons += "thermal-pressure"
        if (snapshot.ramTier == DeviceRamTier.CONSTRAINED) reasons += "constrained-memory"
        if (snapshot.measuredJankPressure) reasons += "measured-jank"
        if (snapshot.severeMemoryPressure) reasons += "system-low-memory"
        if (snapshot.imeVisible) reasons += "ime-visible"

        val severePressure = snapshot.thermalStatus >= THERMAL_SEVERE ||
            snapshot.measuredJankPressure || snapshot.severeMemoryPressure
        val constrained = snapshot.ramTier == DeviceRamTier.CONSTRAINED ||
            snapshot.batterySaverEnabled ||
            snapshot.thermalStatus >= THERMAL_MODERATE || snapshot.severeMemoryPressure

        val material = when {
            snapshot.systemHighContrast -> WrenMaterialMode.HIGH_CONTRAST
            snapshot.reduceTransparency -> WrenMaterialMode.MATTE
            severePressure -> WrenMaterialMode.PERFORMANCE_MINIMAL
            constrained -> WrenMaterialMode.GLASS_TINTED
            canUseVerifiedBlur(snapshot) -> WrenMaterialMode.GLASS_BLUR
            else -> WrenMaterialMode.GLASS_TINTED
        }

        val reduceMotion = snapshot.reduceMotion || constrained || severePressure
        if (snapshot.reduceMotion) reasons += "reduce-motion-preference"
        if (reduceMotion) reasons += "reduced-motion-policy"
        if (!canUseVerifiedBlur(snapshot)) reasons += "blur-fallback"

        return AdaptivePresentationConfig(
            windowLayout = layout,
            addressPlacement = placement,
            materialMode = material,
            reduceMotion = reduceMotion,
            allowNonEssentialAnimation = !reduceMotion,
            allowSpeculativeVisualWork = !constrained && !severePressure && !snapshot.reduceMotion,
            showNavigationActions = !snapshot.imeVisible || layout == WrenWindowLayout.TV,
            reasonCodes = reasons
        )
    }

    private fun resolveLayout(snapshot: PresentationCapabilitySnapshot): WrenWindowLayout {
        if (snapshot.isLeanbackDevice) return WrenWindowLayout.TV
        val width = snapshot.windowWidthDp
        val height = snapshot.windowHeightDp
        if (width < 600 && height in 1 until 480) return WrenWindowLayout.COMPACT_LANDSCAPE
        return when {
            width < 600 -> WrenWindowLayout.PHONE_COMPACT
            width < 840 -> WrenWindowLayout.MEDIUM
            width < 1200 -> WrenWindowLayout.EXPANDED
            width < 1600 -> WrenWindowLayout.LARGE
            else -> WrenWindowLayout.EXTRA_LARGE
        }
    }

    private fun resolveAddressPlacement(
        snapshot: PresentationCapabilitySnapshot,
        layout: WrenWindowLayout
    ): WrenAddressPlacement {
        if (layout == WrenWindowLayout.TV) return WrenAddressPlacement.TV_REMOTE
        return when (snapshot.addressBarPreference) {
            AddressBarPreference.TOP -> WrenAddressPlacement.TOP
            AddressBarPreference.BOTTOM -> if (
                layout == WrenWindowLayout.PHONE_COMPACT ||
                layout == WrenWindowLayout.COMPACT_LANDSCAPE
            ) WrenAddressPlacement.BOTTOM else WrenAddressPlacement.TOP
            AddressBarPreference.AUTO -> when (layout) {
                WrenWindowLayout.PHONE_COMPACT -> WrenAddressPlacement.BOTTOM
                WrenWindowLayout.COMPACT_LANDSCAPE -> WrenAddressPlacement.TOP
                WrenWindowLayout.MEDIUM -> WrenAddressPlacement.TOP
                WrenWindowLayout.EXPANDED -> WrenAddressPlacement.TOP
                WrenWindowLayout.LARGE, WrenWindowLayout.EXTRA_LARGE -> WrenAddressPlacement.SIDE
                WrenWindowLayout.TV -> WrenAddressPlacement.TV_REMOTE
            }
        }
    }

    private fun canUseVerifiedBlur(snapshot: PresentationCapabilitySnapshot): Boolean =
        snapshot.sdkInt >= 31 &&
            snapshot.isHardwareAccelerated &&
            snapshot.hasVerifiedBackdropBlurPath &&
            snapshot.blurCurrentlyEnabled &&
            snapshot.ramTier == DeviceRamTier.HIGH &&
            !snapshot.batterySaverEnabled &&
            snapshot.thermalStatus < THERMAL_MODERATE &&
            !snapshot.systemHighContrast &&
            !snapshot.reduceTransparency &&
            !snapshot.reduceMotion &&
            !snapshot.measuredJankPressure

    private const val THERMAL_MODERATE = 2
    private const val THERMAL_SEVERE = 4
}
