package com.browsex.app

/**
 * Provider-neutral account and synchronization models.
 * No Android, GeckoView, OAuth SDK, backend or credential-vault dependencies.
 * This file does not implement sign-in or cloud synchronization.
 */
enum class IdentityProvider { GOOGLE, MICROSOFT, APPLE, OPENID_CONNECT }

data class AccountIdentity(
    val provider: IdentityProvider,
    val providerSubject: String,
    val displayName: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false
) {
    init {
        require(providerSubject.isNotBlank() && providerSubject.length <= 1024)
        require(displayName == null || displayName.length <= 256)
        require(email == null || email.length <= 320)
    }
}

enum class AccountConnectionState {
    SIGNED_OUT, SIGNING_IN, SIGNED_IN, REAUTHENTICATION_REQUIRED,
    PROVIDER_REVOKED, SIGNING_OUT
}

enum class SyncCategory {
    BOOKMARKS, READING_LIST, BROWSER_PREFERENCES, NAMED_SESSIONS,
    OPEN_TABS, BROWSING_HISTORY
}

data class SyncConsentEntry(
    val category: SyncCategory,
    val enabled: Boolean,
    val updatedAtEpochMillis: Long,
    val policyVersion: Int
) {
    init {
        require(updatedAtEpochMillis >= 0L)
        require(policyVersion > 0)
    }
}

enum class ClientPlatform { ANDROID, WINDOWS, MACOS, LINUX, ANDROID_TV, OTHER }

data class SyncDevice(
    val deviceId: String,
    val displayName: String,
    val platform: ClientPlatform,
    val lastSeenEpochMillis: Long,
    val isCurrentDevice: Boolean,
    val canRevoke: Boolean
) {
    init {
        require(deviceId.isNotBlank() && deviceId.length <= 256)
        require(displayName.isNotBlank() && displayName.length <= 128)
        require(lastSeenEpochMillis >= 0L)
    }
}

enum class SyncOperation { CREATE, UPDATE, DELETE }

data class SyncRecordMetadata(
    val recordId: String,
    val category: SyncCategory,
    val schemaVersion: Int,
    val revision: Long,
    val operation: SyncOperation,
    val modifiedAtEpochMillis: Long,
    val originDeviceId: String
) {
    init {
        require(recordId.isNotBlank() && recordId.length <= 256)
        require(schemaVersion in 1..10_000)
        require(revision >= 0L)
        require(modifiedAtEpochMillis >= 0L)
        require(originDeviceId.isNotBlank() && originDeviceId.length <= 256)
    }
}

enum class SyncConflictStrategy { KEEP_BOTH, MERGE_BY_STABLE_ID, USER_DECISION_REQUIRED }

data class BrowseXAccount(
    val identity: AccountIdentity,
    val connectionState: AccountConnectionState,
    val connectedAtEpochMillis: Long? = null
) {
    init { require(connectedAtEpochMillis == null || connectedAtEpochMillis >= 0L) }
}
