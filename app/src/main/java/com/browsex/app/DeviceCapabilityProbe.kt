package com.browsex.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Reads stable device capabilities once at startup. Runtime memory pressure is handled
 * separately by the host lifecycle coordinator; available RAM is intentionally not used
 * as a permanent device classification signal.
 */
internal class DeviceCapabilityProbe(context: Context) {
    private val appContext = context.applicationContext

    fun snapshot(): DeviceCapabilityProfile {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val totalRamMb = (memoryInfo.totalMem / (1024L * 1024L)).toInt().takeIf { it > 0 }
        val memoryClassMb = activityManager.memoryClass
        val configuration = appContext.resources.configuration
        val smallestWidthDp = configuration.smallestScreenWidthDp
        val screenClass = when {
            smallestWidthDp >= 840 -> DeviceScreenClass.EXTRA_LARGE
            smallestWidthDp >= 600 -> DeviceScreenClass.LARGE
            else -> DeviceScreenClass.COMPACT
        }
        val ramTier = when {
            activityManager.isLowRamDevice || (totalRamMb != null && totalRamMb <= 2_500) ||
                memoryClassMb <= 128 -> DeviceRamTier.CONSTRAINED
            totalRamMb != null && totalRamMb >= 6_000 && memoryClassMb >= 256 ->
                DeviceRamTier.HIGH
            else -> DeviceRamTier.BALANCED
        }

        return DeviceCapabilityProfile(
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            totalRamMb = totalRamMb,
            appMemoryClassMb = memoryClassMb,
            isLowRamDevice = activityManager.isLowRamDevice,
            screenClass = screenClass,
            smallestWidthDp = smallestWidthDp,
            densityDpi = configuration.densityDpi,
            ramTier = ramTier
        )
    }
}
