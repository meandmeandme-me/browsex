package com.wren.app

internal enum class DeviceRamTier { CONSTRAINED, BALANCED, HIGH }
internal enum class DeviceScreenClass { COMPACT, LARGE, EXTRA_LARGE }

internal data class DeviceCapabilityProfile(
    val sdkInt: Int,
    val supportedAbis: List<String>,
    val totalRamMb: Int?,
    val appMemoryClassMb: Int,
    val isLowRamDevice: Boolean,
    val screenClass: DeviceScreenClass,
    val smallestWidthDp: Int,
    val densityDpi: Int,
    val ramTier: DeviceRamTier
)

internal data class RuntimePressure(
    val batterySaverEnabled: Boolean = false,
    /** Android PowerManager thermal status: 0 is none, higher values are hotter. */
    val thermalStatus: Int = 0
) {
    init { require(thermalStatus >= 0) }
}

internal data class AdaptiveRuntimeConfig(
    val maxLiveGeckoSessions: Int,
    val maxParallelResourceLoads: Int,
    val allowSpeculativeTabPreload: Boolean,
    val preferReducedMotion: Boolean,
    val useCompactMemoryMode: Boolean
)

/**
 * Pure policy: device facts and transient system pressure are inputs, never scattered
 * Build or PowerManager checks throughout browser features.
 */
internal object AdaptiveRuntimePolicy {
    fun resolve(
        profile: DeviceCapabilityProfile,
        pressure: RuntimePressure = RuntimePressure()
    ): AdaptiveRuntimeConfig {
        val base = when (profile.ramTier) {
            DeviceRamTier.CONSTRAINED -> AdaptiveRuntimeConfig(1, 1, false, true, true)
            DeviceRamTier.BALANCED -> AdaptiveRuntimeConfig(2, 2, false, false, false)
            DeviceRamTier.HIGH -> AdaptiveRuntimeConfig(4, 4, true, false, false)
        }

        // Thermal pressure takes priority over battery saver. Never trade security for speed.
        val severeThermal = pressure.thermalStatus >= THERMAL_SEVERE
        val moderateThermal = pressure.thermalStatus >= THERMAL_MODERATE
        val reduceForPressure = pressure.batterySaverEnabled || moderateThermal
        if (!reduceForPressure) return base

        val sessionLimit = when {
            severeThermal -> 1
            else -> minOf(base.maxLiveGeckoSessions, 2)
        }
        return base.copy(
            maxLiveGeckoSessions = sessionLimit,
            maxParallelResourceLoads = minOf(base.maxParallelResourceLoads, sessionLimit),
            allowSpeculativeTabPreload = false,
            preferReducedMotion = base.preferReducedMotion || pressure.batterySaverEnabled,
            useCompactMemoryMode = base.useCompactMemoryMode || severeThermal
        )
    }

    private const val THERMAL_MODERATE = 2
    private const val THERMAL_SEVERE = 4
}
