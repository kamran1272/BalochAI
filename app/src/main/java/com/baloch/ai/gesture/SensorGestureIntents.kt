package com.baloch.ai.gesture

import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3

/**
 * Voice commands that control SENSOR gestures — trilingual, including
 * Roman Urdu.
 *
 * Separate toggle from camera gestures (Kamran's order): "sensor wale
 * gesture" never touches the camera pipeline, and "hand gesture" never
 * touches the sensors. [matchGestureCommand] (camera) ignores inputs
 * mentioning "sensor", and this matcher REQUIRES the word "sensor".
 *
 * Parent wiring: on [SensorGestureCmd.Enable] call
 * `SensorGestureService.setEnabled(ctx, true)`; on [Disable] pass false;
 * on [Status] reply with [sensorGestureReply].
 */
sealed interface SensorGestureCmd {
    data object Enable : SensorGestureCmd
    data object Disable : SensorGestureCmd
    data object Status : SensorGestureCmd
}

/**
 * Matches a lowercased input string against sensor-gesture commands.
 * Returns null when the input isn't about sensor gestures.
 */
fun matchSensorGestureCommand(input: String): SensorGestureCmd? {
    val l = input.lowercase().trim()
    if (!l.contains("sensor")) return null

    val mentionsGesture = l.contains("gesture") ||
        l.contains("ishara") || // Roman Urdu
        l.contains("اشار") ||
        l.contains("इशारा")
    if (!mentionsGesture) return null

    // --- DISABLE first.
    val wantsDisable = l.contains("off") ||
        l.contains("disable") ||
        l.contains("deactivate") ||
        l.contains("stop") ||
        l.contains("band kar") ||
        l.contains("بند کر") ||
        l.contains("बंद कर")
    if (wantsDisable) return SensorGestureCmd.Disable

    // --- STATUS before enable.
    val wantsStatus = l.contains("status") ||
        l.contains("haal") ||
        l.contains("state") ||
        l.contains("on hai") ||
        l.contains("enabled hai") ||
        l.trimEnd().endsWith("?")
    if (wantsStatus) return SensorGestureCmd.Status

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
    if (wantsEnable) return SensorGestureCmd.Enable

    return null
}

/** Trilingual replies for sensor-gesture commands. */
fun sensorGestureReply(
    cmd: SensorGestureCmd,
    enabled: Boolean,
    lang: BalochLanguage,
): String = when (cmd) {
    SensorGestureCmd.Enable -> L3(
        en = "Sensor gestures on, sir — wave, flip and shake are listening. They work in the dark, even with the screen off. 📳",
        ur = "سینسر جیسچر آن، سر — لہرانا، پلٹنا اور ہلانا سن رہے ہیں۔ اندھیرے میں بھی، اسکرین آف پر بھی۔ 📳",
        hi = "सेंसर जेस्चर ऑन, सर — वेव, फ़्लिप और शेक सुन रहे हैं। अँधेरे में भी, स्क्रीन ऑफ़ पर भी। 📳",
    ).pick(lang)

    SensorGestureCmd.Disable -> L3(
        en = "Sensor gestures off, sir.",
        ur = "سینسر جیسچر آف، سر۔",
        hi = "सेंसर जेस्चर ऑफ, सर।",
    ).pick(lang)

    SensorGestureCmd.Status -> L3(
        en = if (enabled)
            "Sensor gestures are active — wave to wake me, flip to silence, shake for your mapped action. 📳"
        else
            "Sensor gestures are off, sir. Say \"sensor gestures on\" any time.",
        ur = if (enabled)
            "سینسر جیسچر فعال ہیں — جگانے کے لیے لہرائیں، خاموش کرنے کے لیے پلٹیں۔ 📳"
        else
            "سینسر جیسچر آف ہیں، سر۔ \"sensor gestures on\" کہیں جب چاہیں۔",
        hi = if (enabled)
            "सेंसर जेस्चर सक्रिय हैं — जगाने के लिए वेव करें, साइलेंट के लिए फ़्लिप करें। 📳"
        else
            "सेंसर जेस्चर ऑफ हैं, सर। कभी भी \"sensor gestures on\" कहिए।",
    ).pick(lang)
}
