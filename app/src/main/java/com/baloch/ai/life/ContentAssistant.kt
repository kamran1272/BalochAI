package com.baloch.ai.life

import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3

/**
 * Content Assistant — idea bank, script outlines and trend scans for
 * Kamran's developer-freelancer content pipeline.
 *
 * Voice: "video idea do", "script banao: React hooks", "trending topics".
 * Trend scans are returned as a search query the parent maps to a
 * WebSearch DeviceAction (Baloch never invents trends offline).
 */

// ------------------------------------------------------------------
// Idea bank — 30+ ideas for a dev freelancer, one L3 per idea.
// ------------------------------------------------------------------

val VIDEO_IDEAS: List<L3> = listOf(
    L3("I rebuilt my portfolio in React in 48 hours — here's what changed",
        "میں نے 48 گھنٹوں میں اپنا پورٹ فولیو React میں دوبارہ بنایا — یہ ہے جو بدلا",
        "मैंने 48 घंटों में अपना पोर्टफोलियो React में दोबारा बनाया — यह है जो बदला"),
    L3("5 Laravel tricks that save me 10 hours every week",
        "5 Laravel ٹرکس جو ہر ہفتے میرے 10 گھنٹے بچاتی ہیں",
        "5 Laravel ट्रिक्स जो हर हफ़्ते मेरे 10 घंटे बचाती हैं"),
    L3("How I got my first Upwork client with zero reviews",
        "زیرو ریویوز کے ساتھ مجھے اپنا پہلا Upwork کلائنٹ کیسے ملا",
        "ज़ीरो रिव्यूज़ के साथ मुझे अपना पहला Upwork क्लाइंट कैसे मिला"),
    L3("React vs Laravel: which one actually pays more in 2026?",
        "React بمقابلہ Laravel: 2026 میں اصل میں کون زیادہ کماتا ہے؟",
        "React बनाम Laravel: 2026 में असल में कौन ज़्यादा कमाता है?"),
    L3("My exact proposal template that wins 1 in 4 jobs",
        "میرا وہی پروپوزل ٹیمپلیٹ جو 4 میں سے 1 جاب جتاتا ہے",
        "मेरा वही प्रपोज़ल टेम्पलेट जो 4 में से 1 जॉब जिताता है"),
    L3("A day in my life as a Pakistani freelancer",
        "ایک پاکستانی فری لانسر کے طور پر میرا ایک دن",
        "एक पाकिस्तानी फ्रीलांसर के तौर पर मेरा एक दिन"),
    L3("10 VS Code extensions I can't live without",
        "10 VS Code ایکسٹینشنز جن کے بغیر میں نہیں رہ سکتا",
        "10 VS Code एक्सटेंशन जिनके बिना मैं नहीं रह सकता"),
    L3("I let AI write my code for a week — honest results",
        "میں نے ایک ہفتے AI سے کوڈ لکھوایا — ایماندارانہ نتائج",
        "मैंने एक हफ़्ते AI से कोड लिखवाया — ईमानदार नतीजे"),
    L3("How to price your first freelance project (no more guessing)",
        "اپنا پہلا فری لانس پروجیکٹ کیسے قیمت لگائیں (اندازے ختم)",
        "अपना पहला फ्रीलांस प्रोजेक्ट कैसे प्राइस करें (अंदाज़े ख़त्म)"),
    L3("Building a salon booking system from scratch — part 1",
        "سیلون بکنگ سسٹم شروع سے بنانا — حصہ 1",
        "सैलून बुकिंग सिस्टम शुरू से बनाना — भाग 1"),
    L3("The Git commands I use every single day",
        "وہ Git کمانڈز جو میں ہر روز استعمال کرتا ہوں",
        "वह Git कमांड जो मैं हर रोज़ इस्तेमाल करता हूँ"),
    L3("Client said 'make it pop' — here's what I actually did",
        "کلائنٹ نے کہا 'make it pop' — میں نے اصل میں یہ کیا",
        "क्लाइंट ने कहा 'make it pop' — मैंने असल में यह किया"),
    L3("My home office setup under Rs 30,000",
        "30,000 روپے سے کم میں میرا ہوم آفس سیٹ اپ",
        "30,000 रुपये से कम में मेरा होम ऑफिस सेटअप"),
    L3("Laravel API in 20 minutes — full crash build",
        "20 منٹ میں Laravel API — مکمل کریش بلڈ",
        "20 मिनट में Laravel API — पूरा क्रैश बिल्ड"),
    L3("Why Pakistani freelancers are winning right now",
        "پاکستانی فری لانسرز اس وقت کیوں جیت رہے ہیں",
        "पाकिस्तानी फ्रीलांसर इस वक़्त क्यों जीत रहे हैं"),
    L3("I reviewed 50 Fiverr gigs so you don't have to",
        "میں نے 50 Fiverr گگز کا جائزہ لیا تاکہ آپ کو نہ کرنا پڑے",
        "मैंने 50 Fiverr गिग्स की समीक्षा की ताकि आपको न करना पड़े"),
    L3("React hooks explained like you're five",
        "React hooks ایسے سمجھائیں جیسے آپ پانچ سال کے ہوں",
        "React hooks ऐसे समझाएँ जैसे आप पाँच साल के हों"),
    L3("How I handle difficult clients without losing my cool",
        "مشکل کلائنٹس کو میں پرسکون رہ کر کیسے سنبھالتا ہوں",
        "मुश्किल क्लाइंट्स को मैं शांत रहकर कैसे सँभालता हूँ"),
    L3("MySQL vs the world: when to use what",
        "MySQL بمقابلہ دنیا: کب کیا استعمال کریں",
        "MySQL बनाम दुनिया: कब क्या इस्तेमाल करें"),
    L3("From Bhakkar to the world: my freelancing story",
        "بھکر سے دنیا تک: میری فری لانسنگ کہانی",
        "भक्कर से दुनिया तक: मेरी फ्रीलांसिंग कहानी"),
    L3("The follow-up message that revived a dead lead",
        "وہ فالو اپ پیغام جس نے مردہ لیڈ کو زندہ کر دیا",
        "वह फॉलो-अप संदेश जिसने मरी हुई लीड को ज़िंदा कर दिया"),
    L3("Tailwind in 15 minutes — everything that matters",
        "15 منٹ میں Tailwind — ہر وہ چیز جو اہم ہے",
        "15 मिनट में Tailwind — हर वह चीज़ जो अहम है"),
    L3("I automated my invoices with a free script",
        "میں نے مفت اسکرپٹ سے اپنے انوائسز خودکار کر دیے",
        "मैंने मुफ़्त स्क्रिप्ट से अपने इनवॉइस स्वचालित कर दिए"),
    L3("Reading client minds: what 'urgent' really means",
        "کلائنٹ کے ذہن پڑھنا: 'urgent' کا اصل مطلب کیا ہے",
        "क्लाइंट के मन पढ़ना: 'urgent' का असल मतलब क्या है"),
    L3("My biggest freelancing mistake (cost me $400)",
        "میری سب سے بڑی فری لانسنگ غلطی (مجھے $400 کا نقصان)",
        "मेरी सबसे बड़ी फ्रीलांसिंग ग़लती (मुझे $400 का नुक़सान)"),
    L3("Building in public: week 1 of my SaaS",
        "سب کے سامنے بنانا: میرے SaaS کا ہفتہ 1",
        "सबके सामने बनाना: मेरे SaaS का हफ़्ता 1"),
    L3("PHP is NOT dead — here's the proof",
        "PHP مردہ نہیں ہے — یہ ہے ثبوت",
        "PHP मरी नहीं है — यह है सबूत"),
    L3("How I learn a new framework in one weekend",
        "میں ایک ہفتے کے آخر میں نیا فریم ورک کیسے سیکھتا ہوں",
        "मैं एक हफ़्ते के अंत में नया फ्रेमवर्क कैसे सीखता हूँ"),
    L3("The portfolio section clients actually read",
        "پورٹ فولیو کا وہ حصہ جو کلائنٹس اصل میں پڑھتے ہیں",
        "पोर्टफोलियो का वह हिस्सा जो क्लाइंट्स असल में पढ़ते हैं"),
    L3("Freelancer red flags: clients to avoid",
        "فری لانسر ریڈ فلیگز: کن کلائنٹس سے بچیں",
        "फ्रीलांसर रेड फ्लैग: किन क्लाइंट्स से बचें"),
    L3("My exact morning routine as a developer",
        "ایک ڈیویلپر کے طور پر میری صبح کی درست روٹین",
        "एक डेवलपर के तौर पर मेरी सुबह की सही रूटीन"),
    L3("Deploying Laravel on a Rs 1,500 VPS",
        "1,500 روپے والے VPS پر Laravel ڈیپلائے کرنا",
        "1,500 रुपये वाले VPS पर Laravel डिप्लॉय करना"),
)

/** [count] random ideas, no repeats. */
fun randomIdeas(count: Int): List<L3> =
    VIDEO_IDEAS.shuffled().take(count.coerceIn(1, VIDEO_IDEAS.size))

// ------------------------------------------------------------------
// Script outline generator — hook → 3 points → CTA, filled from a topic.
// ------------------------------------------------------------------

private val SCRIPT_TEMPLATE = L3(
    en = "🎬 Script outline for \"{topic}\", sir:\n\n" +
        "HOOK (first 3 seconds):\n" +
        "\"Stop scrolling — {topic} is easier than you think, and I'll prove it in 60 seconds.\"\n\n" +
        "POINT 1 — The mistake everyone makes:\n" +
        "Show the common wrong way people approach {topic}.\n\n" +
        "POINT 2 — The simple fix:\n" +
        "Demo your way of doing {topic} in 2–3 clear steps.\n\n" +
        "POINT 3 — The proof:\n" +
        "Show the before/after result on screen.\n\n" +
        "CTA:\n" +
        "\"Follow for daily dev tips, sir's orders 😉 — comment '{ctaWord}' and I'll send you my notes.\"",
    ur = "🎬 \"{topic}\" کے لیے اسکرپٹ خاکہ، سر:\n\n" +
        "ہک (پہلے 3 سیکنڈ):\n" +
        "\"رکیں — {topic} آپ کے خیال سے آسان ہے، اور میں 60 سیکنڈ میں ثابت کروں گا۔\"\n\n" +
        "نکتہ 1 — وہ غلطی جو سب کرتے ہیں:\n" +
        "{topic} کے بارے میں غلط طریقہ دکھائیں۔\n\n" +
        "نکتہ 2 — آسان حل:\n" +
        "2–3 واضح مراحل میں اپنا طریقہ دکھائیں۔\n\n" +
        "نکتہ 3 — ثبوت:\n" +
        "اسکرین پر پہلے/بعد کا نتیجہ دکھائیں۔\n\n" +
        "سی ٹی اے:\n" +
        "\"روزانہ ڈیو ٹپس کے لیے فالو کریں 😉 — '{ctaWord}' کمنٹ کریں، میں اپنے نوٹس بھیج دوں گا۔\"",
    hi = "🎬 \"{topic}\" के लिए स्क्रिप्ट रूपरेखा, सर:\n\n" +
        "हुक (पहले 3 सेकंड):\n" +
        "\"रुकें — {topic} आपके ख़याल से आसान है, और मैं 60 सेकंड में साबित करूँगा।\"\n\n" +
        "बिंदु 1 — वह ग़लती जो सब करते हैं:\n" +
        "{topic} के बारे में ग़लत तरीका दिखाएँ।\n\n" +
        "बिंदु 2 — आसान हल:\n" +
        "2–3 स्पष्ट चरणों में अपना तरीका दिखाएँ।\n\n" +
        "बिंदु 3 — सबूत:\n" +
        "स्क्रीन पर पहले/बाद का नतीजा दिखाएँ।\n\n" +
        "सीटीए:\n" +
        "\"रोज़ाना डेव टिप्स के लिए फॉलो करें 😉 — '{ctaWord}' कमेंट करें, मैं अपने नोट्स भेज दूँगा।\"",
)

/** Builds a hook → points → CTA outline for [topic] in [lang]. */
fun scriptOutline(topic: String, lang: BalochLanguage): String {
    val clean = topic.trim().ifBlank { "freelancing" }
    val ctaWord = when (lang) {
        BalochLanguage.ENGLISH -> "NOTES"
        BalochLanguage.URDU -> "نوٹس"
        BalochLanguage.HINDI -> "नोट्स"
    }
    return SCRIPT_TEMPLATE.format(lang, "topic" to clean, "ctaWord" to ctaWord)
}

// ------------------------------------------------------------------
// Trend scan — Baloch never invents trends; it returns the query the
// parent maps to a WebSearch DeviceAction.
// ------------------------------------------------------------------

private val TREND_QUERY = L3(
    en = "trending web development topics {monthYear}",
    ur = "{monthYear} میں ویب ڈیولپمنٹ کے ٹرینڈنگ موضوعات",
    hi = "{monthYear} में वेब डेवलपमेंट के ट्रेंडिंग विषय",
)

/** Search query for a trend scan, in [lang]. Honest: needs the web. */
fun trendScanQuery(lang: BalochLanguage): String {
    val cal = java.util.Calendar.getInstance()
    val monthYear = "%04d-%02d".format(
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
    )
    return TREND_QUERY.format(lang, "monthYear" to monthYear)
}

private val TREND_INTRO = L3(
    en = "Let me check what's trending right now, sir — one moment. 🔍",
    ur = "دیکھتا ہوں اس وقت کیا ٹرینڈ کر رہا ہے، سر — ایک لمحہ۔ 🔍",
    hi = "देखता हूँ इस वक़्त क्या ट्रेंड कर रहा है, सर — एक पल। 🔍",
)

/** The spoken intro while the web search runs. */
fun trendScanIntro(lang: BalochLanguage): String = TREND_INTRO.pick(lang)
