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

internal data class EncryptedSyncRecord(
    val metadata: SyncRecordMetadata,
    val ciphertext: ByteArray,
    val payloadDigest: String
) {
    init {
        require(ciphertext.isNotEmpty())
        require(payloadDigest.isNotBlank())
    }
}

internal data class SyncCursor(val value: String?) 

internal data class SyncBatch(
    val records: List<EncryptedSyncRecord>,
    val nextCursor: SyncCursor
)

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
