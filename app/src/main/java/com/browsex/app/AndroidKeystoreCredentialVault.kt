package com.browsex.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small authenticated-encryption boundary for account credentials.
 *
 * This class deliberately does not persist data or perform OAuth. Callers must
 * store only the returned ciphertext in an app-private, non-backed-up store and
 * must never log plaintext, ciphertext, tokens, or cryptographic exceptions'
 * sensitive provider payloads.
 *
 * A purpose string is authenticated as AES-GCM additional data so ciphertext
 * created for one credential type cannot be replayed as another type.
 */
internal class AndroidKeystoreCredentialVault(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) {
    init {
        require(keyAlias.matches(Regex("^[A-Za-z0-9._-]{1,128}$"))) {
            "Invalid credential key alias"
        }
    }

    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun encrypt(purpose: String, plaintext: ByteArray): ByteArray {
        validatePurpose(purpose)
        require(plaintext.isNotEmpty() && plaintext.size <= MAX_PLAINTEXT_BYTES) {
            "Credential payload size is invalid"
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData(purpose))
        val encrypted = cipher.doFinal(plaintext)
        val iv = cipher.iv
        if (iv == null || iv.size != GCM_NONCE_BYTES) {
            throw GeneralSecurityException("Android Keystore returned an invalid AES-GCM nonce")
        }

        return ByteBuffer.allocate(HEADER_BYTES + iv.size + encrypted.size)
            .put(MAGIC)
            .put(FORMAT_VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(encrypted)
            .array()
    }

    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun decrypt(purpose: String, ciphertext: ByteArray): ByteArray {
        validatePurpose(purpose)
        if (ciphertext.size !in (HEADER_BYTES + GCM_NONCE_BYTES + MIN_GCM_TAG_BYTES)..MAX_ENCRYPTED_BYTES) {
            throw GeneralSecurityException("Credential envelope has an invalid size")
        }

        val buffer = ByteBuffer.wrap(ciphertext)
        val magic = ByteArray(MAGIC.size)
        buffer.get(magic)
        val version = buffer.get()
        val nonceLength = buffer.get().toInt() and 0xff
        if (!magic.contentEquals(MAGIC) || version != FORMAT_VERSION ||
            nonceLength != GCM_NONCE_BYTES ||
            buffer.remaining() <= nonceLength + MIN_GCM_TAG_BYTES
        ) {
            throw GeneralSecurityException("Credential envelope format is unsupported")
        }

        val nonce = ByteArray(nonceLength)
        buffer.get(nonce)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(associatedData(purpose))
        return cipher.doFinal(encrypted)
    }

    /** Removes the local wrapping key. Existing ciphertext then becomes unrecoverable. */
    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun deleteKey() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = getExistingKeyOrNull()
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun getExistingKey(): SecretKey =
        getExistingKeyOrNull() ?: throw GeneralSecurityException("Credential encryption key is unavailable")

    private fun getExistingKeyOrNull(): SecretKey? {
        val keyStore = loadKeyStore()
        if (!keyStore.containsAlias(keyAlias)) return null
        val key = keyStore.getKey(keyAlias, null)
        return key as? SecretKey
            ?: throw GeneralSecurityException("Credential encryption key is invalid")
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun validatePurpose(purpose: String) {
        require(purpose.isNotBlank() && purpose.length <= MAX_PURPOSE_LENGTH &&
            purpose.none { it.isISOControl() }
        ) { "Credential purpose is invalid" }
    }

    private fun associatedData(purpose: String): ByteArray =
        "$AAD_PREFIX$purpose".toByteArray(StandardCharsets.UTF_8)

    companion object {
        const val PURPOSE_PROVIDER_REFRESH_TOKEN = "provider.refresh-token"
        const val PURPOSE_PROVIDER_IDENTITY = "provider.identity"
        const val PURPOSE_SYNC_ENCRYPTION_KEY = "sync.encryption-key"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val DEFAULT_KEY_ALIAS = "com.browsex.account.credentials.v1"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val GCM_NONCE_BYTES = 12
        private const val MIN_GCM_TAG_BYTES = 16
        private const val MAX_PURPOSE_LENGTH = 128
        private const val MAX_PLAINTEXT_BYTES = 1024 * 1024
        private const val MAX_ENCRYPTED_BYTES = MAX_PLAINTEXT_BYTES + 64
        private const val HEADER_BYTES = 6
        private const val AAD_PREFIX = "BrowseX|credential-vault|v1|"
        private const val FORMAT_VERSION: Byte = 1
        private val MAGIC = byteArrayOf(0x42, 0x58, 0x43, 0x56) // BXCV
    }
}
