package com.browsex.app

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/** User-mediated save and selection prompts for GeckoView's native login form handling. */
internal class BrowserCredentialPromptDelegate(
    private val activity: AppCompatActivity,
    private val privateMode: Boolean,
    private val isStillValid: (GeckoSession, String) -> Boolean
) : GeckoSession.PromptDelegate {

    override fun onLoginSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val option = request.options.firstOrNull() ?: return GeckoResult.fromValue(request.dismiss())
        val origin = CredentialOriginPolicy.canonicalHttpsOrigin(option.value.origin)
            ?: return GeckoResult.fromValue(request.dismiss())
        if (privateMode || option.hint == Autocomplete.SaveOption.Hint.LOW_CONFIDENCE ||
            !isStillValid(session, origin) || activity.isFinishing || activity.isDestroyed
        ) return GeckoResult.fromValue(request.dismiss())

        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        AlertDialog.Builder(activity)
            .setTitle("Save password?")
            .setMessage("Save the sign-in for " + origin + " under " + option.value.username + "?")
            .setPositiveButton("Save") { _, _ ->
                result.complete(if (isStillValid(session, origin)) request.confirm(option) else request.dismiss())
            }
            .setNegativeButton("Not now") { _, _ -> result.complete(request.dismiss()) }
            .setOnCancelListener { result.complete(request.dismiss()) }
            .show()
        return result
    }

    override fun onLoginSelect(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSelectOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        if (privateMode || activity.isFinishing || activity.isDestroyed) {
            return GeckoResult.fromValue(request.dismiss())
        }
        val options = request.options
        if (options.isEmpty()) return GeckoResult.fromValue(request.dismiss())
        val firstOrigin = CredentialOriginPolicy.canonicalHttpsOrigin(options.first().value.origin)
            ?: return GeckoResult.fromValue(request.dismiss())
        if (!isStillValid(session, firstOrigin)) return GeckoResult.fromValue(request.dismiss())

        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val labels = options.map { it.value.username }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Choose a saved sign-in")
            .setItems(labels) { _, index ->
                val selected = options.getOrNull(index)
                val selectedOrigin = selected?.value?.origin?.let(CredentialOriginPolicy::canonicalHttpsOrigin)
                if (selected == null || selectedOrigin != firstOrigin || !isStillValid(session, firstOrigin)) {
                    result.complete(request.dismiss())
                } else {
                    BrowserDeviceAuthenticator.authenticate(
                        activity = activity,
                        title = "Unlock saved passwords",
                        subtitle = "Verify your identity to fill this sign-in",
                        onAuthenticated = {
                            result.complete(if (isStillValid(session, firstOrigin)) request.confirm(selected) else request.dismiss())
                        },
                        onRejected = { result.complete(request.dismiss()) }
                    )
                }
            }
            .setOnCancelListener { result.complete(request.dismiss()) }
            .show()
        return result
    }
}
