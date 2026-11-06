package com.browsex.app

import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Requires a fresh system biometric or device-credential check before exposing a saved secret. */
internal object BrowserDeviceAuthenticator {
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onAuthenticated: () -> Unit,
        onRejected: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onRejected()
            return
        }
        val manager = BiometricManager.from(activity)
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        val allowed = if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
        val canAuthenticate = if (Build.VERSION.SDK_INT >= 30) {
            manager.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            manager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS || keyguard?.isDeviceSecure == true
        }
        if (!canAuthenticate) {
            onRejected()
            return
        }

        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).apply {
            if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(allowed)
            else setDeviceCredentialAllowed(true)
        }.build()
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onRejected()
                }
            }
        )
        prompt.authenticate(info)
    }
}
