package com.browsex.app

import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Owns the authenticated saved-password management interaction. */
internal class BrowserPasswordManagerCoordinator(
    private val activity: AppCompatActivity,
    private val vault: BrowserCredentialVault,
    private val isHostUnavailable: () -> Boolean,
    private val showMessage: (String) -> Unit,
    private val translate: (String) -> String
) {
    fun show() {
        BrowserDeviceAuthenticator.authenticate(
            activity = activity,
            title = "Unlock saved passwords",
            subtitle = "Verify your identity to view saved sign-ins",
            onAuthenticated = {
                if (isHostUnavailable()) return@authenticate
                vault.unlockForSession()
                val saved = vault.entries()
                if (saved.isEmpty()) {
                    showMessage(if (vault.privateBrowsingActive) "Close private tabs to view saved entries" else "No saved passwords yet")
                    return@authenticate
                }
                val labels = saved.map { it.username + " · " + Uri.parse(it.origin).host.orEmpty() }.toTypedArray()
                AlertDialog.Builder(activity).setTitle(translate("Saved passwords")).setItems(labels) { _, index ->
                    val item = saved.getOrNull(index) ?: return@setItems
                    BrowserDeviceAuthenticator.authenticate(
                        activity = activity,
                        title = "Reveal saved password",
                        subtitle = "Verify your identity to reveal or copy this sign-in",
                        onAuthenticated = {
                            if (isHostUnavailable()) return@authenticate
                            vault.unlockForSession()
                            val secret = vault.passwordFor(item.id)
                            if (secret == null) {
                                showMessage(if (vault.privateBrowsingActive) "Close private tabs to view saved entries" else "Saved entry unavailable")
                                return@authenticate
                            }
                            val dialog = AlertDialog.Builder(activity).setTitle(Uri.parse(item.origin).host.orEmpty())
                                .setMessage("Username: " + item.username + "\\nPassword: " + secret)
                                .setPositiveButton(translate("Copy password")) { _, _ ->
                                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Password", secret))
                                    showMessage("Password copied")
                                }
                                .setNegativeButton(translate("Close"), null)
                                .setNeutralButton(translate("Delete")) { _, _ ->
                                    if (vault.delete(item.id)) showMessage("Saved password deleted")
                                }.create()
                            dialog.setOnShowListener { dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                            dialog.show()
                        },
                        onRejected = { if (!isHostUnavailable()) showMessage("Authentication required") }
                    )
                }.setNegativeButton(translate("Close"), null).show()
            },
            onRejected = { if (!isHostUnavailable()) showMessage("Authentication required") }
        )
    }
}
