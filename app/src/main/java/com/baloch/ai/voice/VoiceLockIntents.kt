package com.baloch.ai.voice

/**
 * Voice commands that control Voice Lock itself — trilingual, including
 * Roman Urdu (Kamran types/speaks Roman Urdu, so it lives in the patterns,
 * not as a separate locale).
 *
 * SECURITY CONTRACT — read carefully:
 *  - [VoiceLockCmd.Enable] and [VoiceLockCmd.Status] are safe to act on.
 *  - [VoiceLockCmd.DisableRequest] is NOT an order to disable. Actually
 *    turning the lock OFF requires proof of identity first: the voiceprint
 *    must verify on a fresh utterance (VoiceLockManager.canDisable()) OR
 *    the PIN must be entered (PinManager.checkPin(), via the UI). The
 *    PARENT (brain wiring) enforces voice-OR-PIN before it ever calls
 *    VoiceLockManager.setEnabled(false). A stranger saying "voice lock
 *    band karo" must NEVER disable the lock — that would defeat its
 *    entire purpose.
 */
sealed interface VoiceLockCmd {
    /** "voice lock on" / "voice lock lagao" — safe: enabling needs enrollment anyway. */
    data object Enable : VoiceLockCmd

    /**
     * "voice lock off" / "voice lock band karo" — REQUEST ONLY.
     * Parent must pass verification or PIN before disabling.
     */
    data object DisableRequest : VoiceLockCmd

    /** "voice lock status" — read-only report. */
    data object Status : VoiceLockCmd
}

/**
 * Matches a lowercased input string against voice-lock commands.
 * Returns null when the input isn't about Voice Lock at all.
 */
fun matchVoiceLock(input: String): VoiceLockCmd? {
    val l = input.lowercase().trim()

    // The lock's name in all three scripts + Roman Urdu spellings.
    val mentionsLock = l.contains("voice lock") ||
        l.contains("وائس لاک") || // Urdu script
        l.contains("वॉइस लॉक") || // Devanagari
        l.contains("voice laak") ||
        l.contains("vois lock")

    if (!mentionsLock) return null

    // --- DISABLE: checked FIRST so "band karo" never falls through to enable.
    val wantsDisable = l.contains("off") ||
        l.contains("disable") ||
        l.contains("deactivate") ||
        l.contains("band karo") ||
        l.contains("band kar") ||
        l.contains("بند کرو") ||
        l.contains("बंद करो") ||
        l.contains("mute")
    if (wantsDisable) return VoiceLockCmd.DisableRequest

    // --- STATUS: read-only, checked before enable ("is voice lock on?" must
    // not flip the toggle).
    val wantsStatus = l.contains("status") ||
        l.contains("state") ||
        l.contains("kia hai") ||
        l.contains("کیا ہے") ||
        l.contains("क्या है") ||
        l.contains("on hai") && (l.contains("?") || l.contains("kya"))
    if (wantsStatus) return VoiceLockCmd.Status

    // --- ENABLE ---
    val wantsEnable = l.contains("on") ||
        l.contains("enable") ||
        l.contains("activate") ||
        l.contains("lagao") ||
        l.contains("لگاؤ") ||
        l.contains("लगाओ") ||
        l.contains("chalu") ||
        l.contains("چالو") ||
        l.contains("चालू") ||
        l.contains("start")
    if (wantsEnable) return VoiceLockCmd.Enable

    // Mentions the lock but no clear verb → treat as a status question
    // (safest default: read-only, never toggles anything).
    return VoiceLockCmd.Status
}
