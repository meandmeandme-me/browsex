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
    private fun canManagePasswords(): Boolean {
        if (isHostUnavailable()) return false
        if (vault.privateBrowsingActive) {
            showMessage(translate("Close private tabs to view saved entries"))
            return false
        }
        return true
    }

    fun show() {
        if (!canManagePasswords()) return
        BrowserDeviceAuthenticator.authenticate(
            activity = activity,
            title = translate("Unlock saved passwords"),
            subtitle = translate("Verify your identity to view saved sign-ins"),
            onAuthenticated = {
                if (!canManagePasswords()) return@authenticate
                vault.unlockForSession()
                val saved = vault.entries()
                if (saved.isEmpty()) {
                    showMessage(if (vault.privateBrowsingActive) "Close private tabs to view saved entries" else translate("No saved passwords yet"))
                    return@authenticate
                }
                val labels = saved.map { it.username + " · " + Uri.parse(it.origin).host.orEmpty() }.toTypedArray()
                AlertDialog.Builder(activity).setTitle(translate("Saved passwords")).setItems(labels) { _, index ->
                    if (!canManagePasswords()) return@setItems
                    val item = saved.getOrNull(index) ?: return@setItems
                    BrowserDeviceAuthenticator.authenticate(
                        activity = activity,
                        title = translate("Reveal saved password"),
                        subtitle = translate("Verify your identity to reveal or copy this sign-in"),
                        onAuthenticated = {
                            if (!canManagePasswords()) return@authenticate
                            vault.unlockForSession()
                            val secret = vault.passwordFor(item.id)
                            if (secret == null) {
                                showMessage(if (vault.privateBrowsingActive) "Close private tabs to view saved entries" else translate("Saved entry unavailable"))
                                return@authenticate
                            }
                            val dialog = AlertDialog.Builder(activity).setTitle(Uri.parse(item.origin).host.orEmpty())
                                .setMessage(translate("Username") + ": " + item.username + "\n" + translate("Password") + ": " + secret)
                                .setPositiveButton(translate("Copy password")) { _, _ ->
                                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText(translate("Password"), secret)
                                    clip.description.extras = android.os.PersistableBundle().apply {
                                        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                                    }
                                    clipboard.setPrimaryClip(clip)
                                    showMessage(translate("Password copied"))
                                }
                                .setNegativeButton(translate("Close"), null)
                                .setNeutralButton(translate("Delete")) { _, _ ->
                                    if (!canManagePasswords()) return@setNeutralButton
                                    if (vault.delete(item.id)) showMessage(translate("Saved password deleted"))
                                }.create()
                            dialog.setOnShowListener { dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                            dialog.show()
                        },
                        onRejected = { if (!isHostUnavailable()) showMessage(translate("Authentication required")) }
                    )
                }.setNegativeButton(translate("Close"), null).show()
            },
            onRejected = { if (!isHostUnavailable()) showMessage("Authentication required") }
        )
    }
}
