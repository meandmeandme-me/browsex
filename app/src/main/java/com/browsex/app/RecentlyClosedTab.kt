package com.browsex.app

internal data class RecentlyClosedTab(
    val id: String,
    val url: String,
    val title: String,
    val closedAt: Long
)
