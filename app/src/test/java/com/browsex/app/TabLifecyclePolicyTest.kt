package com.browsex.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TabLifecyclePolicyTest {
    @Test
    fun keepsActiveSessionAndReleasesLeastRecentlyUsedBackgroundSessions() {
        val candidates = listOf(
            TabLifecyclePolicy.Candidate(index = 0, active = false, opened = true, lastActiveAt = 50L),
            TabLifecyclePolicy.Candidate(index = 1, active = true, opened = true, lastActiveAt = 10L),
            TabLifecyclePolicy.Candidate(index = 2, active = false, opened = true, lastActiveAt = 20L),
            TabLifecyclePolicy.Candidate(index = 3, active = false, opened = true, lastActiveAt = 30L),
            TabLifecyclePolicy.Candidate(index = 4, active = false, opened = false, lastActiveAt = 1L)
        )

        assertEquals(
            listOf(2, 3),
            TabLifecyclePolicy.sessionsToReleaseForBudget(candidates, maxLiveSessions = 2)
        )
    }

    @Test
    fun doesNotReleaseAnythingWhenWithinBudget() {
        val candidates = listOf(
            TabLifecyclePolicy.Candidate(0, active = true, opened = true, lastActiveAt = 1L),
            TabLifecyclePolicy.Candidate(1, active = false, opened = true, lastActiveAt = 2L),
            TabLifecyclePolicy.Candidate(2, active = false, opened = false, lastActiveAt = 0L)
        )

        assertEquals(emptyList<Int>(), TabLifecyclePolicy.sessionsToReleaseForBudget(candidates, 3))
    }

    @Test
    fun deterministicTieBreakUsesOriginalTabIndex() {
        val candidates = listOf(
            TabLifecyclePolicy.Candidate(4, active = false, opened = true, lastActiveAt = 7L),
            TabLifecyclePolicy.Candidate(2, active = false, opened = true, lastActiveAt = 7L),
            TabLifecyclePolicy.Candidate(0, active = true, opened = true, lastActiveAt = 7L)
        )

        assertEquals(listOf(2), TabLifecyclePolicy.sessionsToReleaseForBudget(candidates, 2))
    }

    @Test
    fun memoryPressureReleasesEveryInactiveOpenedSessionOldestFirst() {
        val candidates = listOf(
            TabLifecyclePolicy.Candidate(0, active = false, opened = true, lastActiveAt = 9L),
            TabLifecyclePolicy.Candidate(1, active = true, opened = true, lastActiveAt = 1L),
            TabLifecyclePolicy.Candidate(2, active = false, opened = false, lastActiveAt = 0L),
            TabLifecyclePolicy.Candidate(3, active = false, opened = true, lastActiveAt = 3L)
        )

        assertEquals(
            listOf(3, 0),
            TabLifecyclePolicy.sessionsToReleaseForMemoryPressure(candidates)
        )
    }

    @Test
    fun minimumBudgetStillPreservesTheActiveTab() {
        val candidates = listOf(
            TabLifecyclePolicy.Candidate(0, active = true, opened = true, lastActiveAt = 1L),
            TabLifecyclePolicy.Candidate(1, active = false, opened = true, lastActiveAt = 2L)
        )

        assertEquals(listOf(1), TabLifecyclePolicy.sessionsToReleaseForBudget(candidates, 0))
    }
}
