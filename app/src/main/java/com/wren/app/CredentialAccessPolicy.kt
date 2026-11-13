package com.wren.app

/** Short-lived unlock window for login autofill; private browsing always overrides it. */
internal class CredentialAccessPolicy(
    private val nowElapsedRealtime: () -> Long,
    private val unlockWindowMillis: Long = 60_000L
) {
    @Volatile private var unlockedUntil = 0L

    init { require(unlockWindowMillis > 0L) }

    fun unlock() {
        val now = nowElapsedRealtime().coerceAtLeast(0L)
        unlockedUntil = if (Long.MAX_VALUE - now < unlockWindowMillis) Long.MAX_VALUE else now + unlockWindowMillis
    }

    fun lock() { unlockedUntil = 0L }

    fun canFetchCredentials(privateTabsOpen: Boolean): Boolean =
        !privateTabsOpen && nowElapsedRealtime().coerceAtLeast(0L) < unlockedUntil
}
