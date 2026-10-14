package com.baloch.ai.life

import android.content.Context
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3

/**
 * Executes [LifeHit] intents matched by [matchLife] and returns Baloch's
 * spoken reply text. Called from the Orchestrator's pre-routing (life step).
 *
 * Every branch is null-safe and permission-aware: a missing permission or
 * offline network produces a friendly plain-language reply, never a crash.
 * SOS is transmit-class — this executor does NOT send it; it returns a
 * [SosPending] marker so the Orchestrator can run it through the normal
 * confirmation pipeline (PersonalDataGuard: mandatory confirm, both modes).
 */
object LifeExecutor {

    /** Returned when the hit is SOS — the caller must confirm before sending. */
    data object SosPending

    suspend fun execute(
        appCtx: Context,
        hit: LifeHit,
        lang: BalochLanguage,
    ): Any {
        return when (hit) {
            is LifeHit.PrayerTimes -> prayerTimes(appCtx, lang)
            is LifeHit.Qibla -> qibla(appCtx, lang)
            is LifeHit.AddClient -> addClient(appCtx, hit.name, lang)
            is LifeHit.ListClients -> listClients(appCtx, lang)
            is LifeHit.ClientSummary -> clientSummary(appCtx, lang)
            is LifeHit.VideoIdeas -> videoIdeas(hit.count, lang)
            is LifeHit.ScriptOutline -> scriptOutline(hit.topic, lang)
            is LifeHit.TrendScan -> trendScan(lang)
            is LifeHit.LogExpense -> logExpense(appCtx, hit.parsed, lang)
            is LifeHit.MonthlyExpenses -> monthlyExpenses(appCtx, lang)
            is LifeHit.StartFocus -> startFocus(appCtx, hit.minutes, lang)
            is LifeHit.StopFocus -> stopFocus(appCtx, lang)
            is LifeHit.GeoReminder -> geoReminder(appCtx, hit.parsed, lang)
            is LifeHit.Translate -> translate(appCtx, hit.parsed, lang)
            is LifeHit.DownloadTranslationModel -> downloadModel(appCtx, lang)
            is LifeHit.Sos -> SosPending
        }
    }

    private fun prayerTimes(appCtx: Context, lang: BalochLanguage): String {
        val timings = try {
            PrayerTimesApi.todaysTimings(appCtx)
        } catch (_: Exception) { emptyList() }
        if (timings.isEmpty()) {
            return L3(
                "Sir, prayer times need internet once a day — I'll show yesterday's cached ones when I have them.",
                "سر، نماز کے اوقات کے لیے دن میں ایک بار انٹرنیٹ چاہیے۔",
                "सर, नमाज़ के वक़्त के लिए दिन में एक बार इंटरनेट चाहिए।",
            ).pick(lang)
        }
        val next = PrayerTimesApi.nextPrayer(timings, System.currentTimeMillis())
        val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        val list = timings.joinToString("\n") {
            "• ${it.name}: ${fmt.format(java.util.Date(it.atMillis))}"
        }
        val nextLine = next?.let {
            "\n\n" + PrayerTimesApi.countdownText(it, System.currentTimeMillis(), lang)
        } ?: ""
        return "$list$nextLine"
    }

    private fun qibla(appCtx: Context, lang: BalochLanguage): String {
        // The live compass lives in the UI; voice gives the bearing once
        // a location fix exists.
        val loc = try { SosHelper.bestLocation(appCtx) } catch (_: Exception) { null }
        return if (loc != null) {
            val bearing = qiblaBearing(loc.latitude, loc.longitude, 21.4225, 39.8262)
            L3(
                "Qibla is ${bearing.toInt()}° from north, sir 🕋",
                "قبلہ شمال سے ${bearing.toInt()}° پر ہے، سر 🕋",
                "क़िबला उत्तर से ${bearing.toInt()}° पर है, सर 🕋",
            ).pick(lang)
        } else {
            L3(
                "Sir, I need your location once for the Qibla bearing — grant location permission and ask again 🕋",
                "سر، قبلہ کی سمت کے لیے ایک بار لوکیشن چاہیے — اجازت دیں اور دوبارہ پوچھیں 🕋",
                "सर, क़िबला दिशा के लिए एक बार लोकेशन चाहिए — अनुमति दें और फिर पूछें 🕋",
            ).pick(lang)
        }
    }

    private fun addClient(appCtx: Context, name: String, lang: BalochLanguage): String {
        return try {
            ClientStore(appCtx).add(Client(name = name))
            L3(
                "Client \"$name\" saved, sir — I'll nudge you if they go quiet for 5 days. ✅",
                "کلائنٹ \"$name\" محفوظ، سر — 5 دن خاموش رہے تو یاد دلاؤں گا۔ ✅",
                "क्लाइंट \"$name\" सेव, सर — 5 दिन चुप रहे तो याद दिलाऊँगा। ✅",
            ).pick(lang)
        } catch (_: Exception) {
            L3(
                "Hmm, couldn't save that client, sir.",
                "ہمم، یہ کلائنٹ محفوظ نہیں ہو سکا، سر۔",
                "हम्म, यह क्लाइंट सेव नहीं हो सका, सर।",
            ).pick(lang)
        }
    }

    private fun listClients(appCtx: Context, lang: BalochLanguage): String {
        val clients = try { ClientStore(appCtx).all() } catch (_: Exception) { emptyList() }
        if (clients.isEmpty()) {
            return L3(
                "No clients tracked yet, sir — say \"add client Ahmed\" to start.",
                "ابھی کوئی کلائنٹ نہیں، سر — \"add client Ahmed\" کہیں۔",
                "अभी कोई क्लाइंट नहीं, सर — \"add client Ahmed\" कहें।",
            ).pick(lang)
        }
        return clients.joinToString("\n") { "• ${it.name}" }
    }

    private fun clientSummary(appCtx: Context, lang: BalochLanguage): String {
        val clients = try { ClientStore(appCtx).all() } catch (_: Exception) { emptyList() }
        if (clients.isEmpty()) {
            return L3(
                "No clients to summarise yet, sir.",
                "ابھی خلاصے کے لیے کوئی کلائنٹ نہیں، سر۔",
                "अभी सारांश के लिए कोई क्लाइंट नहीं, सर।",
            ).pick(lang)
        }
        return try {
            clientSummary(clients, lang, "PKR")
        } catch (_: Exception) {
            L3("Hmm, couldn't build the summary, sir.",
                "ہمم، خلاصہ نہیں بن سکا، سر۔",
                "हम्म, सारांश नहीं बन सका, सर।").pick(lang)
        }
    }

    private fun videoIdeas(count: Int, lang: BalochLanguage): String {
        val ideas = try { randomIdeas(count.coerceIn(1, 5)) } catch (_: Exception) { emptyList() }
        if (ideas.isEmpty()) {
            return L3("My idea well ran dry, sir — try again!",
                "میرے آئیڈیاز ختم، سر — دوبارہ کوشش کریں!",
                "मेरे आइडिया ख़त्म, सर — फिर कोशिश करें!").pick(lang)
        }
        return ideas.mapIndexed { i, idea -> "${i + 1}. ${idea.pick(lang)}" }
            .joinToString("\n")
    }

    private fun scriptOutline(topic: String, lang: BalochLanguage): String {
        return try {
            scriptOutline(topic, lang)
        } catch (_: Exception) {
            L3("Hmm, couldn't outline that, sir.",
                "ہمم، اس کا خاکہ نہیں بن سکا، سر۔",
                "हम्म, इसका खाका नहीं बन सका, सर।").pick(lang)
        }
    }

    private fun trendScan(lang: BalochLanguage): String {
        // Trend data needs the web — Baloch hands over a ready search query.
        return try {
            trendScanIntro(lang) + "\n" + trendScanQuery(lang)
        } catch (_: Exception) {
            L3("Trend scan needs internet, sir — say \"trending\" when you're back online.",
                "ٹرینڈ اسکین کے لیے انٹرنیٹ چاہیے، سر۔",
                "ट्रेंड स्कैन के लिए इंटरनेट चाहिए, सर।").pick(lang)
        }
    }

    private fun logExpense(appCtx: Context, parsed: ParsedExpense, lang: BalochLanguage): String {
        return try {
            ExpenseStore(appCtx).add(
                Expense(
                    amount = parsed.amount,
                    category = parsed.category,
                    note = parsed.note,
                )
            )
            L3(
                "Logged ${parsed.amount} (${parsed.category}), sir ✅",
                "${parsed.amount} (${parsed.category}) درج، سر ✅",
                "${parsed.amount} (${parsed.category}) दर्ज, सर ✅",
            ).pick(lang)
        } catch (_: Exception) {
            L3("Hmm, couldn't log that expense, sir.",
                "ہمم، یہ خرچ درج نہیں ہو سکا، سر۔",
                "हम्म, यह खर्च दर्ज नहीं हो सका, सर।").pick(lang)
        }
    }

    private fun monthlyExpenses(appCtx: Context, lang: BalochLanguage): String {
        return try {
            val store = ExpenseStore(appCtx)
            val now = java.util.Calendar.getInstance()
            val list = expensesForMonth(store.all(), now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH))
            if (list.isEmpty()) {
                return L3(
                    "No expenses logged this month, sir — clean slate! 🧾",
                    "اس ماہ کوئی خرچ نہیں، سر — صاف سلیٹ! 🧾",
                    "इस महीने कोई खर्च नहीं, सर — साफ़ स्लेट! 🧾",
                ).pick(lang)
            }
            val totals = monthlyHisaab(list)
            val lines = totals.joinToString("\n") { "• ${it.category}: ${it.total}" }
            val grand = totals.sumOf { it.total }
            L3(
                "This month: $grand total\n$lines",
                "اس ماہ: مجموعی $grand\n$lines",
                "इस महीने: कुल $grand\n$lines",
            ).pick(lang)
        } catch (_: Exception) {
            L3("Hmm, couldn't read the ledger, sir.",
                "ہمم، حساب نہیں پڑھ سکا، سر۔",
                "हम्म, हिसाब नहीं पढ़ सका, सर।").pick(lang)
        }
    }

    private fun startFocus(appCtx: Context, minutes: Int?, lang: BalochLanguage): String {
        val mins = minutes ?: 25
        return try {
            val res = FocusManager.startFocus(appCtx, mins, 5)
            val dndNote = if (res.dndEnabled) "" else L3(
                " (DND needs the grant: Settings → Apps → Baloch → Do Not Disturb access)",
                " (DND کے لیے اجازت: Settings → Apps → Baloch → Do Not Disturb access)",
                " (DND के लिए अनुमति: Settings → Apps → Baloch → Do Not Disturb access)",
            ).pick(lang)
            L3(
                "Focus mode: $mins minutes, sir. Phone's quiet — you've got this. 🎯$dndNote",
                "فوکس موڈ: $mins منٹ، سر۔ فون خاموش — آپ کر سکتے ہیں۔ 🎯$dndNote",
                "फ़ोकस मोड: $mins मिनट, सर। फ़ोन शांत — आप कर सकते हैं। 🎯$dndNote",
            ).pick(lang)
        } catch (_: Exception) {
            L3("Hmm, focus mode wouldn't start, sir.",
                "ہمم، فوکس موڈ شروع نہیں ہوا، سر۔",
                "हम्म, फ़ोकस मोड शुरू नहीं हुआ, सर।").pick(lang)
        }
    }

    private fun stopFocus(appCtx: Context, lang: BalochLanguage): String {
        return try {
            FocusManager.stopFocus(appCtx)
            L3(
                "Focus over, sir — welcome back! 🎯",
                "فوکس ختم، سر — خوش آمدید! 🎯",
                "फ़ोकस ख़त्म, सर — स्वागत है! 🎯",
            ).pick(lang)
        } catch (_: Exception) {
            L3("Hmm, couldn't stop it cleanly, sir.",
                "ہمم، صاف طور پر نہیں روک سکا، سر۔",
                "हम्म, साफ़ तौर पर नहीं रोक सका, सर।").pick(lang)
        }
    }

    private fun geoReminder(appCtx: Context, parsed: ParsedGeoReminder, lang: BalochLanguage): String {
        return L3(
            "Location reminders need a place pin, sir — open Settings → Automation → Location reminders to drop the pin for \"${parsed.label}\".",
            "لوکیشن یاددہانی کے لیے جگہ منتخب کریں، سر — Settings → Automation → Location reminders میں \"${parsed.label}\" کے لیے پن لگائیں۔",
            "लोकेशन रिमाइंडर के लिए जगह चुनें, सर — Settings → Automation → Location reminders में \"${parsed.label}\" के लिए पिन लगाएँ।",
        ).pick(lang)
    }

    private suspend fun translate(
        appCtx: Context,
        parsed: ParsedTranslation,
        lang: BalochLanguage,
    ): String {
        return try {
            val translator = BalochTranslator(appCtx)
            // Heuristic: if the text looks like Urdu/Arabic script, target English;
            // otherwise target Urdu. The picker in Settings refines this.
            val looksUrdu = parsed.text.any { it in '؀'..'ۿ' }
            val (src, dst) = if (looksUrdu) BalochLanguage.URDU to BalochLanguage.ENGLISH
            else BalochLanguage.ENGLISH to BalochLanguage.URDU
            when (val r = translator.translate(parsed.text, src, dst)) {
                is TranslateResult.Done -> r.translated
                is TranslateResult.ModelMissing -> L3(
                    "Sir, the ${r.pairName} translation pack isn't downloaded yet — tap download in Settings → Voice → Translator.",
                    "سر، ${r.pairName} ٹرانسلیشن پیک ڈاؤن لوڈ نہیں — Settings → Voice → Translator میں ڈاؤن لوڈ کریں۔",
                    "सर, ${r.pairName} अनुवाद पैक डाउनलोड नहीं — Settings → Voice → Translator में डाउनलोड करें।",
                ).pick(lang)
                is TranslateResult.Failed -> L3(
                    "Hmm, translation failed, sir.",
                    "ہمم، ترجمہ ناکام، سر۔",
                    "हम्म, अनुवाद विफल, सर।",
                ).pick(lang)
            }
        } catch (_: Exception) {
            L3("Hmm, translation failed, sir.",
                "ہمم، ترجمہ ناکام، سر۔",
                "हम्म, अनुवाद विफल, सर।").pick(lang)
        }
    }

    private suspend fun downloadModel(appCtx: Context, lang: BalochLanguage): String {
        return try {
            val translator = BalochTranslator(appCtx)
            // EN↔UR is the pair Kamran uses; download both directions.
            val err = translator.downloadModel(BalochLanguage.ENGLISH, BalochLanguage.URDU, requireWifi = false)
                ?: translator.downloadModel(BalochLanguage.URDU, BalochLanguage.ENGLISH, requireWifi = false)
            if (err == null) {
                L3(
                    "Translation packs ready, sir 🌐",
                    "ٹرانسلیشن پیک تیار، سر 🌐",
                    "अनुवाद पैक तैयार, सर 🌐",
                ).pick(lang)
            } else {
                L3(
                    "Couldn't download, sir ($err) — check internet and try from Settings.",
                    "ڈاؤن لوڈ نہیں ہو سکا، سر ($err) — انٹرنیٹ چیک کریں۔",
                    "डाउनलोड नहीं हो सका, सर ($err) — इंटरनेट जाँचें।",
                ).pick(lang)
            }
        } catch (e: Exception) {
            L3(
                "Couldn't start the download, sir — check internet and try from Settings.",
                "ڈاؤن لوڈ شروع نہیں ہو سکا، سر — انٹرنیٹ چیک کریں۔",
                "डाउनलोड शुरू नहीं हो सका, सर — इंटरनेट जाँचें।",
            ).pick(lang)
        }
    }
}
