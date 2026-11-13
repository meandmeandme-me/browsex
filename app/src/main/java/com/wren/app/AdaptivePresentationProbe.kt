package com.wren.app

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.os.Build
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
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
        pressure: RuntimePressure,
        measuredJankPressure: Boolean = false,
        imeVisible: Boolean = false,
        addressBarPreference: AddressBarPreference = AddressBarPreference.AUTO,
        reduceTransparency: Boolean = false,
        reduceMotionPreference: Boolean = false,
        highContrastPreference: Boolean = false,
        appMemoryTrimPressure: Boolean = false,
        foldPosture: WrenFoldPosture = WrenFoldPosture.NONE
    ): PresentationCapabilitySnapshot {
        val packageManager = appContext.packageManager
        val accessibility = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? AccessibilityManager
        val hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        val uiModeManager = appContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val leanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
            uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasDpad = hasDpadInput() || leanback
        val memoryInfo = ActivityManager.MemoryInfo().also { info ->
            (appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        }
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
            highContrastEnabled = highContrastPreference || (Build.VERSION.SDK_INT >= 36 &&
                accessibility?.isHighContrastTextEnabled == true),
            // No OEM-neutral Android switch exists; use Wren's persisted preference.
            reduceTransparency = reduceTransparency,
            reduceMotion = reduceMotionPreference ||
                !runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true),
            measuredJankPressure = measuredJankPressure,
            severeMemoryPressure = memoryInfo.lowMemory || appMemoryTrimPressure,
            foldPosture = foldPosture,
            imeVisible = imeVisible,
            addressBarPreference = addressBarPreference
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
