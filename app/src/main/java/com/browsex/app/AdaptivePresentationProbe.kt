package com.browsex.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.view.InputDevice
import android.view.accessibility.AccessibilityManager

/**
 * Android adapter that collects presentation facts for the pure AdaptivePresentationPolicy.
 *
 * It intentionally does not claim that Android API 31+ guarantees an in-window backdrop
 * blur implementation. Wren's current native chrome uses tinted translucent surfaces, so
 * [hasVerifiedBackdropBlurPath] remains false until a concrete renderer is implemented,
 * benchmarked and enabled for the exact View/window path.
 */
internal class AdaptivePresentationProbe(context: Context) {
    private val appContext = context.applicationContext

    fun snapshot(
        windowWidthPx: Int,
        windowHeightPx: Int,
        density: Float,
        isHardwareAccelerated: Boolean,
        deviceProfile: DeviceCapabilityProfile,
        pressure: RuntimePressure
    ): PresentationCapabilitySnapshot {
        val packageManager = appContext.packageManager
        val accessibility = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? AccessibilityManager
        val hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        val leanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        val hasDpad = hasDpadInput() || leanback
        val safeDensity = density.takeIf { it > 0f } ?: appContext.resources.displayMetrics.density
        val widthDp = (windowWidthPx / safeDensity).toInt().coerceAtLeast(0)
        val heightDp = (windowHeightPx / safeDensity).toInt().coerceAtLeast(0)

        return PresentationCapabilitySnapshot(
            sdkInt = android.os.Build.VERSION.SDK_INT,
            windowWidthDp = widthDp,
            windowHeightDp = heightDp,
            isLeanbackDevice = leanback,
            hasTouchscreen = hasTouchscreen,
            hasDpad = hasDpad,
            isHardwareAccelerated = isHardwareAccelerated,
            hasVerifiedBackdropBlurPath = false,
            blurCurrentlyEnabled = false,
            ramTier = deviceProfile.ramTier,
            batterySaverEnabled = pressure.batterySaverEnabled,
            thermalStatus = pressure.thermalStatus,
            systemHighContrast = accessibility?.isHighTextContrastEnabled == true,
            // Android does not expose a single reliable reduced-transparency setting
            // across all OEM versions. The app preference can populate this separately.
            reduceTransparency = false,
            reduceMotion = !runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true),
            measuredJankPressure = false
        )
    }

    private fun hasDpadInput(): Boolean {
        return runCatching {
            InputDevice.getDeviceIds().any { id ->
                val device = InputDevice.getDevice(id)
                device != null && !device.isVirtual &&
                    (device.sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
            }
        }.getOrDefault(false)
    }
}
