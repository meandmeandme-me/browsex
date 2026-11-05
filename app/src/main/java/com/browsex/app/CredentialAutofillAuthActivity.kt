package com.browsex.app

import android.view.autofill.AutofillManager
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Internal system-Autofill auth step; only origin and transient field IDs enter the Intent. */
internal class CredentialAutofillAuthActivity : AppCompatActivity() {
    private var finished = false

    private val vault by lazy {
        BrowserCredentialVault(applicationContext) { onAuthenticated, onRejected ->
            runOnUiThread {
                BrowserDeviceAuthenticator.authenticate(
                    activity = this,
                    title = BrowseXStrings.text(this, "Unlock BrowseX passwords"),
                    subtitle = BrowseXStrings.text(this, "Verify your identity before filling a saved sign-in"),
                    onAuthenticated = onAuthenticated,
                    onRejected = onRejected
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val origin = intent.getStringExtra(EXTRA_ORIGIN)
            ?.let(CredentialOriginPolicy::canonicalHttpsOrigin)
        @Suppress("DEPRECATION")
        val usernameId = intent.getParcelableExtra<AutofillId>(EXTRA_USERNAME_ID)
        @Suppress("DEPRECATION")
        val passwordId = intent.getParcelableExtra<AutofillId>(EXTRA_PASSWORD_ID)
        if (origin == null || passwordId == null) {
            finishWithoutDataset()
            return
        }

        vault.authenticateForExternalAutofill(
            onAuthenticated = {
                runOnUiThread {
                    if (isFinishing || isDestroyed || finished) return@runOnUiThread
                    showCredentialChoices(origin, usernameId, passwordId)
                }
            },
            onRejected = { runOnUiThread { finishWithoutDataset() } }
        )
    }

    private fun showCredentialChoices(
        origin: String,
        usernameId: AutofillId?,
        passwordId: AutofillId
    ) {
        val matches = vault.entries().filter {
            CredentialOriginPolicy.matchesExactOrigin(it.origin, origin)
        }
        if (matches.isEmpty()) {
            finishWithoutDataset()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(BrowseXStrings.text(this, "Choose a saved sign-in"))
            .setMessage(origin)
            .setItems(matches.map { it.username }.toTypedArray()) { _, index ->
                val credential = matches.getOrNull(index)
                if (credential == null ||
                    CredentialOriginPolicy.canonicalHttpsOrigin(credential.origin) != origin ||
                    isFinishing || isDestroyed
                ) {
                    finishWithoutDataset()
                } else {
                    val password = vault.passwordFor(credential.id)
                    if (password.isNullOrEmpty()) finishWithoutDataset()
                    else returnDataset(usernameId, passwordId, credential.username, password)
                }
            }
            .setOnCancelListener { finishWithoutDataset() }
            .show()
    }

    private fun returnDataset(
        usernameId: AutofillId?,
        passwordId: AutofillId,
        username: String,
        password: String
    ) {
        if (finished || isFinishing || isDestroyed) {
            finishWithoutDataset()
            return
        }
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, username)
        }
        val builder = Dataset.Builder(presentation)
        usernameId?.let { builder.setValue(it, AutofillValue.forText(username), presentation) }
        builder.setValue(passwordId, AutofillValue.forText(password), presentation)
        val response = FillResponse.Builder().addDataset(builder.build()).build()
        finished = true
        setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response))
        finish()
    }

    private fun finishWithoutDataset() {
        if (finished) return
        finished = true
        setResult(RESULT_CANCELED, Intent().putExtras(Bundle.EMPTY))
        finish()
    }

    companion object {
        const val EXTRA_ORIGIN = "com.browsex.autofill.ORIGIN"
        const val EXTRA_USERNAME_ID = "com.browsex.autofill.USERNAME_ID"
        const val EXTRA_PASSWORD_ID = "com.browsex.autofill.PASSWORD_ID"
    }
}
