package com.browsex.app

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.util.WeakHashMap

/**
 * Installs and hosts the official, Mozilla-signed uBlock Origin WebExtension.
 *
 * The XPI is fetched from Mozilla Add-ons at the user's request; it is not bundled
 * into BrowseX or modified by this project.
 */
internal class UBlockOriginManager(
    private val activity: AppCompatActivity,
    private val runtime: GeckoRuntime,
    private val activeSession: () -> GeckoSession?,
    private val existingSessions: () -> List<GeckoSession>,
    private val createTab: (GeckoSession) -> GeckoResult<GeckoSession>?,
    private val closeTab: (GeckoSession) -> Boolean,
    private val onActionChanged: () -> Unit
) : WebExtension.ActionDelegate {
    private val controller: WebExtensionController = runtime.webExtensionController
    private val sessionActions = WeakHashMap<GeckoSession, WebExtension.Action>()
    private var defaultAction: WebExtension.Action? = null
    private var extension: WebExtension? = null
    private var installConsentPending = false
    private var privateBrowsingConsent = false
    private var destroyed = false

    private var popupWindow: PopupWindow? = null
    private var popupSession: GeckoSession? = null
    private var popupView: GeckoView? = null

    val installedExtension: WebExtension? get() = extension
    val isInstalled: Boolean get() = extension != null
    val isEnabled: Boolean get() = extension?.metaData?.enabled == true
    val isAllowedInPrivateBrowsing: Boolean
        get() = extension?.metaData?.allowedInPrivateBrowsing == true

    private val promptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            val accepted = !destroyed &&
                installConsentPending &&
                extension.id == EXTENSION_ID
            val privateAllowed = accepted && privateBrowsingConsent
            installConsentPending = false
            return GeckoResult.fromValue(
                WebExtension.PermissionPromptResponse(
                    accepted,
                    privateAllowed,
                    false
                )
            )
        }

        override fun onOptionalPrompt(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>
        ): GeckoResult<AllowOrDeny> = GeckoResult.deny()
    }

    private val tabDelegate = object : WebExtension.TabDelegate {
        override fun onNewTab(
            source: WebExtension,
            createDetails: WebExtension.CreateTabDetails
        ): GeckoResult<GeckoSession> {
            val parent = activeSession() ?: return GeckoResult.fromValue(null)
            return createTab(parent) ?: GeckoResult.fromValue(null)
        }
    }

    private val sessionTabDelegate = object : WebExtension.SessionTabDelegate {
        override fun onCloseTab(
            source: WebExtension?,
            session: GeckoSession
        ): GeckoResult<AllowOrDeny> {
            return if (closeTab(session)) GeckoResult.allow() else GeckoResult.deny()
        }

        override fun onUpdateTab(
            extension: WebExtension,
            session: GeckoSession,
            details: WebExtension.UpdateTabDetails
        ): GeckoResult<AllowOrDeny> {
            return if (existingSessions().any { it === session }) {
                GeckoResult.allow()
            } else {
                GeckoResult.deny()
            }
        }
    }

    init {
        controller.setPromptDelegate(promptDelegate)
    }

    fun refresh(onComplete: (WebExtension?) -> Unit, onFailure: (Throwable) -> Unit = {}) {
        if (destroyed) return
        controller.list().accept(
            { extensions ->
                if (destroyed) return@accept
                extension = extensions.orEmpty().firstOrNull { it.id == EXTENSION_ID }
                extension?.let(::registerExtension)
                onComplete(extension)
                onActionChanged()
            },
            { error ->
                if (!destroyed) onFailure(error ?: IllegalStateException("Could not list installed extensions"))
            }
        )
    }

    fun install(allowPrivateBrowsing: Boolean, onComplete: (WebExtension?, Throwable?) -> Unit) {
        if (destroyed) {
            onComplete(null, IllegalStateException("uBlock manager is no longer available"))
            return
        }
        if (extension != null) {
            onComplete(extension, null)
            return
        }
        installConsentPending = true
        privateBrowsingConsent = allowPrivateBrowsing
        controller.install(
            AMO_XPI_URL,
            WebExtensionController.INSTALLATION_METHOD_MANAGER
        ).accept(
            { installed ->
                installConsentPending = false
                if (destroyed) {
                    onComplete(null, IllegalStateException("BrowseX closed during installation"))
                } else if (installed == null) {
                    onComplete(null, IllegalStateException("Mozilla Add-ons returned no extension"))
                } else if (installed.id != EXTENSION_ID) {
                    controller.uninstall(installed).accept(
                        { onComplete(null, IllegalStateException("Unexpected extension identity")) },
                        { error -> onComplete(null, error ?: IllegalStateException("Could not remove unexpected extension")) }
                    )
                } else {
                    extension = installed
                    registerExtension(installed)
                    onComplete(installed, null)
                    onActionChanged()
                }
            },
            { error ->
                installConsentPending = false
                if (!destroyed) onComplete(null, error)
            }
        )
    }

    fun setEnabled(enabled: Boolean, onComplete: (WebExtension?, Throwable?) -> Unit) {
        val current = extension
        if (destroyed || current == null) {
            onComplete(null, IllegalStateException("uBlock Origin is not installed"))
            return
        }
        val operation = if (enabled) {
            controller.enable(current, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(current, WebExtensionController.EnableSource.USER)
        }
        operation.accept(
            { updated ->
                if (!destroyed) {
                    extension = updated
                    registerExtension(updated)
                    onComplete(updated, null)
                    onActionChanged()
                }
            },
            { error -> if (!destroyed) onComplete(null, error ?: IllegalStateException("uBlock operation failed")) }
        )
    }

    fun uninstall(onComplete: (Throwable?) -> Unit) {
        val current = extension
        if (destroyed || current == null) {
            onComplete(IllegalStateException("uBlock Origin is not installed"))
            return
        }
        closePopup()
        controller.uninstall(current).accept(
            {
                if (!destroyed) {
                    extension = null
                    defaultAction = null
                    sessionActions.clear()
                    existingSessions().forEach { detachSession(it, current) }
                    onActionChanged()
                    onComplete(null)
                }
            },
            { error -> if (!destroyed) onComplete(error ?: IllegalStateException("uBlock uninstall failed")) }
        )
    }

    fun attachSession(session: GeckoSession) {
        val current = extension ?: return
        if (destroyed) return
        session.webExtensionController.setTabDelegate(current, sessionTabDelegate)
        session.webExtensionController.setActionDelegate(current, this)
    }

    fun setTabActive(session: GeckoSession, active: Boolean) {
        if (destroyed || extension == null) return
        controller.setTabActive(session, active)
    }

    fun hasBrowserAction(session: GeckoSession?): Boolean {
        if (extension == null || !isEnabled) return false
        val action = resolveAction(session) ?: return false
        return action.enabled == true
    }

    fun clickBrowserAction(session: GeckoSession?) {
        if (destroyed || !isEnabled) return
        resolveAction(session)?.takeIf { it.enabled != false }?.click()
    }

    private fun resolveAction(session: GeckoSession?): WebExtension.Action? {
        val local = session?.let { sessionActions[it] }
        return if (local != null) {
            defaultAction?.let(local::withDefault) ?: local
        } else {
            defaultAction
        }
    }

    private fun registerExtension(value: WebExtension) {
        if (destroyed || value.id != EXTENSION_ID) return
        value.setActionDelegate(this)
        value.setTabDelegate(tabDelegate)
        existingSessions().forEach(::attachSession)
    }

    override fun onBrowserAction(
        extension: WebExtension,
        session: GeckoSession?,
        action: WebExtension.Action
    ) {
        if (extension.id != EXTENSION_ID || destroyed) return
        if (session == null) defaultAction = action else sessionActions[session] = action
        onActionChanged()
    }

    override fun onPageAction(
        extension: WebExtension,
        session: GeckoSession?,
        action: WebExtension.Action
    ) {
        if (extension.id != EXTENSION_ID || destroyed) return
        if (session == null) defaultAction = action else sessionActions[session] = action
        onActionChanged()
    }

    override fun onTogglePopup(
        extension: WebExtension,
        action: WebExtension.Action
    ): GeckoResult<GeckoSession>? {
        if (extension.id != EXTENSION_ID || destroyed) return null
        if (popupWindow?.isShowing == true) {
            closePopup()
            return null
        }
        return openPopup()
    }

    override fun onOpenPopup(
        extension: WebExtension,
        action: WebExtension.Action
    ): GeckoResult<GeckoSession>? {
        if (extension.id != EXTENSION_ID || destroyed) return null
        if (popupWindow?.isShowing == true) closePopup()
        return openPopup()
    }

    private fun openPopup(): GeckoResult<GeckoSession>? {
        val current = extension ?: return null
        if (!isEnabled || activity.isFinishing || activity.isDestroyed) return null

        val session = GeckoSession()
        val view = GeckoView(activity)
        val window = PopupWindow(activity)
        val width = (activity.resources.displayMetrics.widthPixels * 0.92f).toInt()
            .coerceAtMost(dp(420))
        val height = (activity.resources.displayMetrics.heightPixels * 0.72f).toInt()
            .coerceAtMost(dp(560))

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) {
                closePopup()
            }
        }

        return try {
            session.open(runtime)
            attachSession(session)
            view.setSession(session)
            window.contentView = view
            window.width = width
            window.height = height
            window.isFocusable = true
            window.isTouchable = true
            window.isOutsideTouchable = true
            window.setBackgroundDrawable(ColorDrawable(Color.WHITE))
            window.elevation = dp(18).toFloat()
            window.setOnDismissListener {
                if (popupWindow === window) {
                    popupWindow = null
                    popupSession = null
                    popupView = null
                }
                runCatching { view.releaseSession() }
                runCatching { session.close() }
            }

            popupWindow = window
            popupSession = session
            popupView = view
            window.showAtLocation(
                activity.window.decorView,
                Gravity.TOP or Gravity.END,
                dp(10),
                dp(76)
            )
            GeckoResult.fromValue(session)
        } catch (error: RuntimeException) {
            runCatching { view.releaseSession() }
            runCatching { session.close() }
            null
        }
    }

    fun closePopup() {
        val window = popupWindow ?: return
        val session = popupSession
        val view = popupView
        popupWindow = null
        popupSession = null
        popupView = null
        window.setOnDismissListener(null)
        if (window.isShowing) runCatching { window.dismiss() }
        if (view != null) runCatching { view.releaseSession() }
        if (session != null) runCatching { session.close() }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        closePopup()
        controller.setPromptDelegate(null)
        extension?.let { current ->
            current.setActionDelegate(null)
            current.setTabDelegate(null)
            existingSessions().forEach { detachSession(it, current) }
        }
        extension = null
        defaultAction = null
        sessionActions.clear()
    }

    private fun detachSession(session: GeckoSession, current: WebExtension) {
        runCatching {
            session.webExtensionController.setTabDelegate(current, null)
            session.webExtensionController.setActionDelegate(current, null)
        }
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    companion object {
        const val EXTENSION_ID = "uBlock0@raymondhill.net"
        const val AMO_XPI_URL =
            "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
    }
}
