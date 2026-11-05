package com.browsex.app

import android.app.Activity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.mozilla.geckoview.GeckoSession

/**
 * Owns the uBlock Origin install/enable/site-control/uninstall dialogs.
 * The activity supplies lifecycle, persistence and presentation callbacks.
 */
internal class BrowserUBlockSettingsCoordinator(
    private val activity: Activity,
    private val manager: () -> UBlockOriginManager?,
    private val store: BrowserStore,
    private val activeSession: () -> GeckoSession?,
    private val isHostUnavailable: () -> Boolean,
    private val showMessage: (String) -> Unit,
    private val translate: (String) -> String,
    private val dp: (Int) -> Int,
    private val textColor: Int,
    private val onSettingsChanged: () -> Unit
) {
    fun show() {
        val extensionManager = manager()
        if (extensionManager == null) {
            showMessage("uBlock Origin controls are unavailable")
            return
        }

        if (!extensionManager.isInstalled) {
            val privateConsent = CheckBox(activity).apply {
                text = translate("Allow uBlock Origin in private browsing")
                isChecked = false
                setPadding(dp(20), dp(8), dp(20), dp(8))
                minHeight = dp(48)
            }
            val explanation = TextView(activity).apply {
                text = translate("uBlock Origin can read and modify website content and requests to block ads, trackers and known anti-blocker scripts.")
                textSize = 14f
                setTextColor(textColor)
                setPadding(dp(20), dp(14), dp(20), dp(8))
            }
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(explanation)
                addView(privateConsent)
            }
            AlertDialog.Builder(activity)
                .setTitle(translate("Install uBlock Origin?"))
                .setView(content)
                .setNegativeButton(translate("Cancel"), null)
                .setPositiveButton(translate("Install")) { _, _ ->
                    extensionManager.install(privateConsent.isChecked) { installed, error ->
                        activity.runOnUiThread {
                            if (isHostUnavailable()) return@runOnUiThread
                            if (error != null || installed == null) {
                                android.util.Log.w("BrowseX", "uBlock Origin installation failed", error)
                                showMessage("Could not install uBlock Origin")
                            } else {
                                val enabled = installed.metaData?.enabled == true
                                val privateAllowed = enabled && installed.metaData?.allowedInPrivateBrowsing == true
                                persistState(true, enabled, privateAllowed)
                                BrowserContentProtectionPolicy.configure(enabled, privateAllowed)
                                showMessage("uBlock Origin installed")
                                onSettingsChanged()
                            }
                        }
                    }
                }
                .show()
            return
        }

        val enabled = extensionManager.isEnabled
        val message = if (enabled) {
            translate("uBlock Origin is enabled. Use its site control to pause blocking only for a site that refuses to load.")
        } else {
            translate("uBlock Origin is installed but disabled.")
        }
        AlertDialog.Builder(activity)
            .setTitle(translate("uBlock Origin"))
            .setMessage(message)
            .setPositiveButton(translate(if (enabled) "Disable" else "Enable")) { _, _ ->
                extensionManager.setEnabled(!enabled) { updated, error ->
                    activity.runOnUiThread {
                        if (isHostUnavailable()) return@runOnUiThread
                        if (error != null || updated == null) {
                            android.util.Log.w("BrowseX", "Could not update uBlock Origin", error)
                            showMessage("Could not update uBlock Origin")
                        } else {
                            val nowEnabled = updated.metaData?.enabled == true
                            val privateAllowed = nowEnabled && updated.metaData?.allowedInPrivateBrowsing == true
                            persistState(true, nowEnabled, privateAllowed)
                            BrowserContentProtectionPolicy.configure(nowEnabled, privateAllowed)
                            showMessage(if (nowEnabled) "uBlock Origin enabled" else "uBlock Origin disabled")
                            onSettingsChanged()
                        }
                    }
                }
            }
            .setNeutralButton(translate("Open site controls")) {
                val session = activeSession()
                if (session != null && extensionManager.hasBrowserAction(session)) {
                    extensionManager.clickBrowserAction(session)
                } else {
                    showMessage("uBlock Origin controls are unavailable for this site")
                }
            }
            .setNegativeButton(translate("Remove uBlock Origin")) {
                AlertDialog.Builder(activity)
                    .setTitle(translate("Uninstall uBlock Origin?"))
                    .setMessage(translate("uBlock Origin will be removed. GeckoView tracking protection will be restored."))
                    .setNegativeButton(translate("Cancel"), null)
                    .setPositiveButton(translate("Remove")) { _, _ ->
                        extensionManager.uninstall { error ->
                            activity.runOnUiThread {
                                if (isHostUnavailable()) return@runOnUiThread
                                if (error != null) {
                                    android.util.Log.w("BrowseX", "Could not uninstall uBlock Origin", error)
                                    showMessage("Could not uninstall uBlock Origin")
                                } else {
                                    persistState(false, false, false)
                                    BrowserContentProtectionPolicy.configure(false, false)
                                    showMessage("uBlock Origin removed")
                                    onSettingsChanged()
                                }
                            }
                        }
                    }
                    .show()
            }
            .show()
    }

    private fun persistState(installed: Boolean, enabled: Boolean, privateAllowed: Boolean) {
        store.adBlockInstalled = installed
        store.adBlockEnabled = enabled
        store.adBlockPrivateEnabled = privateAllowed
    }
}
