package com.browsex.app

/**
 * Platform-neutral contracts for the future account and sync data layer.
 *
 * Implementations belong in platform/network modules. UI and browser-engine
 * classes must depend on use cases, never on provider SDKs or HTTP clients.
 * No interface exposes OAuth tokens or cookies.
 */
internal interface IdentityProviderGateway {
    suspend fun signIn(provider: IdentityProvider): AccountIdentity
    suspend fun disconnect(identity: AccountIdentity)
}

internal interface AccountSessionRepository {
    suspend fun currentAccount(): BrowseXAccount?
    suspend fun saveAccount(account: BrowseXAccount)
    suspend fun clearAccount()
}

internal interface SyncConsentRepository {
    suspend fun getAll(): List<SyncConsentEntry>
    suspend fun setConsent(entry: SyncConsentEntry)
}

internal interface DeviceRegistryRepository {
    suspend fun listDevices(): List<SyncDevice>
    suspend fun revokeDevice(deviceId: String)
}

/** SHA-256 digest covers ciphertext only; plaintext hashes must never be exposed as sync metadata. */
internal data class EncryptedSyncRecord(
    val metadata: SyncRecordMetadata,
    val ciphertext: ByteArray,
    val payloadDigest: String
) {
    init {
        require(ciphertext.size in 1..MAX_CIPHERTEXT_BYTES)
        require(payloadDigest.matches(SHA256_HEX))
        require(
            java.security.MessageDigest.isEqual(
                payloadDigest.lowercase().toByteArray(Charsets.US_ASCII),
                SyncPayloadIntegrity.digest(ciphertext).toByteArray(Charsets.US_ASCII)
            )
        ) { "Encrypted sync payload digest does not match ciphertext" }
    }

    private companion object {
        const val MAX_CIPHERTEXT_BYTES = 8 * 1024 * 1024
        val SHA256_HEX = Regex("^[a-fA-F0-9]{64}$")
    }
}

internal object SyncPayloadIntegrity {
    fun digest(ciphertext: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(ciphertext)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    fun matches(record: EncryptedSyncRecord, ciphertext: ByteArray = record.ciphertext): Boolean {
        if (ciphertext.size !in 1..8 * 1024 * 1024) return false
        val expected = digest(ciphertext)
        return java.security.MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            record.payloadDigest.lowercase().toByteArray(Charsets.US_ASCII)
        )
    }
}

internal data class SyncCursor(val value: String?) {
    init { require(value == null || value.length <= 4096) }
}

internal data class SyncBatch(
    val records: List<EncryptedSyncRecord>,
    val nextCursor: SyncCursor
) {
    init {
        require(records.size <= MAX_RECORDS_PER_BATCH)
        require(records.map { it.metadata.recordId }.toSet().size == records.size)
    }

    private companion object {
        const val MAX_RECORDS_PER_BATCH = 500
    }
}

internal interface EncryptedSyncTransport {
    suspend fun upload(records: List<EncryptedSyncRecord>)
    suspend fun download(cursor: SyncCursor, categories: Set<SyncCategory>): SyncBatch
}

internal interface SyncRecordRepository {
    suspend fun pendingUploads(categories: Set<SyncCategory>, limit: Int): List<EncryptedSyncRecord>
    suspend fun applyRemoteBatch(batch: SyncBatch)
    suspend fun markUploaded(recordIds: Set<String>)
}

internal interface SyncCipher {
    suspend fun encrypt(category: SyncCategory, plaintext: ByteArray): EncryptedSyncRecord
    suspend fun decrypt(record: EncryptedSyncRecord): ByteArray
}

internal enum class SyncRunState {
    IDLE, RUNNING, PAUSED, OFFLINE, AUTH_REQUIRED, FAILED
}

internal data class SyncRunStatus(
    val state: SyncRunState,
    val lastSuccessfulSyncEpochMillis: Long? = null,
    val pendingRecordCount: Int = 0,
    val errorCode: String? = null
) {
    init { require(pendingRecordCount >= 0) }
}

internal interface SyncStatusRepository {
    suspend fun currentStatus(): SyncRunStatus
    suspend fun saveStatus(status: SyncRunStatus)
}

internal interface AccountDataLifecycleRepository {
    suspend fun exportAccountData(): ByteArray
    suspend fun deleteCloudData()
}

/**
 * Consent is category-scoped and opt-in for sensitive browsing data.
 * A policy-version mismatch disables sync until the user reviews consent again.
 */
internal object SyncConsentPolicy {
    const val CURRENT_POLICY_VERSION = 1

    fun initialChoices(nowEpochMillis: Long): List<SyncConsentEntry> {
        require(nowEpochMillis >= 0L)
        return SyncCategory.values().map { category ->
            SyncConsentEntry(
                category = category,
                // Cloud sync remains off until the user explicitly enables each category.
                enabled = false,
                updatedAtEpochMillis = nowEpochMillis,
                policyVersion = CURRENT_POLICY_VERSION
            )
        }
    }

    fun isEnabled(category: SyncCategory, entries: List<SyncConsentEntry>): Boolean {
        val choices = entries.filter { it.category == category }
        val latestTimestamp = choices.maxOfOrNull { it.updatedAtEpochMillis } ?: return false
        val latest = choices.filter { it.updatedAtEpochMillis == latestTimestamp }
        // Conflicting records at the same revision/time fail closed instead of enabling sync.
        if (latest.map { it.enabled to it.policyVersion }.distinct().size != 1) return false
        val current = latest.first()
        return current.policyVersion == CURRENT_POLICY_VERSION && current.enabled
    }
}
