package com.browsex.app.storage

import com.browsex.app.BrowserSiteOriginPolicy

/** Persisted per-origin camera/microphone choices. "ask" means no stored decision. */
internal class RoomSitePermissionRepository(
    private val dao: BrowseXStorageDao
) {
    suspend fun decision(origin: String, feature: String): String? {
        val canonical = BrowserSiteOriginPolicy.canonicalOrigin(origin) ?: return null
        if (feature !in FEATURES) return null
        return dao.permissionDecision(canonical, feature)?.decision
            ?.takeIf { it == "allow" || it == "deny" }
    }

    suspend fun setDecision(
        origin: String,
        feature: String,
        decision: String,
        updatedAt: Long = System.currentTimeMillis()
    ): Boolean {
        val canonical = BrowserSiteOriginPolicy.canonicalOrigin(origin) ?: return false
        if (feature !in FEATURES || decision !in DECISIONS || updatedAt < 0L) return false
        if (decision == "ask") {
            dao.deletePermissionDecision(canonical, feature)
        } else {
            dao.setPermissionDecision(
                SitePermissionDecisionEntity(canonical, feature, decision, updatedAt),
                MAX_DECISIONS
            )
        }
        return true
    }

    suspend fun clearOrigin(origin: String): Boolean {
        val canonical = BrowserSiteOriginPolicy.canonicalOrigin(origin) ?: return false
        dao.deletePermissionDecisionsForOrigin(canonical)
        return true
    }

    suspend fun clearAll() = dao.clearAllPermissionDecisions()

    suspend fun all(): List<SitePermissionDecisionEntity> = dao.permissionDecisions()

    private companion object {
        val FEATURES = setOf("camera", "microphone")
        val DECISIONS = setOf("allow", "deny", "ask")
        const val MAX_DECISIONS = 100
    }
}
