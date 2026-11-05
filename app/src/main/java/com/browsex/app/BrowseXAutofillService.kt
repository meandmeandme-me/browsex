package com.browsex.app

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import android.widget.RemoteViews
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fill-only Android Autofill bridge for saved HTTPS website credentials.
 * Secrets are not decrypted in the Service: the returned response requires
 * an explicit device-authentication and account-selection Activity.
 */
internal class BrowseXAutofillService : AutofillService() {
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onSuccess(null)
            return
        }
        try {
            val structure = request.fillContexts.lastOrNull()?.structure
            if (structure == null) {
                callback.onSuccess(null)
                return
            }
            val sourcePackage = structure.activityComponent?.packageName
            // GeckoView's own website credential flow remains the only credential path
            // inside BrowseX; the system service is intended for other apps' web forms.
            if (sourcePackage.isNullOrBlank() || sourcePackage == packageName) {
                callback.onSuccess(null)
                return
            }

            val fields = findLoginFields(structure)
            val origin = fields.webDomain?.let(::canonicalWebOrigin)
            val passwordId = fields.passwordId
            if (origin == null || passwordId == null) {
                callback.onSuccess(null)
                return
            }
            val autofillIds = listOfNotNull(fields.usernameId, passwordId).toTypedArray()
            val authIntent = Intent(this, CredentialAutofillAuthActivity::class.java)
                .putExtra(CredentialAutofillAuthActivity.EXTRA_ORIGIN, origin)
                .putExtra(CredentialAutofillAuthActivity.EXTRA_USERNAME_ID, fields.usernameId)
                .putExtra(CredentialAutofillAuthActivity.EXTRA_PASSWORD_ID, passwordId)
            val pendingIntent = PendingIntent.getActivity(
                this,
                nextRequestCode(),
                authIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "Unlock BrowseX passwords")
            }
            val response = FillResponse.Builder()
                .setAuthentication(autofillIds, pendingIntent.intentSender, presentation)
                .build()
            if (cancellationSignal.isCanceled) callback.onSuccess(null)
            else callback.onSuccess(response)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not prepare an Autofill response", error)
            callback.onFailure("BrowseX could not prepare password autofill")
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Deliberately fill-only. Do not capture passwords from unrelated apps until
        // an independently authenticated and reviewable save flow exists.
        callback.onSuccess()
    }

    private fun canonicalWebOrigin(raw: String): String? {
        val candidate = if (raw.contains("://")) raw else "https://$raw"
        return CredentialOriginPolicy.canonicalHttpsOrigin(candidate)
    }

    private fun findLoginFields(structure: AssistStructure): LoginFields {
        val result = LoginFields()
        for (windowIndex in 0 until structure.windowNodeCount) {
            visit(structure.getWindowNodeAt(windowIndex).rootViewNode, result)
        }
        return result
    }

    private fun visit(node: AssistStructure.ViewNode, result: LoginFields) {
        if (result.webDomain.isNullOrBlank()) {
            result.webDomain = node.webDomain?.takeIf { it.isNotBlank() }
        }
        val hints = node.autofillHints?.toList().orEmpty()
        val id = node.autofillId
        if (id != null && result.usernameId == null && BrowserAutofillFieldPolicy.isUsername(hints)) {
            result.usernameId = id
        }
        if (id != null && result.passwordId == null && BrowserAutofillFieldPolicy.isExistingPassword(hints)) {
            result.passwordId = id
        }
        for (index in 0 until node.childCount) {
            visit(node.getChildAt(index), result)
        }
    }

    private data class LoginFields(
        var usernameId: android.view.autofill.AutofillId? = null,
        var passwordId: android.view.autofill.AutofillId? = null,
        var webDomain: String? = null
    )

    private fun nextRequestCode(): Int {
        val value = requestCounter.incrementAndGet()
        return if (value == 0) requestCounter.incrementAndGet() else value
    }

    private companion object {
        const val TAG = "BrowseXAutofill"
        val requestCounter = AtomicInteger(1000)
    }
}
