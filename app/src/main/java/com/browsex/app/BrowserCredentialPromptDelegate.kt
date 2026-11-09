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
    private val hasPrivateTabsOpen: () -> Boolean,
    private val onDeviceAuthenticated: () -> Unit,
    private val isStillValid: (GeckoSession, String) -> Boolean,
    private val translate: (String) -> String
) : GeckoSession.PromptDelegate {

    override fun onLoginSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val option = request.options.firstOrNull() ?: return GeckoResult.fromValue(request.dismiss())
        val origin = CredentialOriginPolicy.canonicalHttpsOrigin(option.value.origin)
            ?: return GeckoResult.fromValue(request.dismiss())
        if (privateMode || hasPrivateTabsOpen() ||
            (option.hint and Autocomplete.SaveOption.Hint.LOW_CONFIDENCE) != 0 ||
            !isStillValid(session, origin) || activity.isFinishing || activity.isDestroyed
        ) return GeckoResult.fromValue(request.dismiss())

        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        AlertDialog.Builder(activity)
            .setTitle(translate("Save password?"))
            .setMessage(translate("Save this website sign-in?") + "\n" + origin + "\n" + option.value.username)
            .setPositiveButton(translate("Save")) { _, _ ->
                BrowserDeviceAuthenticator.authenticate(
                    activity = activity,
                    title = translate("Save password"),
                    subtitle = translate("Verify your identity to encrypt this sign-in"),
                    onAuthenticated = {
                        onDeviceAuthenticated()
                        result.complete(if (!hasPrivateTabsOpen() && isStillValid(session, origin)) request.confirm(option) else request.dismiss())
                    },
                    onRejected = { result.complete(request.dismiss()) }
                )
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
        if (privateMode || hasPrivateTabsOpen() || activity.isFinishing || activity.isDestroyed) {
            return GeckoResult.fromValue(request.dismiss())
        }
        val safeOptions = request.options.filter { option ->
            val origin = CredentialOriginPolicy.canonicalHttpsOrigin(option.value.origin)
            val actionOrigin = option.value.formActionOrigin?.takeIf { it.isNotBlank() }
                ?.let(CredentialOriginPolicy::canonicalHttpsOrigin)
            val insecure = (option.hint and Autocomplete.SelectOption.Hint.INSECURE_FORM) != 0
            !insecure && origin != null && (actionOrigin == null || actionOrigin == origin)
        }
        val firstOrigin = safeOptions.firstOrNull()?.value?.origin
            ?.let(CredentialOriginPolicy::canonicalHttpsOrigin)
            ?: return GeckoResult.fromValue(request.dismiss())
        val options = safeOptions.filter {
            CredentialOriginPolicy.canonicalHttpsOrigin(it.value.origin) == firstOrigin
        }
        if (options.isEmpty() || !isStillValid(session, firstOrigin)) {
            return GeckoResult.fromValue(request.dismiss())
        }

        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val labels = options.map { it.value.username }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(translate("Choose a saved sign-in"))
            .setItems(labels) { _, index ->
                val selected = options.getOrNull(index)
                val selectedOrigin = selected?.value?.origin?.let(CredentialOriginPolicy::canonicalHttpsOrigin)
                if (selected == null || selectedOrigin != firstOrigin || hasPrivateTabsOpen() || !isStillValid(session, firstOrigin)) {
                    result.complete(request.dismiss())
                } else {
                    BrowserDeviceAuthenticator.authenticate(
                        activity = activity,
                        title = "Unlock saved passwords",
                        subtitle = "Verify your identity to fill this sign-in",
                        onAuthenticated = {
                            onDeviceAuthenticated()
                            result.complete(if (!hasPrivateTabsOpen() && isStillValid(session, firstOrigin)) request.confirm(selected) else request.dismiss())
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
