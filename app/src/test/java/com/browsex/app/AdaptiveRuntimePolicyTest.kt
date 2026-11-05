package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRuntimePolicyTest {
    @Test
    fun constrainedDevicesAvoidSpeculativeWorkAndLimitLiveSessions() {
        val config = AdaptiveRuntimePolicy.resolve(profile(DeviceRamTier.CONSTRAINED))

        assertEquals(1, config.maxLiveGeckoSessions)
        assertEquals(1, config.maxParallelResourceLoads)
        assertFalse(config.allowSpeculativeTabPreload)
        assertTrue(config.useCompactMemoryMode)
    }

    @Test
    fun balancedDevicesUseModerateBudgets() {
        val config = AdaptiveRuntimePolicy.resolve(profile(DeviceRamTier.BALANCED))

        assertEquals(2, config.maxLiveGeckoSessions)
        assertEquals(2, config.maxParallelResourceLoads)
        assertFalse(config.allowSpeculativeTabPreload)
    }

    @Test
    fun capableDevicesCanUseHigherBudgetsAndPreload() {
        val config = AdaptiveRuntimePolicy.resolve(profile(DeviceRamTier.HIGH))

        assertEquals(4, config.maxLiveGeckoSessions)
        assertEquals(4, config.maxParallelResourceLoads)
        assertTrue(config.allowSpeculativeTabPreload)
    }

    private fun profile(tier: DeviceRamTier) = DeviceCapabilityProfile(
        sdkInt = 35,
        supportedAbis = listOf("arm64-v8a"),
        totalRamMb = 8_000,
        appMemoryClassMb = 256,
        isLowRamDevice = false,
        screenClass = DeviceScreenClass.COMPACT,
        smallestWidthDp = 411,
        densityDpi = 420,
        ramTier = tier
    )
}
