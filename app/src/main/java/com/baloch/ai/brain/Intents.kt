package com.baloch.ai.brain

/**
 * Multilingual intent parsing — English, Roman Urdu, Urdu script, Hindi.
 *
 * Roman Urdu gets first-class treatment because Kamran types and speaks
 * it himself ("whatsapp kholo", "alarm lagao subah 7 baje"). Spelling
 * varies, so patterns use flexible endings: khol[o]?, lag?a?o?, kar?o?.
 *
 * The input arriving here is already trimmed + lowercased (a no-op for
 * Urdu script and Devanagari, which have no case).
 */
internal object Intents {

    /** What the parser recognised. [Action] carries a device effect; the
     *  rest are answered directly by the brain from live context. */
    sealed interface Hit {
        data class Action(val action: DeviceAction) : Hit
        data class CallRequest(val name: String) : Hit
        data class SmsRequest(val name: String, val message: String) : Hit
        data object Greeting : Hit
        data object Thought : Hit
        data object Time : Hit
        data object Battery : Hit
        data object Stats : Hit
        data object Notifications : Hit
        data object NotesList : Hit
        data class SetMode(val jarvis: Boolean) : Hit
        // ---------- PRO PACK ----------
        /** "good morning" / "brief me" / "subah bakhair" … */
        data object Briefing : Hit
        /** "screen time" / "time on instagram" … query is null for the overall report. */
        data class AppUsage(val query: String?) : Hit
        /** "calculate 25% of 800" / "5+5 kitna hota hai" … */
        data class Calculate(val expr: String) : Hit
        /** "convert 50 dollars to pkr" … words resolved to ISO codes by the brain. */
        data class Convert(val amount: Double, val fromWord: String, val toWord: String) : Hit
        /** "whatsapp ammi: hello" — draft only, never auto-sends. */
        data class WhatsAppRequest(val name: String, val text: String) : Hit
        /** "what did I copy" … query null = last clips, else search. */
        data class ClipboardQuery(val query: String?) : Hit
        /** "find app whatsapp" — text-only list, no new DeviceAction. */
        data class AppSearch(val query: String) : Hit
        /** "emergency" / "bachao" / "madad" … */
        data object Emergency : Hit
        /** "when I say X, <commands>" — the brain parses each command clause. */
        data class RoutineCreate(val trigger: String, val commands: String) : Hit
    }

    fun match(l: String): Hit? {
        greeting(l)?.let { return it }
        setMode(l)?.let { return it }
        // Routine definitions must win over everything inside them: the
        // "when I say X," prefix is unambiguous, and clauses may contain
        // words other matchers would grab ("wifi", "good morning", …).
        routineCreate(l)?.let { return it }
        emergency(l)?.let { return it }
        briefing(l)?.let { return it }
        callRequest(l)?.let { return it }
        smsRequest(l)?.let { return it }
        whatsAppRequest(l)?.let { return it }
        rememberFact(l)?.let { return it }
        noteAdd(l)?.let { return it }
        notesList(l)?.let { return it }
        alarm(l)?.let { return Hit.Action(it) }
        timer(l)?.let { return Hit.Action(it) }
        reminder(l)?.let { return Hit.Action(it) }
        dnd(l)?.let { return Hit.Action(it) }
        volume(l)?.let { return Hit.Action(it) }
        brightness(l)?.let { return Hit.Action(it) }
        wifi(l)?.let { return Hit.Action(it) }
        bluetooth(l)?.let { return Hit.Action(it) }
        flashlight(l)?.let { return Hit.Action(it) }
        calculate(l)?.let { return it }
        convert(l)?.let { return it }
        appSearch(l)?.let { return it }
        openApp(l)?.let { return Hit.Action(it) }
        webSearch(l)?.let { return Hit.Action(it) }
        clipboard(l)?.let { return it }
        appUsage(l)?.let { return it }
        notifications(l)?.let { return it }
        time(l)?.let { return it }
        battery(l)?.let { return it }
        stats(l)?.let { return it }
        thought(l)?.let { return it }
        return null
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun cleanName(s: String): String = s.trim()
        .removeSuffix(" app").removeSuffix(" application")
        .removeSuffix(" ko").removeSuffix(" koo")
        .trim().trim('"', '\'', ':', '،', '۔')

    // ------------------------------------------------------------------
    // Greeting
    // ------------------------------------------------------------------

    private val GREETING_RX = Regex(
        """^(hi|hii+|hello|hey|sala+m|aoa|adab|assalam(ualekum| o alaikum)?|السلام علیکم|آداب|नमस्ते|आदाब)\b.*$"""
    )
    private fun greeting(l: String): Hit? =
        if (GREETING_RX.containsMatchIn(l) && l.length < 30) Hit.Greeting else null

    // ------------------------------------------------------------------
    // Dual mode: JARVIS vs MANUAL
    // ------------------------------------------------------------------

    private val JARVIS_ON_RX = Regex(
        """\b(jarvis mode( on| lagao| chalu)?|jarvis on|enable jarvis|جاروس موڈ|जार्विस मोड)\b"""
    )
    private val MANUAL_ON_RX = Regex(
        """\b(manual mode( on| lagao| kar do)?|manual kar do|مینوئل موڈ|मैनुअल मोड)\b"""
    )

    private fun setMode(l: String): Hit? = when {
        JARVIS_ON_RX.containsMatchIn(l) -> Hit.SetMode(true)
        MANUAL_ON_RX.containsMatchIn(l) -> Hit.SetMode(false)
        else -> null
    }

    // ------------------------------------------------------------------
    // Call / SMS  (RISKY — the brain asks for confirmation)
    // ------------------------------------------------------------------

    private val CALL_RX = listOf(
        Regex("""^call\s+(.+)$"""),                                            // call Ammi
        Regex("""^(.+?)\s+(?:ko\s+)?call\s+(?:kar\s?o?|lag\s?a\s?o?|mila\s?o?)$"""), // ammi ko call karo
        Regex("""^call\s+(?:kar\s?o?|lag\s?a\s?o?)\s+(.+?)(?:\s+ko)?$"""),      // call karo ammi ko
        Regex("""^(.+?)\s*کو\s*کال\s*کرو$"""),                                   // امی کو کال کرو
        Regex("""^(.+?)\s*को\s*कॉल\s*करो$"""),                                   // अम्मी को कॉल करो
    )

    private fun callRequest(l: String): Hit? {
        for (rx in CALL_RX) {
            val m = rx.find(l) ?: continue
            val name = cleanName(m.groupValues[1])
            if (name.isNotBlank()) return Hit.CallRequest(name)
        }
        return null
    }

    private val SMS_RX = listOf(
        Regex("""^(?:text|sms|message|msg)\s+([a-z\u0600-\u06FF\u0900-\u097F ]+?)\s*:\s*(.+)$"""),
        Regex("""^(?:text|sms|message|msg)\s+([a-z\u0600-\u06FF\u0900-\u097F ]+?)\s+(.+)$"""),
        Regex("""^(.+?)\s+ko\s+(?:sms|msg|message|میسج|मैसेज)\s+(?:bhej\s?o?|kar\s?o?|بھیجو|भेजो)\s*:?\s*(.+)$"""),
        Regex("""^(?:sms|msg)\s+(?:kar\s?o?|bhej\s?o?)\s+(.+?)\s+ko\s*:?\s*(.+)$"""),
        Regex("""^(.+?)\s*کو\s*(?:میسج|پیغام)\s*بھیجو\s*:?\s*(.+)$"""),
        Regex("""^(.+?)\s*को\s*(?:मैसेज|संदेश)\s*भेजो\s*:?\s*(.+)$"""),
    )

    private fun smsRequest(l: String): Hit? {
        for (rx in SMS_RX) {
            val m = rx.find(l) ?: continue
            val name = cleanName(m.groupValues[1])
            val msg = m.groupValues[2].trim()
            if (name.isNotBlank() && msg.isNotBlank()) return Hit.SmsRequest(name, msg)
        }
        return null
    }

    // ------------------------------------------------------------------
    // Remember fact / notes
    // ------------------------------------------------------------------

    private val REMEMBER_RX = listOf(
        Regex("""^remember\s+(?:this|that)\s*:?\s*(.+)$"""),
        Regex("""^yaad\s+rakh\s?o?\s*:?\s*(?:ke\s+)?(.+)$"""),
        Regex("""^یاد\s*رکھو\s*:?\s*(.+)$"""),
        Regex("""^याद\s*रखो\s*:?\s*(.+)$"""),
    )

    private fun rememberFact(l: String): Hit? {
        for (rx in REMEMBER_RX) {
            val m = rx.find(l) ?: continue
            val fact = m.groupValues[1].trim()
            if (fact.isNotBlank()) return Hit.Action(DeviceAction.RememberFact(fact))
        }
        return null
    }

    private val NOTE_ADD_RX = listOf(
        Regex("""^(?:note|take a note|make a note)\s*:?\s*(?:that\s+)?(.+)$"""),
        Regex("""^(?:note\s+likh\s?o?|not\s+kar\s?o?|yaad\s+rakh\s?o?)\s*:?\s*(?:ke\s+)?(.+)$"""),
        Regex("""^نوٹ\s*(?:کرو|لکھو)?\s*:?\s*(.+)$"""),
        Regex("""^नोट\s*(?:करो|लिखो)?\s*:?\s*(.+)$"""),
    )

    private fun noteAdd(l: String): Hit? {
        for (rx in NOTE_ADD_RX) {
            val m = rx.find(l) ?: continue
            val text = m.groupValues[1].trim()
            if (text.isNotBlank()) return Hit.Action(DeviceAction.AddNote(text))
        }
        return null
    }

    private val NOTES_LIST_RX = Regex(
        """\b(my notes|show notes|list notes|mere notes|notes dikh?a?o?|notes bat?a?o?|میرے نوٹ|मेरे नोट्स?)\b"""
    )
    private fun notesList(l: String): Hit? =
        if (NOTES_LIST_RX.containsMatchIn(l)) Hit.NotesList else null

    // ------------------------------------------------------------------
    // Alarm
    // ------------------------------------------------------------------

    private val ALARM_WORD = Regex("""\b(alarm|alaram|الارم|अलार्म)\b""")
    private val TIME_RX = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
    private val MORNING_RX = Regex("""\b(subah|subha|morning|am|صبح)\b""")
    private val EVENING_RX = Regex("""\b(sham|shaam|raat|evening|pm|شام|رات|शाम|रात)\b""")

    private fun alarm(l: String): DeviceAction? {
        val hasAlarmWord = ALARM_WORD.containsMatchIn(l)
        val hasWakeWord = Regex("""\b(uth?a?\s?o?|wake|jag?a?o?|اٹھ|जगाओ)\b""").containsMatchIn(l) &&
            Regex("""\b(baje|bajay|بجے|बजे)\b""").containsMatchIn(l)
        if (!hasAlarmWord && !hasWakeWord) return null
        val m = TIME_RX.find(l) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        val ampm = m.groupValues[3]
        when {
            ampm == "pm" || EVENING_RX.containsMatchIn(l) -> if (hour < 12) hour += 12
            ampm == "am" || MORNING_RX.containsMatchIn(l) -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return DeviceAction.SetAlarm(hour, minute)
    }

    // ------------------------------------------------------------------
    // Timer
    // ------------------------------------------------------------------

    private val TIMER_WORD = Regex("""\b(timer|ٹائمر|टाइमर)\b""")
    private val DURATION_RX =
        Regex("""(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|second|minute|hour|منٹ|گھنٹے?|मिनट|घंटे?)""")

    private fun timer(l: String): DeviceAction? {
        if (!TIMER_WORD.containsMatchIn(l)) return null
        val m = DURATION_RX.find(l) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val unit = m.groupValues[2]
        val seconds = when {
            unit.startsWith("hour") || unit.startsWith("hr") || unit == "گھنٹے" || unit == "گھنٹہ" || unit == "घंटे" -> n * 3600
            unit.startsWith("min") || unit == "minute" || unit == "منٹ" || unit == "मिनट" -> n * 60
            else -> n
        }
        if (seconds <= 0 || seconds > 24 * 3600) return null
        return DeviceAction.StartTimer(seconds)
    }

    // ------------------------------------------------------------------
    // Reminder
    // ------------------------------------------------------------------

    private val REMIND_RX = listOf(
        // remind me to <text> in <n> minutes
        Regex("""remind me (?:to\s+)?(.+?)\s+in\s+(\d+)\s*(?:minutes?|mins?)"""),
        // <n> minute me yaad dilao <text>   /   mujhe yaad dilao <text> <n> minute me
        Regex("""(\d+)\s*(?:minute|min)s?\s*(?:me|main|mein)\s*(?:yaad\s+dil?a?\s?o?|یاد\s*دلاؤ|याद\s*दिलाओ)\s*(?:ke\s+|k\s+|کہ\s+)?(.+)?"""),
        Regex("""(?:mujhe|mujhy)\s*(?:yaad\s+dil?a?\s?o?)\s*(.+?)\s*(\d+)\s*(?:minute|min)s?\s*(?:me|main|mein)?"""),
        Regex("""(?:yaad\s+dil?a?\s?o?|یاد\s*دلاؤ|याद\s*दिलाओ)\s*:?\s*(.+)$"""),
    )

    private fun reminder(l: String): DeviceAction? {
        for ((i, rx) in REMIND_RX.withIndex()) {
            val m = rx.find(l) ?: continue
            when (i) {
                0 -> {
                    val text = m.groupValues[1].trim()
                    val mins = m.groupValues[2].toIntOrNull() ?: continue
                    if (text.isNotBlank()) return DeviceAction.AddReminder(text, mins)
                }
                1 -> {
                    val mins = m.groupValues[1].toIntOrNull() ?: continue
                    val text = m.groupValues[2].trim().ifBlank { "reminder" }
                    return DeviceAction.AddReminder(text, mins)
                }
                2 -> {
                    val text = m.groupValues[1].trim().ifBlank { "reminder" }
                    val mins = m.groupValues[2].toIntOrNull() ?: continue
                    return DeviceAction.AddReminder(text, mins)
                }
                3 -> {
                    val text = m.groupValues[1].trim()
                    if (text.isNotBlank()) return DeviceAction.AddReminder(text, 30)
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------
    // Volume
    // ------------------------------------------------------------------

    private fun volume(l: String): DeviceAction? {
        if (Regex("""\b(unmute|un mute)\b""").containsMatchIn(l)) return DeviceAction.SetMuted(false)
        if (Regex("""\b(mute|khamosh|خاموش|म्यूट)\b""").containsMatchIn(l)) return DeviceAction.SetMuted(true)
        if (Regex("""(volume up|awaz (?:tez|barh?a?o?|zyada)|آواز تیز|आवाज़ तेज़?|volume barh?a?o?)""").containsMatchIn(l))
            return DeviceAction.AdjustVolume(true)
        if (Regex("""(volume down|awaz (?:kam|kam karo|ghatao)|آواز کم|आवाज़ कम|volume kam)""").containsMatchIn(l))
            return DeviceAction.AdjustVolume(false)
        val pct = Regex("""(?:volume|awaz|آواز|आवाज़)\s*(\d{1,3})""").find(l)
        if (pct != null) {
            val n = pct.groupValues[1].toInt().coerceIn(0, 100)
            return DeviceAction.SetVolume(n)
        }
        return null
    }

    // ------------------------------------------------------------------
    // Brightness
    // ------------------------------------------------------------------

    private fun brightness(l: String): DeviceAction? {
        if (!Regex("""\b(brightness|roshni|روشنی|रोशनी)\b""").containsMatchIn(l)) return null
        val pct = Regex("""(\d{1,3})""").find(l)
        if (pct != null) return DeviceAction.SetBrightness(pct.groupValues[1].toInt().coerceIn(1, 100))
        if (Regex("""\b(tez|zyada|zaida|barh?a?o?|full|زیادہ|ज्यादा)\b""").containsMatchIn(l))
            return DeviceAction.SetBrightness(90)
        if (Regex("""\b(kam|ghatao|کم|कम)\b""").containsMatchIn(l))
            return DeviceAction.SetBrightness(25)
        return null
    }

    // ------------------------------------------------------------------
    // Wifi / Bluetooth / Flashlight
    // ------------------------------------------------------------------

    private val ON_RX = Regex("""\b(on|kh?ol\s?o?|chalu|start|on kar\s?o?|jal?a?o?|آن|चालू|जलाओ)\b""")
    private val OFF_RX = Regex("""\b(off|band|stop|off kar\s?o?|bujh?a?o?|بند|बंद|बुझाओ)\b""")

    private fun wifi(l: String): DeviceAction? {
        if (!Regex("""\b(wifi|wi-fi|وائی\s?فائی|वाई\s?फाई)\b""").containsMatchIn(l)) return null
        return when {
            ON_RX.containsMatchIn(l) -> DeviceAction.ToggleWifi(true)
            OFF_RX.containsMatchIn(l) -> DeviceAction.ToggleWifi(false)
            else -> null
        }
    }

    private fun bluetooth(l: String): DeviceAction? {
        if (!Regex("""\b(bluetooth|بلوٹوتھ|ब्लूटूथ)\b""").containsMatchIn(l)) return null
        return when {
            ON_RX.containsMatchIn(l) -> DeviceAction.ToggleBluetooth(true)
            OFF_RX.containsMatchIn(l) -> DeviceAction.ToggleBluetooth(false)
            else -> null
        }
    }

    private fun flashlight(l: String): DeviceAction? {
        if (!Regex("""\b(flashlight|torch|ٹارچ|टॉर्च)\b""").containsMatchIn(l)) return null
        return when {
            ON_RX.containsMatchIn(l) -> DeviceAction.ToggleFlashlight(true)
            OFF_RX.containsMatchIn(l) -> DeviceAction.ToggleFlashlight(false)
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // Open app
    // ------------------------------------------------------------------

    private val OPEN_APP_RX = listOf(
        Regex("""^open\s+(.+)$"""),
        Regex("""^(.+?)\s+(?:khol\s?o?|khol do|open kar\s?o?)$"""),
        Regex("""^(?:khol\s?o?)\s+(.+)$"""),
        Regex("""^(.+?)\s*کھولو?$"""),
        Regex("""^کھولو?\s+(.+)$"""),
        Regex("""^(.+?)\s*खोलो?$"""),
        Regex("""^खोलो?\s+(.+)$"""),
    )

    private fun openApp(l: String): DeviceAction? {
        for (rx in OPEN_APP_RX) {
            val m = rx.find(l) ?: continue
            val name = cleanName(m.groupValues[1])
            // Guard: single generic verbs are not app names.
            if (name.isBlank() || name.length < 2) continue
            if (name in setOf("it", "this", "that", "ye", "یہ", "यह")) continue
            return DeviceAction.OpenApp(name)
        }
        return null
    }

    // ------------------------------------------------------------------
    // Web search
    // ------------------------------------------------------------------

    private val SEARCH_RX = listOf(
        Regex("""^(?:search|google)(?:\s+for)?\s+(.+)$"""),
        Regex("""^(.+?)\s+(?:search|google)\s+kar\s?o?$"""),
        Regex("""^(?:search|google)\s+kar\s?o?\s+(.+)$"""),
        Regex("""^(.+?)\s*(?:تلاش\s*کرو|گوگل\s*کرو)$"""),
        Regex("""^(.+?)\s*(?:खोजो?|गूगल\s*करो)$"""),
    )

    private fun webSearch(l: String): DeviceAction? {
        for (rx in SEARCH_RX) {
            val m = rx.find(l) ?: continue
            val q = cleanName(m.groupValues[1])
            if (q.isNotBlank() && q.length > 1) return DeviceAction.WebSearch(q)
        }
        return null
    }

    // ------------------------------------------------------------------
    // Pure-info intents (answered from context, no device effect)
    // ------------------------------------------------------------------

    private val NOTIF_RX = Regex(
        """\b(notifications?|اطلاعات|اطلاع|सूचनाएँ?|सूचना)\b|notif\w*\s*(parh\s?o?|dikh?a?o?|bat?a?o?|read)"""
    )
    private fun notifications(l: String): Hit? =
        if (NOTIF_RX.containsMatchIn(l)) Hit.Notifications else null

    private val TIME_Q_RX = Regex(
        """\b(what(\s*'?s|\s+is)?\s+the\s+time|tell me the time|time\s*(kya|batao|dekho)|kitne\s*baj[ey]\s*(hain|hai)?|کتنے\s*بجے|कितने\s*बजे|وقت\s*کیا)\b"""
    )
    private fun time(l: String): Hit? =
        if (TIME_Q_RX.containsMatchIn(l)) Hit.Time else null

    private val BATTERY_RX = Regex("""\b(battery|بیٹری|बैटरी)\b""")
    private fun battery(l: String): Hit? =
        if (BATTERY_RX.containsMatchIn(l)) Hit.Battery else null

    private val STATS_RX = Regex("""\b(device stats|system stats|phone stats|mobile stats|system info)\b""")
    private fun stats(l: String): Hit? =
        if (STATS_RX.containsMatchIn(l)) Hit.Stats else null

    private val THOUGHT_RX = Regex(
        """\b(thought|motivat|inspir|ach[hi]?\s*baat|اچھی\s*بات|अच्छी\s*बात|सुविचार)\b"""
    )
    private fun thought(l: String): Hit? =
        if (THOUGHT_RX.containsMatchIn(l)) Hit.Thought else null

    // ==================================================================
    // PRO PACK matchers
    // ==================================================================

    // ------------------------------------------------------------------
    // Emergency — "emergency", "help me", "bachao", "madad", …
    // ------------------------------------------------------------------

    private val EMERGENCY_RX = Regex(
        """\b(emergency(\s*call)?|help\s*me|bacha\s?o?|madad(\s*kar\s?o?)?|بچاؤ|مدد\s*کرو|बचाओ|मदद(\s*करो)?)\b"""
    )
    private fun emergency(l: String): Hit? =
        if (EMERGENCY_RX.containsMatchIn(l)) Hit.Emergency else null

    // ------------------------------------------------------------------
    // Smart briefing — "good morning", "brief me", "subah bakhair", …
    // ------------------------------------------------------------------

    private val BRIEFING_RX = Regex(
        """\b(good\s*morning|good\s*afternoon|good\s*evening|morning\s*briefing|brief\s*me|briefing|(subah|subha)\s*bakh?air|(subah|subha)\s*bakher|آج\s*کی\s*(briefing|خبر)|صبح\s*بخیر|सुबह\s*की\s*(खबर|ब्रीफिंग)|मॉर्निंग\s*ब्रीफिंग)\b"""
    )
    private fun briefing(l: String): Hit? =
        if (BRIEFING_RX.containsMatchIn(l)) Hit.Briefing else null

    // ------------------------------------------------------------------
    // DND control — "dnd on", "silence my phone", "phone silent karo", …
    // ------------------------------------------------------------------

    private val DND_OFF_RX = Regex(
        """\b(dnd\s*off|do\s*not\s*disturb\s*off|dnd\s*band(\s*kar\s?o?)?|dnd\s*hat?a?o?|unsilence(\s*(my\s*)?phone)?|ڈسٹرب\s*(ختم|بند)|परेशान\s*करना\s*बंद)\b"""
    )
    private val DND_ON_RX = Regex(
        """\b(dnd(\s*on)?|do\s*not\s*disturb(\s*on|\s*lag?a?o?)?|silence(\s*(my\s*)?phone)?|phone\s*silent\s*kar\s?o?|silent\s*kar\s?o?|khamosh\s*kar\s?o?|ڈسٹرب\s*نہ\s*کرو|परेशान\s*न\s*करो)\b"""
    )

    private fun dnd(l: String): DeviceAction? = when {
        DND_OFF_RX.containsMatchIn(l) -> DeviceAction.SetDnd(false)
        DND_ON_RX.containsMatchIn(l) -> DeviceAction.SetDnd(true)
        else -> null
    }

    // ------------------------------------------------------------------
    // Calculator — "calculate 25% of 800", "5+5 kitna hota hai", …
    // ------------------------------------------------------------------

    private val CALC_RX = listOf(
        Regex("""^(?:calculate|calc|hisab|hisaab)\s*:?\s*(.+)$"""),
        Regex("""^(?:calculate|calc|hisab|hisaab)\s*(?:kar\s?o?|کرو|करो)\s+(.+)$"""),
        Regex("""^(.+?)\s*(?:calculate|calc|hisab|hisaab)\s*(?:kar\s?o?|کرو|करो)\s*:?\s*$"""),
        Regex("""^(.+?)\s*(?:kitna|kitne)\s*(?:hota|hote|hoti)\s*(?:hai|hain)\s*\??$"""),
        Regex("""^حساب\s*:?\s*(.+)$"""),
        Regex("""^गणना\s*:?\s*(.+)$"""),
    )
    /** Only digits, operators, whitespace and parens — anything else is not maths. */
    private val CALC_OK_RX = Regex("""^[\d\s+\-*/%^().]+$""")
    /** "25% of 800" carries the word "of", so it gets its own acceptance. */
    private val PERCENT_OF_OK_RX = Regex("""^\s*[\d.]+\s*%\s*of\s*[\d.]+\s*$""")

    private fun calculate(l: String): Hit? {
        for (rx in CALC_RX) {
            val m = rx.find(l) ?: continue
            val expr = m.groupValues[1].trim().trimEnd('?', '؟')
            if (expr.isBlank()) continue
            if (!CALC_OK_RX.matches(expr) && !PERCENT_OF_OK_RX.matches(expr)) continue
            if (!expr.any { it.isDigit() }) continue
            // Guard: a bare number like "5" is not a calculation.
            if (!expr.any { it in "+-*/%^()" } && !Regex("""\d\s*%\s*of\s*\d""").containsMatchIn(expr)) continue
            return Hit.Calculate(expr)
        }
        return null
    }

    // ------------------------------------------------------------------
    // Currency converter — "convert 50 dollars to pkr", …
    // ------------------------------------------------------------------

    private val CONVERT_RX = listOf(
        Regex("""^convert\s+([\d.]+)\s+([\w\u0600-\u06FF\u0900-\u097F]+)\s+to\s+([\w\u0600-\u06FF\u0900-\u097F]+)$"""),
        Regex("""^([\d.]+)\s+([\w\u0600-\u06FF\u0900-\u097F]+)\s+(?:ko|کو|को)\s+([\w\u0600-\u06FF\u0900-\u097F]+)\s*(?:me|mein|main|میں|میں|में)?\s*convert\s*(?:kar\s?o?|karo|کرو|करो)?$"""),
    )

    private fun convert(l: String): Hit? {
        for (rx in CONVERT_RX) {
            val m = rx.find(l) ?: continue
            val amount = m.groupValues[1].toDoubleOrNull() ?: continue
            val from = m.groupValues[2].trim()
            val to = m.groupValues[3].trim()
            if (from.isNotBlank() && to.isNotBlank() && amount > 0) {
                return Hit.Convert(amount, from, to)
            }
        }
        return null
    }

    // ------------------------------------------------------------------
    // WhatsApp draft — "whatsapp ammi: hello", "{name} ko whatsapp karo: …"
    // ------------------------------------------------------------------

    private val WHATSAPP_RX = listOf(
        Regex("""^(?:whatsapp|watsapp|واٹس\s*ایپ|व्हाट्सऐप)\s+([a-z\u0600-\u06FF\u0900-\u097F ]+?)\s*:\s*(.+)$"""),
        Regex("""^(.+?)\s+ko\s+(?:whatsapp|watsapp|واٹس\s*ایپ|व्हाट्सऐप)\s+(?:kar\s?o?|karo|bhej\s?o?|message)\s*:?\s*(.+)$"""),
    )

    private fun whatsAppRequest(l: String): Hit? {
        for (rx in WHATSAPP_RX) {
            val m = rx.find(l) ?: continue
            val name = cleanName(m.groupValues[1])
            val text = m.groupValues[2].trim()
            if (name.isNotBlank() && text.isNotBlank()) return Hit.WhatsAppRequest(name, text)
        }
        return null
    }

    // ------------------------------------------------------------------
    // Clipboard history — "what did I copy", "clipboard", …
    // ------------------------------------------------------------------

    private val CLIPBOARD_ABOUT_RX = Regex(
        """\b(?:about|containing|wala|والی|والا)\s+(.+)$"""
    )
    private val CLIPBOARD_RX = Regex(
        """\b(what\s*did\s*i\s*copy|copy\s*history|clipboard(\s*history)?|copy\s*kiya\s*tha|میں\s*نے\s*کیا\s*کاپی\s*کیا|کلپ\s*بورڈ|मैंने\s*क्या\s*कॉपी\s*किया)\b"""
    )

    private fun clipboard(l: String): Hit? {
        if (!CLIPBOARD_RX.containsMatchIn(l)) return null
        val q = CLIPBOARD_ABOUT_RX.find(l)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        return Hit.ClipboardQuery(q)
    }

    // ------------------------------------------------------------------
    // App usage — "screen time", "time on instagram", …
    // ------------------------------------------------------------------

    private val APPUSAGE_RX = Regex(
        """\b(screen\s*time|app\s*usage|usage\s*stats?|kitna\s*time|time\s*(did\s*i\s*)?spen[td]|اسکرین\s*ٹائم|स्क्रीन\s*टाइम|ऐप\s*यूसेज)\b"""
    )
    private val APPUSAGE_ON_RX = Regex("""\bon\s+([a-z0-9][a-z0-9 .+\-]*)$""")

    private fun appUsage(l: String): Hit? {
        if (!APPUSAGE_RX.containsMatchIn(l)) return null
        val q = APPUSAGE_ON_RX.find(l)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 2 }
        return Hit.AppUsage(q)
    }

    // ------------------------------------------------------------------
    // App search — "find app X", "app dhoondo X", …
    // ------------------------------------------------------------------

    private val APPSEARCH_RX = listOf(
        Regex("""^(?:find|search)\s+app\s+(.+)$"""),
        Regex("""^app\s+(?:dhoond\s?o?|dhoondo|تلاش\s*کرو|खोजो?)\s+(.+)$"""),
        Regex("""^(.+?)\s+app\s+(?:dhoond\s?o?|dhoondo)$"""),
    )

    private fun appSearch(l: String): Hit? {
        for (rx in APPSEARCH_RX) {
            val m = rx.find(l) ?: continue
            val q = cleanName(m.groupValues[1])
            if (q.length >= 2) return Hit.AppSearch(q)
        }
        return null
    }

    // ------------------------------------------------------------------
    // Routine creation — "when I say X, <commands>", "jab main X kahun, …"
    // ------------------------------------------------------------------

    private val ROUTINE_CREATE_RX = listOf(
        Regex("""^when\s+i\s+say\s+(.+?)\s*,\s*(.+)$"""),
        Regex("""^jab\s+(?:mein|main|me)\s+(.+?)\s+(?:kahun|kaho|bolun)\s*[:,،]?\s*(.+)$"""),
        Regex("""^جب\s+میں\s+(.+?)\s+کہوں\s*[:,،]?\s*(.+)$"""),
        Regex("""^जब\s+मैं\s+(.+?)\s+कहूँ\s*[:,]?\s*(.+)$"""),
    )

    private fun routineCreate(l: String): Hit? {
        for (rx in ROUTINE_CREATE_RX) {
            val m = rx.find(l) ?: continue
            val trigger = m.groupValues[1].trim().trim('"', '\'', ':', '،', '۔')
            val commands = m.groupValues[2].trim()
            if (trigger.length >= 2 && commands.length >= 3) {
                return Hit.RoutineCreate(trigger, commands)
            }
        }
        return null
    }
}
