package com.baloch.ai.brain

import android.content.Intent
import com.baloch.ai.BalochApp
import com.baloch.ai.engine.NotesStore
import com.baloch.ai.engine.pro.BriefingHelper
import com.baloch.ai.engine.pro.CalcParser
import com.baloch.ai.engine.pro.ClipboardWatcher
import com.baloch.ai.engine.pro.CurrencyConverter
import com.baloch.ai.engine.pro.EmergencyStore
import com.baloch.ai.engine.pro.Routine
import com.baloch.ai.engine.pro.RoutineStore
import com.baloch.ai.engine.pro.UsageHelper
import com.baloch.ai.memory.MemoryStore
import java.util.Locale

/**
 * The default Baloch brain: a real multilingual intent parser.
 * Understands English, Roman Urdu, Urdu script and Hindi for every
 * intent (see [Intents]). Pure-info questions are answered from the
 * live [DeviceContext]; device effects become [DeviceAction]s.
 */
class RuleBasedBrain(
    private val memory: MemoryStore,
    private val notes: NotesStore,
    private val resolveContact: (String) -> String?,
    /** Null when notification access is disabled. */
    private val notificationSummary: () -> List<String>?,
) : BrainEngine {

    override suspend fun respond(input: String, context: DeviceContext): BrainResponse {
        val lang = context.language
        val l = input.trim().lowercase()

        // Routines first: a stored trigger phrase anywhere in the input wins
        // over normal intent parsing — user-defined macros take priority
        // (e.g. "good night" runs the routine instead of greeting).
        val triggered = try {
            appCtx()?.let { RoutineStore(it).matchTrigger(l) }
        } catch (_: Exception) {
            null
        }

        val response = if (triggered != null) {
            BrainResponse(routineReply(triggered, lang), triggered.actions)
        } else when (val hit = Intents.match(l)) {
            null -> BrainResponse(Replies.NOT_UNDERSTOOD.pick(lang))

            is Intents.Hit.Greeting ->
                BrainResponse(Replies.GREETING.pick(lang))

            is Intents.Hit.Thought ->
                BrainResponse(DAILY_THOUGHTS.random().pick(lang))

            is Intents.Hit.Time -> BrainResponse(
                L3(
                    en = "It's ${context.timeString}, sir.",
                    ur = "${context.timeString} بجے ہیں، سر۔",
                    hi = "${context.timeString} बजे हैं, सर।",
                ).pick(lang)
            )

            is Intents.Hit.Battery -> {
                val charge = if (context.isCharging) {
                    L3(" and charging", " اور چارج ہو رہی ہے", " और चार्ज हो रही है").pick(lang)
                } else ""
                BrainResponse(
                    L3(
                        en = "Battery is at ${context.batteryPercent}%$charge, sir.",
                        ur = "بیٹری ${context.batteryPercent} فیصد ہے$charge، سر۔",
                        hi = "बैटरी ${context.batteryPercent} प्रतिशत है$charge, सर।",
                    ).pick(lang)
                )
            }

            is Intents.Hit.Stats -> BrainResponse(
                L3(
                    en = "Battery ${context.batteryPercent}%. Time ${context.timeString}, ${context.dateString}. All systems nominal, sir.",
                    ur = "بیٹری ${context.batteryPercent} فیصد۔ وقت ${context.timeString}، ${context.dateString}۔ تمام سسٹم درست ہیں، سر۔",
                    hi = "बैटरी ${context.batteryPercent} प्रतिशत। समय ${context.timeString}, ${context.dateString}। सभी सिस्टम सही हैं, सर।",
                ).pick(lang)
            )

            is Intents.Hit.Notifications -> {
                val items = notificationSummary()
                if (items == null) {
                    BrainResponse(Replies.NOTIFICATIONS_DISABLED.pick(lang))
                } else if (items.isEmpty()) {
                    BrainResponse(Replies.NOTIFICATIONS_EMPTY.pick(lang))
                } else {
                    val header = L3(
                        en = "Top notifications, sir:",
                        ur = "اہم اطلاعات، سر:",
                        hi = "मुख्य सूचनाएँ, सर:",
                    ).pick(lang)
                    BrainResponse("$header\n• " + items.take(5).joinToString("\n• "))
                }
            }

            is Intents.Hit.NotesList -> {
                val all = notes.all()
                if (all.isEmpty()) {
                    BrainResponse(Replies.NOTES_EMPTY.pick(lang))
                } else {
                    val header = L3(
                        en = "Your notes, sir:",
                        ur = "آپ کے نوٹس، سر:",
                        hi = "आपके नोट्स, सर:",
                    ).pick(lang)
                    BrainResponse("$header\n• " + all.take(10).joinToString("\n• "))
                }
            }

            is Intents.Hit.CallRequest -> {
                val number = resolveContact(hit.name)
                if (number == null) {
                    BrainResponse(Replies.CONTACT_NOT_FOUND.format(lang, "name" to hit.name))
                } else {
                    BrainResponse(
                        Replies.CONFIRM_CALL.format(lang, "name" to hit.name),
                        listOf(DeviceAction.CallContact(hit.name, number)),
                    )
                }
            }

            is Intents.Hit.SmsRequest -> {
                val number = resolveContact(hit.name)
                if (number == null) {
                    BrainResponse(Replies.CONTACT_NOT_FOUND.format(lang, "name" to hit.name))
                } else {
                    BrainResponse(
                        Replies.CONFIRM_SMS.format(lang, "name" to hit.name),
                        listOf(DeviceAction.SendSms(hit.name, number, hit.message)),
                    )
                }
            }

            is Intents.Hit.SetMode -> {
                val msg = if (hit.jarvis) L3(
                    en = "JARVIS mode engaged, sir. I am now watching over everything.",
                    ur = "جاروس موڈ فعال ہو گیا، سر۔ اب میں ہر چیز پر نظر رکھوں گا۔",
                    hi = "जार्विस मोड सक्रिय हो गया, सर। अब मैं हर चीज़ पर नज़र रखूँगा।",
                ) else L3(
                    en = "Manual mode, sir. I'll stay quiet until you call on me.",
                    ur = "مینوئل موڈ، سر۔ آپ کے بلانے تک میں خاموش رہوں گا۔",
                    hi = "मैनुअल मोड, सर। आपके बुलाने तक मैं शांत रहूँगा।",
                )
                BrainResponse(msg.pick(lang), listOf(DeviceAction.SetMode(hit.jarvis)))
            }

            is Intents.Hit.Action -> {
                // Friendly pre-check: DND without the special access would
                // just bounce off the executor — tell Kamran the exact
                // grant path instead.
                if (hit.action is DeviceAction.SetDnd && !hasDndAccess()) {
                    BrainResponse(Replies.DND_NEEDS_ACCESS.pick(lang))
                } else {
                    BrainResponse(replyFor(hit.action, lang), listOf(hit.action))
                }
            }

            // ---------------- PRO PACK ----------------

            is Intents.Hit.Briefing ->
                BrainResponse(BriefingHelper.buildBriefing(context, notificationSummary, lang))

            is Intents.Hit.AppUsage -> appUsageReply(hit, lang)

            is Intents.Hit.Calculate -> calculateReply(hit, lang)

            is Intents.Hit.Convert -> convertReply(hit, lang)

            is Intents.Hit.WhatsAppRequest -> whatsAppReply(hit, lang)

            is Intents.Hit.ClipboardQuery -> clipboardReply(hit, lang)

            is Intents.Hit.AppSearch -> appSearchReply(hit, lang)

            is Intents.Hit.Emergency -> emergencyReply(lang)

            is Intents.Hit.RoutineCreate -> routineCreateReply(hit, lang)
        }
        memory.logExchange(input, response.text)
        return response
    }

    /**
     * App context for the pro engine helpers. BalochApp sets its instance
     * in onCreate — long before any ViewModel exists — so this is only
     * null on a pathological init order. Callers degrade gracefully.
     */
    private fun appCtx(): android.content.Context? =
        try {
            BalochApp.instance?.applicationContext
        } catch (_: Exception) {
            null
        }

    /** True when the DND special access is granted (or unknowable — then
     *  let the executor try and degrade gracefully). */
    private fun hasDndAccess(): Boolean {
        val ctx = appCtx() ?: return true
        return try {
            (ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager).isNotificationPolicyAccessGranted
        } catch (_: Exception) {
            true
        }
    }

    /** What Baloch says when it takes an action (in the active language). */
    private fun replyFor(action: DeviceAction, lang: BalochLanguage): String = when (action) {
        is DeviceAction.OpenApp -> L3(
            "Opening ${action.appName}, sir.", "${action.appName} کھول رہا ہوں، سر۔", "${action.appName} खोल रहा हूँ, सर।")
            .pick(lang)
        is DeviceAction.SetAlarm -> {
            val t = "%d:%02d".format(action.hour, action.minute)
            L3("Alarm set for $t, sir.", "$t کا الارم لگا دیا، سر۔", "$t का अलार्म लगा दिया, सर।").pick(lang)
        }
        is DeviceAction.StartTimer -> {
            val d = describeDuration(action.seconds, lang)
            L3("Timer started for $d, sir.", "$d کا ٹائمر شروع کر دیا، سر۔", "$d का टाइमर शुरू कर दिया, सर।").pick(lang)
        }
        is DeviceAction.AddReminder -> L3(
            "I'll remind you in ${action.minutesFromNow} minutes, sir.",
            "${action.minutesFromNow} منٹ میں یاد دلاؤں گا، سر۔",
            "${action.minutesFromNow} मिनट में याद दिलाऊँगा, सर।").pick(lang)
        is DeviceAction.ToggleWifi -> if (action.on) L3(
            "Wi-Fi on, sir.", "وائی فائی آن کر دیا، سر۔", "वाईफाई चालू कर दिया, सर।").pick(lang)
        else L3("Wi-Fi off, sir.", "وائی فائی بند کر دیا، سر۔", "वाईफाई बंद कर दिया, सर।").pick(lang)
        is DeviceAction.ToggleBluetooth -> if (action.on) L3(
            "Bluetooth on, sir.", "بلوٹوتھ آن کر دیا، سر۔", "ब्लूटूथ चालू कर दिया, सर।").pick(lang)
        else L3("Bluetooth off, sir.", "بلوٹوتھ بند کر دیا، سر۔", "ब्लूटूथ बंद कर दिया, सर।").pick(lang)
        is DeviceAction.ToggleFlashlight -> if (action.on) L3(
            "Torch on, sir.", "ٹارچ جلا دی، سر۔", "टॉर्च जला दी, सर।").pick(lang)
        else L3("Torch off, sir.", "ٹارچ بجھا دی، سر۔", "टॉर्च बुझा दी, सर।").pick(lang)
        is DeviceAction.SetVolume -> L3(
            "Volume set to ${action.levelPercent}%, sir.",
            "آواز ${action.levelPercent} فیصد کر دی، سر۔",
            "आवाज़ ${action.levelPercent} प्रतिशत कर दी, सर।").pick(lang)
        is DeviceAction.AdjustVolume -> if (action.up) L3(
            "Volume up, sir.", "آواز تیز کر دی، سر۔", "आवाज़ तेज़ कर दी, सर।").pick(lang)
        else L3("Volume down, sir.", "آواز کم کر دی، سر۔", "आवाज़ कम कर दी, सर।").pick(lang)
        is DeviceAction.SetMuted -> if (action.muted) L3(
            "Muted, sir.", "خاموش کر دیا، سر۔", "म्यूट कर दिया, सर।").pick(lang)
        else L3("Sound back on, sir.", "آواز بحال کر دی، سر۔", "आवाज़ वापस चालू कर दी, सर।").pick(lang)
        is DeviceAction.SetBrightness -> L3(
            "Brightness set to ${action.levelPercent}%, sir.",
            "روشنی ${action.levelPercent} فیصد کر دی، سر۔",
            "रोशनी ${action.levelPercent} प्रतिशत कर दी, सर।").pick(lang)
        is DeviceAction.WebSearch -> L3(
            "Searching for \"${action.query}\", sir.",
            "\"${action.query}\" تلاش کر رہا ہوں، سر۔",
            "\"${action.query}\" खोज रहा हूँ, सर।").pick(lang)
        is DeviceAction.AddNote -> L3(
            "Noted, sir.", "نوٹ کر لیا، سر۔", "नोट कर लिया, सर।").pick(lang)
        is DeviceAction.RememberFact -> L3(
            "I'll remember that, sir.", "یہ یاد رکھوں گا، سر۔", "यह याद रखूँगा, सर।").pick(lang)
        is DeviceAction.SetDnd -> if (action.on) L3(
            "Do-not-disturb on, sir — pure focus mode. 🔕",
            "ڈو ناٹ ڈسٹرب آن، سر — مکمل توجہ کا وقت۔ 🔕",
            "डू-नॉट-डिस्टर्ब चालू, सर — पूर्ण फोकस मोड। 🔕").pick(lang)
        else L3(
            "DND off, sir — you're reachable again. 🔔",
            "ڈی این ڈی آف، سر — اب آپ دستیاب ہیں۔ 🔔",
            "DND बंद, सर — अब आप उपलब्ध हैं। 🔔").pick(lang)
        is DeviceAction.DisableDndDelayed -> L3(
            "I'll lift do-not-disturb in ${action.minutes} minutes, sir. ⏳",
            "${action.minutes} منٹ میں ڈو ناٹ ڈسٹرب خود بند کر دوں گا، سر۔ ⏳",
            "${action.minutes} मिनट में डू-नॉट-डिस्टर्ब खुद बंद कर दूँगा, सर। ⏳").pick(lang)
        is DeviceAction.WhatsAppDraft -> L3(
            "Opening WhatsApp with your message to ${action.name}, sir — just hit send. 📩",
            "${action.name} کے لیے پیغام لے کر واٹس ایپ کھول رہا ہوں، سر — بس بھیج دیں۔ 📩",
            "${action.name} के लिए संदेश लेकर WhatsApp खोल रहा हूँ, सर — बस भेज दें। 📩").pick(lang)
        is DeviceAction.EmergencyCall -> L3(
            "Calling your emergency contact now, sir. 🚨",
            "ابھی آپ کے ایمرجنسی رابطے کو کال کر رہا ہوں، سر۔ 🚨",
            "अभी आपके आपातकालीन संपर्क को कॉल कर रहा हूँ, सर। 🚨").pick(lang)
        else -> L3("Done, sir.", "ہو گیا، سر۔", "हो गया, सर।").pick(lang)
    }

    // ==================================================================
    // PRO PACK brain helpers
    // ==================================================================

    private fun routineReply(routine: Routine, lang: BalochLanguage): String =
        when (routine.name) {
            "good night", "shab bakhair", "شب بخیر", "शुभ रात्रि" ->
                Replies.GOOD_NIGHT_REPLY.pick(lang)
            "meeting mode" -> Replies.MEETING_MODE_REPLY.pick(lang)
            else -> Replies.ROUTINE_RUNNING.format(lang, "name" to routine.name)
        }

    private fun appUsageReply(hit: Intents.Hit.AppUsage, lang: BalochLanguage): BrainResponse {
        val ctx = appCtx()
        if (ctx == null || !UsageHelper.hasAccess(ctx)) {
            return BrainResponse(Replies.USAGE_ACCESS_NEEDED.pick(lang))
        }
        val report = UsageHelper.query(ctx, hit.query)
        if (report == null || report.top.isEmpty()) {
            return BrainResponse(
                if (hit.query != null) Replies.USAGE_NO_MATCH.format(lang, "q" to hit.query)
                else Replies.USAGE_EMPTY.pick(lang)
            )
        }
        val header = if (hit.query != null) {
            val total = report.top.sumOf { it.minutes }
            L3(
                "📱 Time on ${hit.query} today: ${UsageHelper.formatMinutes(total, lang)}, sir.",
                "📱 آج ${hit.query} پر: ${UsageHelper.formatMinutes(total, lang)}، سر۔",
                "📱 आज ${hit.query} पर: ${UsageHelper.formatMinutes(total, lang)}, सर।",
            ).pick(lang)
        } else {
            UsageHelper.header(report.totalMinutes, lang)
        }
        val lines = report.top.joinToString("\n") {
            "• ${it.label} — ${UsageHelper.formatMinutes(it.minutes, lang)}"
        }
        return BrainResponse("$header\n$lines")
    }

    private fun calculateReply(hit: Intents.Hit.Calculate, lang: BalochLanguage): BrainResponse {
        val result = CalcParser.eval(hit.expr)
            ?: return BrainResponse(Replies.CALC_FAILED.pick(lang))
        return BrainResponse(
            L3(
                "That's $result, sir. 🧮",
                "یہ $result بنتا ہے، سر۔ 🧮",
                "यह $result बनता है, सर। 🧮",
            ).pick(lang)
        )
    }

    private suspend fun convertReply(hit: Intents.Hit.Convert, lang: BalochLanguage): BrainResponse {
        val from = CurrencyConverter.codeFor(hit.fromWord)
        val to = CurrencyConverter.codeFor(hit.toWord)
        if (from == null || to == null) {
            return BrainResponse(Replies.CONVERT_UNKNOWN.pick(lang))
        }
        val res = CurrencyConverter.convert(appCtx(), hit.amount, from, to)
            ?: return BrainResponse(Replies.CONVERT_FAILED.pick(lang))
        fun fmt(v: Double): String = "%.2f".format(Locale.US, v)
        val line = L3(
            "💱 ${fmt(res.amount)} $from = ${fmt(res.converted)} $to, sir.",
            "💱 ${fmt(res.amount)} $from = ${fmt(res.converted)} $to، سر۔",
            "💱 ${fmt(res.amount)} $from = ${fmt(res.converted)} $to, सर।",
        ).pick(lang)
        return if (res.dated) {
            BrainResponse("$line\n${Replies.CONVERT_OFFLINE_NOTE.pick(lang)}")
        } else {
            BrainResponse(line)
        }
    }

    private fun whatsAppReply(hit: Intents.Hit.WhatsAppRequest, lang: BalochLanguage): BrainResponse {
        val number = resolveContact(hit.name)
        if (number == null) {
            return BrainResponse(Replies.CONTACT_NOT_FOUND.format(lang, "name" to hit.name))
        }
        return BrainResponse(
            Replies.WHATSAPP_DRAFT_READY.format(lang, "name" to hit.name),
            listOf(DeviceAction.WhatsAppDraft(hit.name, number, hit.text)),
        )
    }

    private fun clipboardReply(hit: Intents.Hit.ClipboardQuery, lang: BalochLanguage): BrainResponse {
        val ctx = appCtx() ?: return BrainResponse(Replies.CLIPBOARD_EMPTY.pick(lang))
        val items = if (hit.query != null) ClipboardWatcher.search(ctx, hit.query)
        else ClipboardWatcher.recent(ctx, 5)
        if (items.isEmpty()) {
            return BrainResponse(
                if (hit.query != null) Replies.CLIPBOARD_NO_MATCH.format(lang, "q" to hit.query)
                else Replies.CLIPBOARD_EMPTY.pick(lang)
            )
        }
        val header = L3(
            "📋 Your recent copies, sir:",
            "📋 آپ کی حالیہ کاپیاں، سر:",
            "📋 आपकी हालिया कॉपियाँ, सर:",
        ).pick(lang)
        return BrainResponse("$header\n• " + items.joinToString("\n• ") { it.take(120) })
    }

    private fun appSearchReply(hit: Intents.Hit.AppSearch, lang: BalochLanguage): BrainResponse {
        val ctx = appCtx()
            ?: return BrainResponse(Replies.APPSEARCH_NONE.format(lang, "q" to hit.query))
        val pm = ctx.packageManager
        val q = hit.query.lowercase()
        val matches = try {
            val main = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(main, 0)
        } catch (_: Exception) {
            emptyList()
        }.map { it.loadLabel(pm).toString() }
            .filter { it.lowercase().contains(q) }
            .distinct()
            .take(5)
        if (matches.isEmpty()) {
            return BrainResponse(Replies.APPSEARCH_NONE.format(lang, "q" to hit.query))
        }
        return BrainResponse(
            L3(
                "I found: ${matches.joinToString(", ")} — say 'open ${matches.first()}' and I'll launch it, sir. 🔍",
                "یہ ملیں: ${matches.joinToString("، ")} — کہیں 'open ${matches.first()}' اور میں کھول دوں گا، سر۔ 🔍",
                "यह मिले: ${matches.joinToString(", ")} — कहें 'open ${matches.first()}' और मैं खोल दूँगा, सर। 🔍",
            ).pick(lang)
        )
    }

    private fun emergencyReply(lang: BalochLanguage): BrainResponse {
        val ice = appCtx()?.let { EmergencyStore.get(it) }
        if (ice.isNullOrBlank()) {
            return BrainResponse(Replies.ICE_NOT_SET.pick(lang))
        }
        val name = L3(
            "your emergency contact",
            "آپ کے ایمرجنسی رابطے",
            "आपके आपातकालीन संपर्क",
        ).pick(lang)
        return BrainResponse(
            Replies.CONFIRM_EMERGENCY.format(
                lang,
                "name" to name,
                "number" to ice,
            ),
            listOf(DeviceAction.EmergencyCall("emergency contact", ice)),
        )
    }

    private val COMMAND_SPLIT_RX = Regex("""\s*(?:\band\b|\baur\b|،|,|اور|और)\s*""")

    private fun routineCreateReply(hit: Intents.Hit.RoutineCreate, lang: BalochLanguage): BrainResponse {
        val trigger = hit.trigger.lowercase()
        val actions = mutableListOf<DeviceAction>()
        var skippedRisky = 0
        hit.commands.split(COMMAND_SPLIT_RX).forEach { clause ->
            val c = clause.trim()
            if (c.isEmpty()) return@forEach
            val parsed = Intents.match(c) as? Intents.Hit.Action ?: return@forEach
            if (parsed.action.needsConfirmation) skippedRisky++ else actions.add(parsed.action)
        }
        if (actions.isEmpty()) {
            return BrainResponse(Replies.ROUTINE_NO_ACTIONS.pick(lang))
        }
        // RoutineStore degrades to in-memory when the app context is
        // unavailable, so the save below never silently drops the routine.
        RoutineStore(appCtx()).save(Routine(trigger, trigger, actions))
        var text = Replies.ROUTINE_SAVED.format(lang, "trigger" to hit.trigger)
        if (skippedRisky > 0) {
            text += " " + L3(
                "(I left out the risky steps like calls, sir — those always need your tap.)",
                "(خطرناک مراحل جیسے کالز میں نے چھوڑ دیے، سر — ان کے لیے ہمیشہ آپ کی تصدیق چاہیے۔)",
                "(जोखिम भरे चरण जैसे कॉल मैंने छोड़ दिए, सर — उनके लिए हमेशा आपकी पुष्टि चाहिए।)",
            ).pick(lang)
        }
        return BrainResponse(text)
    }

    private fun describeDuration(seconds: Int, lang: BalochLanguage): String {
        val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
        return when (lang) {
            BalochLanguage.ENGLISH -> buildString {
                if (h > 0) append("$h hour${if (h > 1) "s" else ""} ")
                if (m > 0) append("$m minute${if (m > 1) "s" else ""} ")
                if (s > 0 || isEmpty()) append("$s second${if (s != 1) "s" else ""}")
            }.trim()
            BalochLanguage.URDU -> buildString {
                if (h > 0) append("$h گھنٹے ")
                if (m > 0) append("$m منٹ ")
                if (s > 0 || isEmpty()) append("$s سیکنڈ")
            }.trim()
            BalochLanguage.HINDI -> buildString {
                if (h > 0) append("$h घंटे ")
                if (m > 0) append("$m मिनट ")
                if (s > 0 || isEmpty()) append("$s सेकंड")
            }.trim()
        }
    }
}
