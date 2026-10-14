package com.baloch.ai.gesture

import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3

/**
 * Voice commands that control CAMERA hand gestures — trilingual, including
 * Roman Urdu (Kamran types/speaks Roman Urdu, so it lives in the patterns,
 * not as a separate locale).
 *
 * Parent wiring: match in the brain BEFORE the generic fallback; on
 * [GestureCmd.Enable] call `GestureControlService.setMasterEnabled(ctx, true)`
 * (the service itself gates firing behind calibration); on [Disable] pass
 * false; on [Status] read the service status / calibration state and reply
 * with [replyFor].
 */
sealed interface GestureCmd {
    /** "gesture control on" / "hand gesture band kar do"→off … */
    data object Enable : GestureCmd
    data object Disable : GestureCmd
    data object Status : GestureCmd
}

/**
 * Matches a lowercased input string against camera-gesture commands.
 * Returns null when the input isn't about hand gestures at all.
 *
 * NOTE: sensor gestures ("sensor wale gesture") are matched by
 * [SensorGestureIntents.matchSensorGestureCommand] — this matcher ignores
 * inputs that mention "sensor" so the two never collide.
 */
fun matchGestureCommand(input: String): GestureCmd? {
    val l = input.lowercase().trim()
    if (l.contains("sensor")) return null

    val mentionsGesture = l.contains("gesture") ||
        l.contains("hand gesture") ||
        l.contains("haath") || // Roman Urdu "haath ka ishara"
        l.contains("اشار") || // Urdu script (اشارہ)
        l.contains("इशारा") // Devanagari
    if (!mentionsGesture) return null

    // --- DISABLE first: "band karo" must never fall through to enable.
    val wantsDisable = l.contains("off") ||
        l.contains("disable") ||
        l.contains("deactivate") ||
        l.contains("stop") ||
        l.contains("band kar") ||
        l.contains("بند کر") ||
        l.contains("बंद कर")
    if (wantsDisable) return GestureCmd.Disable

    // --- STATUS before enable: "is gesture control on?" is read-only.
    val wantsStatus = l.contains("status") ||
        l.contains("haal") ||
        l.contains("state") ||
        l.contains("on hai") ||
        l.contains("enabled hai") ||
        l.trimEnd().endsWith("?") ||
        l.contains("kaam kar") // "gesture kaam kar raha hai?"
    if (wantsStatus) return GestureCmd.Status

    // --- ENABLE.
    val wantsEnable = l.contains("on") ||
        l.contains("enable") ||
        l.contains("activate") ||
        l.contains("start") ||
        l.contains("chalu") ||
        l.contains("chalo") ||
        l.contains("shuru") ||
        l.contains("کھول") ||
        l.contains("चालू")
    if (wantsEnable) return GestureCmd.Enable

    return null
}

/** Trilingual replies for gesture-control commands. */
fun gestureReply(
    cmd: GestureCmd,
    enabled: Boolean,
    calibrated: Boolean,
    lang: BalochLanguage,
): String = when (cmd) {
    GestureCmd.Enable -> L3(
        en = if (calibrated)
            "Hand gestures on, sir — I know your hand. ✋"
        else
            "Hand gestures on, sir — but first a 10-second calibration so I learn YOUR hand. Open Settings → Gestures → Calibrate.",
        ur = if (calibrated)
            "ہینڈ جیسچر آن، سر — میں آپ کا ہاتھ پہچانتا ہوں۔ ✋"
        else
            "ہینڈ جیسچر آن، سر — پہلے 10 سیکنڈ کیلیبریشن تاکہ میں آپ کا ہاتھ سیکھ لوں۔ سیٹنگز → جیسچرز → کیلیبریٹ کھولیں۔",
        hi = if (calibrated)
            "हैंड जेस्चर ऑन, सर — मैं आपका हाथ पहचानता हूँ। ✋"
        else
            "हैंड जेस्चर ऑन, सर — पहले 10 सेकंड का कैलिब्रेशन ताकि मैं आपका हाथ सीख लूँ। सेटिंग्स → जेस्चर्स → कैलिब्रेट खोलिए।",
    ).pick(lang)

    GestureCmd.Disable -> L3(
        en = "Hand gestures off, sir. The camera is released. 📷",
        ur = "ہینڈ جیسچر آف، سر۔ کیمرہ بند کر دیا۔ 📷",
        hi = "हैंड जेस्चर ऑफ, सर। कैमरा बंद कर दिया। 📷",
    ).pick(lang)

    GestureCmd.Status -> L3(
        en = if (enabled)
            "Camera gestures are active${if (calibrated) " and calibrated to your hand" else " — calibration pending"}. 👆"
        else
            "Camera gestures are off, sir. Say \"gesture control on\" any time.",
        ur = if (enabled)
            "کیمرہ جیسچر فعال ہیں${if (calibrated) " اور آپ کے ہاتھ سے کیلیبریٹڈ" else " — کیلیبریشن باقی"}۔ 👆"
        else
            "کیمرہ جیسچر آف ہیں، سر۔ \"gesture control on\" کہیں جب چاہیں۔",
        hi = if (enabled)
            "कैमरा जेस्चर सक्रिय हैं${if (calibrated) " और आपके हाथ से कैलिब्रेटेड" else " — कैलिब्रेशन बाकी"}। 👆"
        else
            "कैमरा जेस्चर ऑफ हैं, सर। कभी भी \"gesture control on\" कहिए।",
    ).pick(lang)
}
