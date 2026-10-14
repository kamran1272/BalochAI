package com.baloch.ai.voice

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Optional BIOMETRIC APP LOCK — a second hard guarantee behind Voice Lock.
 *
 * When enabled (OFF by default; the parent wires the settings toggle),
 * opening the Baloch app itself requires the device's biometric (fingerprint
 * / face) or the device PIN/pattern/password. This protects everything even
 * if someone gets past the voice check with a recording — they still can't
 * open the app without Kamran's finger or device credential.
 *
 * Uses androidx.biometric:biometric:1.1.0 (already in build.gradle).
 * Note: when DEVICE_CREDENTIAL is allowed as a fallback, the prompt must
 * NOT set a negative button (the library throws otherwise) — the system
 * draws its own "use PIN" affordance.
 */
object BiometricLock {

    /** True when the device can actually do biometric-or-device-credential auth. */
    fun canUseBiometric(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return manager.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows the system biometric prompt. [onOk] fires only on a successful
     * authentication; [onFail] gets a human-readable reason otherwise
     * (user cancelled, lockout, no biometrics enrolled, …).
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        onOk: () -> Unit,
        onFail: (String) -> Unit,
    ) {
        val executor = ContextCompat.getExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    onOk()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Includes user-cancel and lockout — surfaced, never silent.
                    onFail(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    onFail("Biometric not recognised — try again.")
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Confirm it is you to open Baloch.")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()
        biometricPrompt.authenticate(promptInfo)
    }
}
