package com.baloch.ai.power

/**
 * Trilingual voice/text intent matching for the 10 dormant power features.
 *
 * Covers English, Urdu script, Devanagari AND Roman Urdu (Kamran types
 * and speaks Roman Urdu himself — e.g. "driving mode on karo",
 * "aaj ka lafz", "meeting record karo", "petrol price 278",
 * "dollar 290 par alert").
 *
 * [matchPower] only IDENTIFIES the intent — the parent executes it via
 * [PowerToggles.applyHit] (toggles/workers/replies all live there), so
 * there is exactly one execution path for voice and settings alike.
 */
enum class PowerFeature {
    DRIVING, RAMADAN, CRICKET, CURRENCY, PETROL, WORD, RECORDER, CHARGE, RAIN, APPLOCK
}

enum class PowerCommand {
    TOGGLE_ON, TOGGLE_OFF, QUERY, TEACH,
}

data class PowerHit(
    val feature: PowerFeature,
    val command: PowerCommand,
    /** Number taught with TEACH (petrol price / currency threshold). */
    val number: Double? = null,
)

private val NUMBER = Regex("(\\d{2,5}(?:\\.\\d{1,2})?)")

/** Pulls the first plausible number out of free text. */
private fun extractNumber(input: String): Double? =
    NUMBER.find(input)?.groupValues?.get(1)?.toDoubleOrNull()

private fun has(input: String, vararg needles: String): Boolean =
    needles.any { input.contains(it) }

/**
 * Returns the matched power intent, or null when the input is not a
 * power-feature command. Order matters: TEACH patterns (with numbers)
 * and specific phrases are checked before bare feature words.
 */
fun matchPower(rawInput: String): PowerHit? {
    val input = rawInput.trim().lowercase()
    if (input.isBlank()) return null

    // -- petrol: "petrol price 278" / "petrol 292" (TEACH first) ---------
    if (has(input, "petrol", "پیٹرول", "پٹرول", "पेट्रोल")) {
        val n = extractNumber(input)
        if (n != null) return PowerHit(PowerFeature.PETROL, PowerCommand.TEACH, n)
        if (has(input, "on", "آن", "चालू", "chalu karo", "lagao")) return PowerHit(PowerFeature.PETROL, PowerCommand.TOGGLE_ON)
        if (has(input, "off", "بند", "बंद", "band karo", "hatao")) return PowerHit(PowerFeature.PETROL, PowerCommand.TOGGLE_OFF)
        return PowerHit(PowerFeature.PETROL, PowerCommand.QUERY)
    }

    // -- currency: "dollar 290 par alert" (TEACH) / "dollar rate" ---------
    if (has(input, "dollar", "ڈالر", "डॉलर", "usd")) {
        val n = extractNumber(input)
        if (n != null && has(input, "alert", "الرٹ", "अलर्ट", "cross", "par", "پر", "پر")) {
            return PowerHit(PowerFeature.CURRENCY, PowerCommand.TEACH, n)
        }
        if (has(input, "on", "آن", "चालू", "alert lagao")) return PowerHit(PowerFeature.CURRENCY, PowerCommand.TOGGLE_ON)
        if (has(input, "off", "بند", "बंद", "alert band")) return PowerHit(PowerFeature.CURRENCY, PowerCommand.TOGGLE_OFF)
        return PowerHit(PowerFeature.CURRENCY, PowerCommand.QUERY)
    }

    // -- driving mode ----------------------------------------------------
    if (has(input, "driving", "ڈرائیونگ", "ड्राइविंग", "drive mode", "gaari", "گاڑی")) {
        if (has(input, "off", "بند", "बंद", "band karo", "khatam", "ختم")) return PowerHit(PowerFeature.DRIVING, PowerCommand.TOGGLE_OFF)
        if (has(input, "on", "آن", "चालू", "karo", "shuru", "شروع", "lagao", "لگاؤ")) return PowerHit(PowerFeature.DRIVING, PowerCommand.TOGGLE_ON)
        return PowerHit(PowerFeature.DRIVING, PowerCommand.QUERY)
    }

    // -- ramadan ---------------------------------------------------------
    if (has(input, "ramadan", "ramzan", "رمضان", "रमज़ान", "ramazan")) {
        if (has(input, "off", "بند", "बंद", "band")) return PowerHit(PowerFeature.RAMADAN, PowerCommand.TOGGLE_OFF)
        if (has(input, "mode", "موڈ", "मोड", "on", "آن", "चालू")) return PowerHit(PowerFeature.RAMADAN, PowerCommand.TOGGLE_ON)
        return PowerHit(PowerFeature.RAMADAN, PowerCommand.QUERY)
    }
    if (has(input, "sehri", "سحری", "सहरी", "iftari", "iftar", "افطاری", "इफ़्तार", "roza", "روزہ", "रोज़ा")) {
        return PowerHit(PowerFeature.RAMADAN, PowerCommand.QUERY)
    }

    // -- cricket ---------------------------------------------------------
    if (has(input, "cricket", "کرکٹ", "क्रिकेट", "pakistan match", "پاکستان میچ", "पाकिस्तान मैच", "pak ka match", "score")) {
        if (has(input, "off", "بند", "बंद", "band")) return PowerHit(PowerFeature.CRICKET, PowerCommand.TOGGLE_OFF)
        if (has(input, "on", "آن", "चालू", "alert", "الرٹ", "अलर्ट", "lagao")) return PowerHit(PowerFeature.CRICKET, PowerCommand.TOGGLE_ON)
        return PowerHit(PowerFeature.CRICKET, PowerCommand.QUERY)
    }

    // -- daily word ------------------------------------------------------
    if (has(input, "aaj ka lafz", "daily word", "word of the day", "آج کا لفظ", "आज का", "lafz", "لفظ", "new word")) {
        return PowerHit(PowerFeature.WORD, PowerCommand.QUERY)
    }

    // -- meeting recorder -------------------------------------------------
    if (has(input, "meeting record", "recording", "ریکارڈنگ", "रिकॉर्डिंग", "record karo")) {
        if (has(input, "band karo", "stop", "بند", "बंद", "khatam", "روکو")) return PowerHit(PowerFeature.RECORDER, PowerCommand.TOGGLE_OFF)
        return PowerHit(PowerFeature.RECORDER, PowerCommand.TOGGLE_ON)
    }

    // -- charge alert -----------------------------------------------------
    if (has(input, "charge", "charging", "چارج", "चार्ज", "battery full", "بیٹری فل")) {
        if (has(input, "off", "بند", "बंद", "band")) return PowerHit(PowerFeature.CHARGE, PowerCommand.TOGGLE_OFF)
        if (has(input, "on", "آن", "चालू", "alert", "الرٹ", "अलर्ट", "lagao")) return PowerHit(PowerFeature.CHARGE, PowerCommand.TOGGLE_ON)
        return PowerHit(PowerFeature.CHARGE, PowerCommand.QUERY)
    }

    // -- rain --------------------------------------------------------------
    if (has(input, "barish", "بارش", "rain", "बारीश", "बारिश", "umbrella", "چھتری")) {
        if (has(input, "off", "بند", "बंद", "band")) return PowerHit(PowerFeature.RAIN, PowerCommand.TOGGLE_OFF)
        if (has(input, "on", "آن", "चालू", "alert", "الرٹ", "अलर्ट", "lagao")) return PowerHit(PowerFeature.RAIN, PowerCommand.TOGGLE_ON)
        return PowerHit(PowerFeature.RAIN, PowerCommand.QUERY)
    }

    // -- app lock ------------------------------------------------------------
    if (has(input, "app lock", "ایپ لاک", "ऐप लॉक", "lock lagao", "لاک لگاؤ")) {
        if (has(input, "hatao", "remove", "off", "بند", "बंद", "ہٹاؤ")) return PowerHit(PowerFeature.APPLOCK, PowerCommand.TOGGLE_OFF)
        return PowerHit(PowerFeature.APPLOCK, PowerCommand.TOGGLE_ON)
    }

    return null
}
