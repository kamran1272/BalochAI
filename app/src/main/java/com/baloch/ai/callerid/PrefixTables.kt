package com.baloch.ai.callerid

import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3

/**
 * Offline Pakistani number intelligence — hand-built lookup tables, all
 * in-memory, zero network, instant on the call-announcement hot path.
 *
 * Sources: PTA mobile operator prefix allocations and the public PTCL area
 * code list (Wikipedia "Telephone numbers in Pakistan" / "List of dialling
 * codes in Pakistan", cross-checked Oct 2026; 0459 = Mianwali verified via
 * telcode.info). Only high-confidence codes are included — anything else
 * resolves to "unknown", never a guess.
 *
 * Honest limit: this tells you the NETWORK and CITY, never the caller's
 * NAME. Name identification for unknown numbers needs a crowdsourced cloud
 * directory (Truecaller's closed/paid database) — Baloch does not have one
 * and does not pretend to. See CALLERID-DOCS-ADD.md.
 */
object PrefixTables {

    // ------------------------------------------------------------------
    // Mobile network prefixes (first 4 digits of the 0-prefixed number)
    // ------------------------------------------------------------------

    /** prefix4 -> network. Ranges per PTA allocation. */
    private val MOBILE_NETWORKS: Map<String, String> = buildMap {
        for (p in 300..309) put("0$p", "Jazz")
        for (p in 310..318) put("0$p", "Zong")
        for (p in 320..327) put("0$p", "Jazz")      // ex-Warid, merged into Jazz
        for (p in 330..337) put("0$p", "Ufone")
        for (p in 340..349) put("0$p", "Telenor")
        put("0355", "SCO")                          // Special Communications Org (AJK/GB)
        put("0370", "Zong")                         // Zong new series
    }

    // ------------------------------------------------------------------
    // Landline area codes -> city (longest-prefix match wins)
    // ------------------------------------------------------------------

    private val AREA_CODES: Map<String, String> = mapOf(
        // 4-digit codes first (checked before 3-digit ones)
        "0459" to "Mianwali",
        // Major metros
        "021" to "Karachi",
        "042" to "Lahore",
        "051" to "Islamabad / Rawalpindi",
        // Punjab
        "041" to "Faisalabad",
        "052" to "Sialkot",
        "053" to "Gujrat",
        "055" to "Gujranwala",
        "048" to "Sargodha",
        "061" to "Multan",
        "062" to "Bahawalpur",
        "068" to "Rahim Yar Khan",
        // Sindh
        "022" to "Hyderabad",
        "071" to "Sukkur",
        // Balochistan
        "081" to "Quetta",
        "086" to "Gwadar",
        // KPK
        "091" to "Peshawar",
    )

    // ------------------------------------------------------------------
    // Normalisation: +92 300 1234567 / 0092... / 92300... -> 03001234567
    // ------------------------------------------------------------------

    /** Normalise any Pakistani dial string to the canonical 0-prefixed form. */
    fun normalize(raw: String): String {
        var n = raw.trim().replace(Regex("[\\s\\-().]"), "")
        n = when {
            n.startsWith("+92") -> "0" + n.removePrefix("+92")
            n.startsWith("0092") -> "0" + n.removePrefix("0092")
            n.startsWith("92") && n.length == 12 -> "0" + n.removePrefix("92")
            else -> n
        }
        return n.replace(Regex("[^\\d]"), "")
    }

    /** True for Pakistani mobile numbers: 03XX XXXXXXX (11 digits). */
    fun isMobile(normalized: String): Boolean =
        normalized.length == 11 && normalized.startsWith("03")

    /** Network name for a mobile number, or null if the prefix is unknown. */
    fun networkOf(number: String): String? {
        val n = normalize(number)
        if (!isMobile(n)) return null
        return MOBILE_NETWORKS[n.take(4)]
    }

    /** City for a landline number (longest area-code match), or null. */
    fun cityOf(number: String): String? {
        val n = normalize(number)
        if (n.length < 6 || !n.startsWith("0") || isMobile(n)) return null
        // Longest prefix first so 0459 beats a hypothetical 045.
        return AREA_CODES.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { n.startsWith(it.key) }
            ?.value
    }

    // ------------------------------------------------------------------
    // Trilingual description
    // ------------------------------------------------------------------

    private val MOBILE_KNOWN = L3(
        en = "Sir, unknown number — it's a {network} mobile.",
        ur = "سر، نامعلوم نمبر ہے — {network} کا موبائل ہے",
        hi = "सर, अज्ञात नंबर है — {network} का मोबाइल है",
    )
    private val LANDLINE_KNOWN = L3(
        en = "Sir, unknown number — a {city} landline.",
        ur = "سر، نامعلوم نمبر ہے — {city} کا لینڈ لائن ہے",
        hi = "सर, अज्ञात नंबर है — {city} का लैंडलाइन है",
    )
    private val TOTALLY_UNKNOWN = L3(
        en = "Sir, unknown number — I can't place this one.",
        ur = "سر، نامعلوم نمبر ہے — اس کا پتا نہیں چل سکا",
        hi = "सर, अज्ञात नंबर है — इसका पता नहीं चल सका",
    )

    /**
     * One spoken line describing an unknown number, in the active language.
     * Never invents a name — network/city only.
     */
    fun describe(number: String, lang: BalochLanguage): String {
        val network = networkOf(number)
        if (network != null) return MOBILE_KNOWN.format(lang, "network" to network)
        val city = cityOf(number)
        if (city != null) return LANDLINE_KNOWN.format(lang, "city" to city)
        return TOTALLY_UNKNOWN.pick(lang)
    }
}
