package com.wren.app

/**
 * Pure policy for bounding live GeckoSession instances.
 *
 * The active tab is never selected for release. Among inactive opened sessions,
 * the least recently active sessions are released first. The returned indexes
 * refer to the exact input snapshot and must be applied immediately.
 */
internal object TabLifecyclePolicy {
    internal data class Candidate(
        val index: Int,
        val active: Boolean,
        val opened: Boolean,
        val lastActiveAt: Long
    )

    /** Returns the inactive opened sessions that must be released to meet [maxLiveSessions]. */
    fun sessionsToReleaseForBudget(
        candidates: List<Candidate>,
        maxLiveSessions: Int
    ): List<Int> {
        val safeBudget = maxLiveSessions.coerceAtLeast(1)
        val openedCount = candidates.count { it.opened }
        val excess = (openedCount - safeBudget).coerceAtLeast(0)
        if (excess == 0) return emptyList()

        return candidates.asSequence()
            .filter { it.opened && !it.active }
            .sortedWith(compareBy<Candidate> { it.lastActiveAt }.thenBy { it.index })
            .take(excess)
            .map { it.index }
            .toList()
    }

    /** Returns every inactive opened session, oldest first, for severe memory pressure. */
    fun sessionsToReleaseForMemoryPressure(candidates: List<Candidate>): List<Int> =
        candidates.asSequence()
            .filter { it.opened && !it.active }
            .sortedWith(compareBy<Candidate> { it.lastActiveAt }.thenBy { it.index })
            .map { it.index }
            .toList()
}
