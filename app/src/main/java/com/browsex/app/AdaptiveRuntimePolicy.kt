package com.browsex.app

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

internal data class AdaptiveRuntimeConfig(
    val maxLiveGeckoSessions: Int,
    val maxParallelResourceLoads: Int,
    val allowSpeculativeTabPreload: Boolean,
    val preferReducedMotion: Boolean,
    val useCompactMemoryMode: Boolean
)

/**
 * Pure policy: device facts are inputs, never scattered Build checks throughout features.
 * The UI can consume screenClass independently from browser-engine resource decisions.
 */
internal object AdaptiveRuntimePolicy {
    fun resolve(profile: DeviceCapabilityProfile): AdaptiveRuntimeConfig =
        when (profile.ramTier) {
            DeviceRamTier.CONSTRAINED -> AdaptiveRuntimeConfig(
                maxLiveGeckoSessions = 1,
                maxParallelResourceLoads = 1,
                allowSpeculativeTabPreload = false,
                preferReducedMotion = true,
                useCompactMemoryMode = true
            )
            DeviceRamTier.BALANCED -> AdaptiveRuntimeConfig(
                maxLiveGeckoSessions = 2,
                maxParallelResourceLoads = 2,
                allowSpeculativeTabPreload = false,
                preferReducedMotion = false,
                useCompactMemoryMode = false
            )
            DeviceRamTier.HIGH -> AdaptiveRuntimeConfig(
                maxLiveGeckoSessions = 4,
                maxParallelResourceLoads = 4,
                allowSpeculativeTabPreload = true,
                preferReducedMotion = false,
                useCompactMemoryMode = false
            )
        }
}
