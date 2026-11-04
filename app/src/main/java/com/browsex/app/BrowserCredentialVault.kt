package com.browsex.app

import android.content.Context
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

internal data class StoredCredentialSummary(val id: String, val origin: String, val username: String, val password: String)

internal class BrowserCredentialVault(context: Context) : Autocomplete.StorageDelegate {
    private val prefs = context.applicationContext.getSharedPreferences("browsex_credentials_v1", Context.MODE_PRIVATE)
    private val accessPolicy = CredentialAccessPolicy({ SystemClock.elapsedRealtime() })
    @Volatile private var privateTabsOpen = false

    val privateBrowsingActive: Boolean get() = privateTabsOpen
    fun setPrivateTabsOpen(open: Boolean) { privateTabsOpen = open }
    fun unlockForSession() = accessPolicy.unlock()
    fun lock() = accessPolicy.lock()

    @Synchronized
    fun entries(): List<StoredCredentialSummary> = runCatching {
        read().map { StoredCredentialSummary(it.optString("id"), it.optString("origin"),
            it.optString("username"), it.optString("secret")) }
    }.getOrElse { android.util.Log.e("BrowseX", "Credential list unavailable", it); emptyList() }

    @Synchronized
    fun delete(id: String): Boolean {
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
        if (!accessPolicy.canFetchCredentials(privateTabsOpen)) {
            return GeckoResult.fromValue(emptyArray<Autocomplete.LoginEntry>())
        }
        val rows = runCatching { read() }.getOrElse {
            android.util.Log.e("BrowseX", "Credential lookup unavailable", it)
            emptyList()
        }.filter { CredentialOriginPolicy.matchesExactOrigin(it.optString("origin"), domain) }
        val result = rows.map { row ->
            Autocomplete.LoginEntry.Builder().guid(row.optString("id")).origin(row.optString("origin"))
                .formActionOrigin(row.optString("action").takeIf { it.isNotBlank() })
                .httpRealm(row.optString("realm").takeIf { it.isNotBlank() })
                .username(row.optString("username")).password(row.optString("secret")).build()
        }.toTypedArray()
        return GeckoResult.fromValue(result)
    }

    @Synchronized
    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        if (privateTabsOpen) return
        val origin = CredentialOriginPolicy.canonicalHttpsOrigin(login.origin) ?: return
        val action = login.formActionOrigin?.takeIf { it.isNotBlank() }?.let(CredentialOriginPolicy::canonicalHttpsOrigin)
        if (login.formActionOrigin?.isNotBlank() == true && action != origin) return
        if (login.username.isBlank() || login.password.isBlank() || login.password.length > 4096) return
        val rows = runCatching { read() }.getOrElse {
            android.util.Log.e("BrowseX", "Refusing to overwrite unreadable credential data", it)
            return
        }.filterNot {
            it.optString("id") == login.guid ||
                (it.optString("origin") == origin && it.optString("username") == login.username)
        }.toMutableList()
        rows.add(0, JSONObject().put("id", login.guid ?: UUID.randomUUID().toString())
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
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            val json = JSONArray(String(cipher.doFinal(Base64.decode(dataText, Base64.NO_WRAP)), Charsets.UTF_8))
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
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private companion object { const val KEY_ALIAS = "browsex.credentials.aes.v1" }
}
