package com.baloch.ai.power

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.baloch.ai.voice.BiometricLock
import com.baloch.ai.voice.PinManager

/**
 * APP LOCK — a gate before Baloch itself opens. 🔒
 *
 * OFF by default (Kamran's order — dormant until he wakes it). When ON,
 * the parent checks [isAppLockOn] in MainActivity (onCreate/onResume) and
 * calls [AppLockGate.requireUnlock] before showing content:
 *
 *  1. Device CAN do biometric/device-credential → system [BiometricPrompt]
 *     via the existing [BiometricLock] (reused, not duplicated).
 *  2. Otherwise (or on biometric failure) → the parent shows its PIN pad
 *     and verifies through the existing [PinManager] (reused).
 *
 * The PIN is the same one from Voice Lock setup — one PIN for the whole
 * app, hashed with SHA-256 in DataStore (see PinManager). If no PIN is set
 * and biometrics are unavailable, the gate tells the parent honestly that
 * there is nothing to lock with.
 */
object AppLock {

    fun isOn(context: Context): Boolean = PowerPrefs.isOn(context, PowerPrefs.APPLOCK)

    fun setOn(context: Context, on: Boolean) = PowerPrefs.setOn(context, PowerPrefs.APPLOCK, on)
}

/** Parent-facing check for MainActivity: `if (isAppLockOn(this)) gate…`. */
fun isAppLockOn(context: Context): Boolean = AppLock.isOn(context)

object AppLockGate {

    /**
     * Requires unlock before the app content is shown.
     *
     * @param onUnlocked show the app.
     * @param onPinNeeded biometric unavailable/failed — parent shows its
     *   PIN entry UI, verifies with `PinManager(ctx).checkPin(pin)`, and
     *   calls [onUnlocked] only on success.
     * @param onDenied biometric hard-failed AND no PIN path (nothing to
     *   fall back to, or user cancelled) — parent keeps the gate closed.
     */
    fun requireUnlock(
        activity: FragmentActivity,
        onUnlocked: () -> Unit,
        onPinNeeded: () -> Unit,
        onDenied: (reason: String) -> Unit,
    ) {
        val pin = PinManager(activity.applicationContext)
        if (BiometricLock.canUseBiometric(activity)) {
            BiometricLock.prompt(
                activity,
                title = "🔒 Baloch is locked",
                onOk = onUnlocked,
                onFail = { reason ->
                    // Biometric failed/cancelled — fall back to the PIN if
                    // one exists; otherwise the gate stays closed.
                    if (pin.hasPin()) onPinNeeded()
                    else onDenied(reason)
                },
            )
        } else if (pin.hasPin()) {
            onPinNeeded()
        } else {
            onDenied(
                "No biometric and no PIN set — enable one in Voice Lock settings first, sir."
            )
        }
    }
}
