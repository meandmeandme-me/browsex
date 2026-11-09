package com.browsex.app

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class StoredCredentialSummary(val id: String, val origin: String, val username: String)

internal class BrowserCredentialVault(
    context: Context,
    private val requestAuthentication: (onAuthenticated: () -> Unit, onRejected: () -> Unit) -> Unit
) : Autocomplete.StorageDelegate {
    private val prefs = context.applicationContext.getSharedPreferences("browsex_credentials_v1", Context.MODE_PRIVATE)
    private val accessPolicy = CredentialAccessPolicy({ SystemClock.elapsedRealtime() })
    @Volatile private var privateTabsOpen = loadPrivateTabsOpen()

    /**
     * The Android Autofill service runs through a separate Service/Activity instance.
     * Read the process-scoped private-tab marker on every access so that it cannot
     * bypass the browser's "private tabs override password access" rule.
     */
    val privateBrowsingActive: Boolean get() = refreshPrivateTabsOpen()

    fun setPrivateTabsOpen(open: Boolean) {
        val pid = android.os.Process.myPid()
        privateTabsOpen = open
        // updateChrome() may run during every navigation/progress callback. Avoid
        // synchronous disk I/O when the process-scoped state has not changed.
        if (prefs.getInt(PRIVATE_TABS_PROCESS_KEY, -1) == pid &&
            prefs.getBoolean(PRIVATE_TABS_OPEN_KEY, false) == open
        ) return

        // AutofillService runs in the app process and reads the same preference cache.
        // apply() updates memory immediately without blocking the browser UI thread.
        prefs.edit()
            .putBoolean(PRIVATE_TABS_OPEN_KEY, open)
            .putInt(PRIVATE_TABS_PROCESS_KEY, pid)
            .apply()
    }

    private fun loadPrivateTabsOpen(): Boolean {
        val pid = android.os.Process.myPid()
        if (prefs.getInt(PRIVATE_TABS_PROCESS_KEY, -1) != pid) {
            prefs.edit()
                .putBoolean(PRIVATE_TABS_OPEN_KEY, false)
                .putInt(PRIVATE_TABS_PROCESS_KEY, pid)
                .apply()
            return false
        }
        return prefs.getBoolean(PRIVATE_TABS_OPEN_KEY, false)
    }

    private fun refreshPrivateTabsOpen(): Boolean {
        val current = loadPrivateTabsOpen()
        privateTabsOpen = current
        return current
    }
    fun unlockForSession() = accessPolicy.unlock()
    fun lock() = accessPolicy.lock()

    /** One-shot external Autofill authorization; no secret leaves the vault before auth. */
    fun authenticateForExternalAutofill(
        onAuthenticated: () -> Unit,
        onRejected: () -> Unit
    ) {
        if (privateBrowsingActive) {
            onRejected()
            return
        }
        requestAuthentication(
            {
                val authorized = runCatching {
                    unlockForSession()
                    !privateBrowsingActive
                }.getOrElse {
                    android.util.Log.e("BrowseX", "External credential authorization failed", it)
                    false
                }
                if (authorized) onAuthenticated() else onRejected()
            },
            onRejected
        )
    }

    @Synchronized
    fun entries(): List<StoredCredentialSummary> {
        if (!accessPolicy.canFetchCredentials(privateBrowsingActive)) return emptyList()
        return runCatching {
            read().asSequence()
                .mapNotNull { row ->
                    val id = row.optString("id")
                    val origin = row.optString("origin")
                    val username = row.optString("username")
                    val canonicalOrigin = CredentialOriginPolicy.canonicalHttpsOrigin(origin)
                    if (id.isBlank() || id.length > MAX_CREDENTIAL_ID_LENGTH ||
                        canonicalOrigin != origin || username.isBlank() ||
                        username.length > MAX_USERNAME_LENGTH
                    ) null else StoredCredentialSummary(id, origin, username)
                }
                .take(MAX_CREDENTIALS)
                .toList()
        }.getOrElse { android.util.Log.e("BrowseX", "Credential list unavailable", it); emptyList() }
    }

    @Synchronized
    fun passwordFor(id: String): String? {
        if (id.isBlank() || id.length > MAX_CREDENTIAL_ID_LENGTH ||
            !accessPolicy.canFetchCredentials(privateBrowsingActive)
        ) return null
        return runCatching {
            read().firstOrNull { it.optString("id") == id }
                ?.optString("secret")?.takeIf { it.isNotEmpty() && it.length <= MAX_PASSWORD_LENGTH }
        }.getOrElse { android.util.Log.e("BrowseX", "Saved credential unavailable", it); null }
    }

    @Synchronized
    fun delete(id: String): Boolean {
        if (id.isBlank() || id.length > MAX_CREDENTIAL_ID_LENGTH ||
            !accessPolicy.canFetchCredentials(privateBrowsingActive)
        ) return false
        val rows = runCatching { read() }.getOrElse { return false }
        val filtered = rows.filterNot { it.optString("id") == id }
        if (filtered.size == rows.size) return false
        write(JSONArray().also { array -> filtered.forEach(array::put) })
        return true
    }

    @Synchronized
    override fun onLoginFetch(): GeckoResult<Array<Autocomplete.LoginEntry>> =
        GeckoResult.fromValue(emptyArray<Autocomplete.LoginEntry>())

    @Synchronized
    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> {
        if (privateBrowsingActive) return GeckoResult.fromValue(emptyArray<Autocomplete.LoginEntry>())
        if (accessPolicy.canFetchCredentials(privateBrowsingActive)) {
            return GeckoResult.fromValue(loadEntriesForDomain(domain))
        }

        // GeckoView asks for credentials when the user interacts with a login form.
        // Authorize the Keystore key before decrypting anything for that form.
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()
        requestAuthentication(
            {
                val entries = runCatching {
                    unlockForSession()
                    synchronized(this) {
                        if (accessPolicy.canFetchCredentials(privateBrowsingActive)) loadEntriesForDomain(domain)
                        else emptyArray()
                    }
                }.getOrElse {
                    android.util.Log.e("BrowseX", "Credential autofill authorization failed", it)
                    emptyArray()
                }
                result.complete(entries)
            },
            { result.complete(emptyArray()) }
        )
        return result
    }

    @Synchronized
    private fun loadEntriesForDomain(domain: String): Array<Autocomplete.LoginEntry> {
        val rows = runCatching { read() }.getOrElse {
            android.util.Log.e("BrowseX", "Credential lookup unavailable", it)
            emptyList()
        }.filter { CredentialOriginPolicy.matchesExactOrigin(it.optString("origin"), domain) }
        return rows.map { row ->
            Autocomplete.LoginEntry.Builder().guid(row.optString("id")).origin(row.optString("origin"))
                .formActionOrigin(row.optString("action").takeIf { it.isNotBlank() })
                .httpRealm(row.optString("realm").takeIf { it.isNotBlank() })
                .username(row.optString("username")).password(row.optString("secret")).build()
        }.toTypedArray()
    }

    @Synchronized
    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        if (privateBrowsingActive || !accessPolicy.canFetchCredentials(privateBrowsingActive)) return
        val origin = CredentialOriginPolicy.canonicalHttpsOrigin(login.origin) ?: return
        val action = login.formActionOrigin?.takeIf { it.isNotBlank() }?.let(CredentialOriginPolicy::canonicalHttpsOrigin)
        if (login.formActionOrigin?.isNotBlank() == true && action != origin) return
        if (login.username.isBlank() || login.username.length > MAX_USERNAME_LENGTH ||
            login.password.isBlank() || login.password.length > MAX_PASSWORD_LENGTH
        ) return
        val rows = runCatching { read() }.getOrElse {
            android.util.Log.e("BrowseX", "Refusing to overwrite unreadable credential data", it)
            return
        }.filterNot {
            it.optString("id") == login.guid ||
                (it.optString("origin") == origin && it.optString("username") == login.username)
        }.toMutableList()
        val credentialId = login.guid?.takeIf { it.isNotBlank() && it.length <= MAX_CREDENTIAL_ID_LENGTH }
            ?: UUID.randomUUID().toString()
        rows.add(0, JSONObject().put("id", credentialId)
            .put("origin", origin).put("action", action).put("realm", login.httpRealm)
            .put("username", login.username.take(512)).put("secret", login.password))
        write(JSONArray().also { array -> rows.take(500).forEach(array::put) })
    }

    private fun read(): List<JSONObject> {
        val ivText = prefs.getString("iv", null)
        val dataText = prefs.getString("data", null)
        if (ivText == null && dataText == null) return emptyList()
        if (ivText == null || dataText == null) {
            throw IllegalStateException("Credential vault payload is incomplete")
        }
        return try {
            val version = prefs.getInt("version", 1)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val decryptionKey = if (version == 1) legacyKey() else key()
            cipher.init(Cipher.DECRYPT_MODE, decryptionKey, GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            val json = JSONArray(String(cipher.doFinal(Base64.decode(dataText, Base64.NO_WRAP)), Charsets.UTF_8))
            if (version == 1) {
                // Re-encrypt existing credentials with the authentication-bound key.
                write(json)
                KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(LEGACY_KEY_ALIAS) }
            } else if (version != 2) {
                throw IllegalStateException("Unsupported credential vault version")
            }
            (0 until json.length()).mapNotNull(json::optJSONObject)
        } catch (error: Exception) {
            android.util.Log.e("BrowseX", "Encrypted credential data unavailable", error)
            throw IllegalStateException("Credential vault could not be decrypted", error)
        }
    }

    private fun write(json: JSONArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putInt("version", 2).commit())
    }

    /** AES key use is authorized by Android's recent biometric/device-credential token. */
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(
                60,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(60)
            builder.setInvalidatedByBiometricEnrollment(true)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(builder.build())
        return generator.generateKey()
    }

    /** Used only to migrate installations that stored data before key authentication was enforced. */
    private fun legacyKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(LEGACY_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(LEGACY_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "browsex.credentials.aes.v2.auth"
        const val LEGACY_KEY_ALIAS = "browsex.credentials.aes.v1"
        const val PRIVATE_TABS_OPEN_KEY = "private_tabs_open"
        const val PRIVATE_TABS_PROCESS_KEY = "private_tabs_process"
        const val MAX_CREDENTIALS = 500
        const val MAX_CREDENTIAL_ID_LENGTH = 128
        const val MAX_USERNAME_LENGTH = 512
        const val MAX_PASSWORD_LENGTH = 4096
    }
}
