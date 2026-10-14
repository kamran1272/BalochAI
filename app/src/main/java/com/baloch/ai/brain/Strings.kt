package com.baloch.ai.brain

/**
 * Daily motivational lines, one per language. The brain picks one at
 * random for the "thought" intent. en-GB spelling in the English set.
 */
val DAILY_THOUGHTS: List<L3> = listOf(
    L3(
        en = "Consistency beats intensity, sir. One more honest hour of craft today.",
        ur = "مستقل مزاجی شدت پر غالب آتی ہے، سر۔ آج بھی دیانت داری سے ایک گھنٹہ مزید محنت کریں۔",
        hi = "निरंतरता तीव्रता से जीतती है, सर। आज ईमानदारी से एक घंटा और मेहनत करें।",
    ),
    L3(
        en = "Your portfolio is your loudest voice, sir. Let the work speak.",
        ur = "آپ کا پورٹ فولیو آپ کی سب سے بلند آواز ہے، سر۔ کام کو بولنے دیں۔",
        hi = "आपका पोर्टफोलियो आपकी सबसे बुलंद आवाज़ है, सर। काम को बोलने दें।",
    ),
    L3(
        en = "Small ships, steady sails, sir. Freelancing rewards those who show up daily.",
        ur = "چھوٹی کشتی، مستحکم بادبان، سر۔ فری لانسنگ میں روز حاضر ہونے والوں کو صلہ ملتا ہے۔",
        hi = "छोटी नाव, स्थिर पाल, सर। फ्रीलांसिंग में रोज़ हाज़िर रहने वालों को फल मिलता है।",
    ),
    L3(
        en = "Learn one thing deeply today, sir. Depth is what clients pay for.",
        ur = "آج ایک چیز گہرائی سے سیکھیں، سر۔ گہرائی ہی وہ ہے جس کے لیے کلائنٹ ادائیگی کرتے ہیں۔",
        hi = "आज एक चीज़ गहराई से सीखें, सर। गहराई ही वह है जिसके लिए क्लाइंट भुगतान करते हैं।",
    ),
    L3(
        en = "Done is better than perfect, sir — ship it, then sharpen it.",
        ur = "مکمل سے بہتر ہے کہ کام ہو جائے، سر — پہلے بھیجیں، پھر بہتر بنائیں۔",
        hi = "परफेक्ट से बेहतर है कि काम हो जाए, सर — पहले भेजें, फिर निखारें।",
    ),
    L3(
        en = "Every expert was once a beginner who refused to quit, sir.",
        ur = "ہر ماہر کبھی ایک ابتدائی تھا جس نے ہار ماننے سے انکار کیا، سر۔",
        hi = "हर विशेषज्ञ कभी एक शुरुआती था जिसने हार मानने से इनकार किया, सर।",
    ),
    L3(
        en = "Protect your mornings, sir. Deep work before the world wakes up.",
        ur = "اپنی صبحوں کی حفاظت کریں، سر۔ دنیا کے جاگنے سے پہلے گہرا کام۔",
        hi = "अपनी सुबहों की रक्षा करें, सर। दुनिया के जागने से पहले गहरा काम।",
    ),
    L3(
        en = "Rejection is redirection, sir. The right client is still out there.",
        ur = "مسترد ہونا دراصل نئی سمت ہے، سر۔ صحیح کلائنٹ ابھی کہیں موجود ہے۔",
        hi = "अस्वीकृति वास्तव में नई दिशा है, सर। सही क्लाइंट अभी कहीं मौजूद है।",
    ),
    L3(
        en = "Charge for the value, not the hours, sir. Your skill compounds.",
        ur = "گھنٹوں کے لیے نہیں، قدر کے لیے معاوضہ لیں، سر۔ آپ کی مہارت بڑھتی رہتی ہے۔",
        hi = "घंटों के लिए नहीं, मूल्य के लिए शुल्क लें, सर। आपका कौशल बढ़ता रहता है।",
    ),
    L3(
        en = "Build in public, sir. Someone is watching and taking notes.",
        ur = "سب کے سامنے بنائیں، سر۔ کوئی دیکھ رہا ہے اور نوٹ کر رہا ہے۔",
        hi = "सबके सामने बनाएँ, सर। कोई देख रहा है और नोट कर रहा है।",
    ),
)

/** Shared reply templates used across intents. */
object Replies {
    val GREETING = L3(
        en = "At your service, sir. How can I help?",
        ur = "حاضر ہوں، سر۔ میں آپ کی کیا مدد کر سکتا ہوں؟",
        hi = "हाज़िर हूँ, सर। मैं आपकी क्या मदद कर सकता हूँ?",
    )
    val NOT_UNDERSTOOD = L3(
        en = "I didn't quite catch that, sir. Try 'open WhatsApp', 'set an alarm', or 'battery status'.",
        ur = "میں سمجھ نہیں پایا، سر۔ 'واٹس ایپ کھولو'، 'الارم لگاؤ' یا 'بیٹری بتاؤ' آزمائیں۔",
        hi = "मैं समझ नहीं पाया, सर। 'व्हाट्सऐप खोलो', 'अलार्म लगाओ' या 'बैटरी बताओ' आज़माएँ।",
    )
    val PERMISSION_NEEDED = L3(
        en = "I need the {perm} permission for that, sir. Please grant it in Settings.",
        ur = "اس کے لیے مجھے {perm} کی اجازت درکار ہے، سر۔ براہ کرم سیٹنگز میں اجازت دیں۔",
        hi = "इसके लिए मुझे {perm} की अनुमति चाहिए, सर। कृपया सेटिंग्स में अनुमति दें।",
    )
    val CONFIRM_CALL = L3(
        en = "Shall I call {name}, sir?",
        ur = "کیا میں {name} کو کال کروں، سر؟",
        hi = "क्या मैं {name} को कॉल करूँ, सर?",
    )
    val CONFIRM_SMS = L3(
        en = "Shall I send this message to {name}, sir?",
        ur = "کیا میں یہ پیغام {name} کو بھیجوں، سر؟",
        hi = "क्या मैं यह संदेश {name} को भेजूँ, सर?",
    )
    val CONTACT_NOT_FOUND = L3(
        en = "I couldn't find {name} in your contacts, sir.",
        ur = "مجھے آپ کے رابطوں میں {name} نہیں ملا، سر۔",
        hi = "मुझे आपके संपर्कों में {name} नहीं मिला, सर।",
    )
    val NOTIFICATIONS_EMPTY = L3(
        en = "No recent notifications, sir. All quiet.",
        ur = "کوئی حالیہ اطلاع نہیں، سر۔ سب خاموش ہے۔",
        hi = "कोई हालिया सूचना नहीं, सर। सब शांत है।",
    )
    val NOTIFICATIONS_DISABLED = L3(
        en = "Notification access is off, sir. Enable it in Settings so I can read your notifications.",
        ur = "اطلاعات تک رسائی بند ہے، سر۔ سیٹنگز میں اسے فعال کریں تاکہ میں آپ کی اطلاعات پڑھ سکوں۔",
        hi = "सूचना एक्सेस बंद है, सर। सेटिंग्स में इसे चालू करें ताकि मैं आपकी सूचनाएँ पढ़ सकूँ।",
    )
    val NOTES_EMPTY = L3(
        en = "No notes yet, sir. Say 'note: buy milk' to save one.",
        ur = "ابھی کوئی نوٹ نہیں، سر۔ محفوظ کرنے کے لیے کہیں 'نوٹ: دودھ خریدنا ہے'۔",
        hi = "अभी कोई नोट नहीं, सर। सहेजने के लिए कहें 'नोट: दूध खरीदना है'।",
    )
    val VOICE_UNAVAILABLE = L3(
        en = "Speech recognition isn't available on this device, sir.",
        ur = "اس ڈیوائس پر آواز کی شناخت دستیاب نہیں، سر۔",
        hi = "इस डिवाइस पर वॉयस पहचान उपलब्ध नहीं है, सर।",
    )
    val VOICE_FALLBACK_ENGLISH = L3(
        en = "That language isn't supported for voice here, sir — I'll listen in English instead.",
        ur = "یہاں آواز کے لیے یہ زبان معاون نہیں، سر — میں انگریزی میں سنوں گا۔",
        hi = "यहाँ वॉयस के लिए यह भाषा समर्थित नहीं है, सर — मैं अंग्रेज़ी में सुनूँगा।",
    )

    // ------------------------------------------------------------------
    // PRO PACK — warm, friendly, a little playful. Never a cold error dump.
    // ------------------------------------------------------------------

    val USAGE_ACCESS_NEEDED = L3(
        en = "To read your screen time I need Usage access, sir — it's a quick one:\nSettings → Apps → Special app access → Usage access → Baloch ✅",
        ur = "آپ کا اسکرین ٹائم پڑھنے کے لیے مجھے Usage access چاہیے، سر — بس یہ کریں:\nسیٹنگز → ایپس → Special app access → Usage access → بلوچ ✅",
        hi = "आपका स्क्रीन टाइम पढ़ने के लिए मुझे Usage access चाहिए, सर — बस यह करें:\nसेटिंग्स → ऐप्स → Special app access → Usage access → Baloch ✅",
    )
    val USAGE_EMPTY = L3(
        en = "Barely any screen time today, sir — either you're being very disciplined, or the phone's been napping. 📱😄",
        ur = "آج تو اسکرین ٹائم نہ ہونے کے برابر ہے، سر — یا تو آپ بہت منظم ہیں، یا فون سو رہا تھا۔ 📱😄",
        hi = "आज तो स्क्रीन टाइम न के बराबर है, सर — या तो आप बहुत अनुशासित हैं, या फ़ोन सो रहा था। 📱😄",
    )
    val USAGE_NO_MATCH = L3(
        en = "Hmm, I didn't spot {q} in today's usage, sir — maybe it hasn't been opened yet today. 🔍",
        ur = "ہمم، آج کے استعمال میں {q} نظر نہیں آیا، سر — شاید آج ابھی کھولا ہی نہیں۔ 🔍",
        hi = "हमम, आज के इस्तेमाल में {q} नज़र नहीं आया, सर — शायद आज अभी खोला ही नहीं। 🔍",
    )
    val ROUTINE_SAVED = L3(
        en = "Ho gaya, sir! ✅ Routine saved — just say '{trigger}' and I'll run it for you.",
        ur = "ہو گیا، سر! ✅ روٹین محفوظ ہو گئی — بس '{trigger}' کہیں اور میں چلا دوں گا۔",
        hi = "हो गया, सर! ✅ रूटीन सहेज ली — बस '{trigger}' कहें और मैं चला दूँगा।",
    )
    val ROUTINE_NO_ACTIONS = L3(
        en = "Hmm, ye nahi ho paya, sir — I couldn't make sense of those steps. Try something like 'when I say movie time, dnd on and brightness 20'. 🎬",
        ur = "ہمم، یہ نہیں ہو پایا، سر — میں ان مراحل کو سمجھ نہیں پایا۔ ایسے آزمائیں: 'when I say movie time, dnd on and brightness 20'۔ 🎬",
        hi = "हमम, यह नहीं हो पाया, सर — मैं उन चरणों को समझ नहीं पाया। ऐसे आज़माएँ: 'when I say movie time, dnd on and brightness 20'। 🎬",
    )
    val ROUTINE_RUNNING = L3(
        en = "On it, sir — running '{name}'. ✅",
        ur = "ابھی کرتا ہوں، سر — '{name}' چلا رہا ہوں۔ ✅",
        hi = "अभी करता हूँ, सर — '{name}' चला रहा हूँ। ✅",
    )
    val GOOD_NIGHT_REPLY = L3(
        en = "Good night, sir. 🌙 DND is on and brightness is low — sleep well. Want a morning alarm too? Just say 'alarm lagao subah 7 baje'.",
        ur = "شب بخیر، سر۔ 🌙 ڈی این ڈی آن ہے اور روشنی مدھم — اچھی نیند سوئیں۔ صبح کا الارم بھی چاہیے؟ بس کہیں 'alarm lagao subah 7 baje'۔",
        hi = "शुभ रात्रि, सर। 🌙 DND चालू है और रोशनी धीमी — अच्छी नींद सोएँ। सुबह का अलार्म भी चाहिए? बस कहें 'alarm lagao subah 7 baje'।",
    )
    val MEETING_MODE_REPLY = L3(
        en = "Meeting mode, sir — DND on for the next hour, and I'll lift it automatically when you're done. Knock it out of the park. 🤫",
        ur = "میٹنگ موڈ، سر — اگلے ایک گھنٹے کے لیے ڈی این ڈی آن، اور ختم ہوتے ہی میں خود بند کر دوں گا۔ کمال کر دیں۔ 🤫",
        hi = "मीटिंग मोड, सर — अगले एक घंटे के लिए DND चालू, और ख़त्म होते ही मैं खुद बंद कर दूँगा। कमाल कर दें। 🤫",
    )
    val ICE_NOT_SET = L3(
        en = "I don't have an emergency contact yet, sir — set one first in Settings → Emergency contact, and I'll be ready when it matters. 🛟",
        ur = "میرے پاس ابھی کوئی ایمرجنسی رابطہ نہیں، سر — پہلے سیٹنگز → Emergency contact میں درج کریں، پھر ضرورت کے وقت میں حاضر ہوں گا۔ 🛟",
        hi = "मेरे पास अभी कोई आपातकालीन संपर्क नहीं है, सर — पहले सेटिंग्स → Emergency contact में दर्ज करें, फिर ज़रूरत के समय मैं हाज़िर हूँगा। 🛟",
    )
    val CONFIRM_EMERGENCY = L3(
        en = "This will call {name} at {number} right away, sir — tap confirm only if you're sure. 🚨",
        ur = "یہ ابھی {name} کو {number} پر کال کرے گا، سر — صرف تب تصدیق کریں جب یقین ہو۔ 🚨",
        hi = "यह अभी {name} को {number} पर कॉल करेगा, सर — तभी कन्फ़र्म करें जब यक़ीन हो। 🚨",
    )
    val WHATSAPP_DRAFT_READY = L3(
        en = "Opening WhatsApp with your message to {name}, sir — just hit send. 📩",
        ur = "{name} کے لیے آپ کا پیغام لے کر واٹس ایپ کھول رہا ہوں، سر — بس بھیج دیں۔ 📩",
        hi = "{name} के लिए आपका संदेश लेकर WhatsApp खोल रहा हूँ, सर — बस भेज दें। 📩",
    )
    val CALC_FAILED = L3(
        en = "Hmm, ye nahi ho paya, sir — that one stumped me. Try something like 'calculate 25% of 800'. 🧮",
        ur = "ہمم، یہ حساب نہیں ہو پایا، سر — یہ تو مجھے بھی چکرا گیا۔ 'calculate 25% of 800' آزمائیں۔ 🧮",
        hi = "हमम, यह हिसाब नहीं हो पाया, सर — यह तो मुझे भी चकरा गया। 'calculate 25% of 800' आज़माएँ। 🧮",
    )
    val CONVERT_UNKNOWN = L3(
        en = "Hmm, I don't know that currency, sir — I know dollars, euros, pounds, rupees, dirhams and riyals. 💱",
        ur = "ہمم، یہ کرنسی میں نہیں جانتا، سر — میں ڈالر، یورو، پاؤنڈ، روپے، درہم اور ریال جانتا ہوں۔ 💱",
        hi = "हमम, यह मुद्रा मैं नहीं जानता, सर — मैं डॉलर, यूरो, पाउंड, रुपये, दिरहम और रियाल जानता हूँ। 💱",
    )
    val CONVERT_OFFLINE_NOTE = L3(
        en = "Live rates were unreachable, sir — using my last saved ones, so they may be a little dated. 📶",
        ur = "براہِ راست نرخ نہیں مل سکے، سر — محفوظ شدہ پرانے نرخ استعمال کر رہا ہوں، اس لیے تھوڑے پرانے ہو سکتے ہیں۔ 📶",
        hi = "लाइव दरें नहीं मिल सकीं, सर — सहेजी हुई पुरानी दरें इस्तेमाल कर रहा हूँ, इसलिए थोड़ी पुरानी हो सकती हैं। 📶",
    )
    val CONVERT_FAILED = L3(
        en = "Hmm, ye nahi ho paya, sir — I couldn't reach the rates server and I have nothing saved. Check your connection and try again? 💱",
        ur = "ہمم، یہ نہیں ہو پایا، سر — نرخ سرور تک رسائی نہیں ہو سکی اور میرے پاس محفوظ بھی کچھ نہیں۔ کنکشن چیک کر کے دوبارہ کوشش کریں؟ 💱",
        hi = "हमम, यह नहीं हो पाया, सर — दर सर्वर तक पहुँच नहीं हो सकी और मेरे पास सहेजा भी कुछ नहीं। कनेक्शन जाँचकर दोबारा कोशिश करें? 💱",
    )
    val CLIPBOARD_EMPTY = L3(
        en = "Clipboard's empty, sir — copy something and I'll keep it here for you. 📋",
        ur = "کلپ بورڈ خالی ہے، سر — کچھ کاپی کریں، میں یہیں سنبھال کر رکھوں گا۔ 📋",
        hi = "क्लिपबोर्ड खाली है, सर — कुछ कॉपी करें, मैं यहीं सँभालकर रखूँगा। 📋",
    )
    val CLIPBOARD_NO_MATCH = L3(
        en = "Nothing in your recent copies about '{q}', sir. 📋🔍",
        ur = "آپ کی حالیہ کاپیوں میں '{q}' کے بارے میں کچھ نہیں، سر۔ 📋🔍",
        hi = "आपकी हालिया कॉपियों में '{q}' के बारे में कुछ नहीं, सर। 📋🔍",
    )
    val APPSEARCH_NONE = L3(
        en = "Hmm, couldn't find any app matching '{q}', sir — check the spelling? 🔍",
        ur = "ہمم، '{q}' سے ملتی کوئی ایپ نہیں ملی، سر — ہجے چیک کریں؟ 🔍",
        hi = "हमम, '{q}' से मिलता कोई ऐप नहीं मिला, सर — स्पेलिंग जाँचें? 🔍",
    )
    val DND_NEEDS_ACCESS = L3(
        en = "For do-not-disturb I need the DND access grant, sir — Settings → Apps → Baloch → Do Not Disturb access. One tap, then I'm all yours. 🤫",
        ur = "ڈو ناٹ ڈسٹرب کے لیے مجھے DND access چاہیے، سر — سیٹنگز → ایپس → بلوچ → Do Not Disturb access۔ ایک ٹیپ، پھر میں حاضر۔ 🤫",
        hi = "डू-नॉट-डिस्टर्ब के लिए मुझे DND एक्सेस चाहिए, सर — सेटिंग्स → ऐप्स → Baloch → Do Not Disturb access। एक टैप, फिर मैं हाज़िर। 🤫",
    )
    // ---- Voice Lock (speaker authentication) ----
    val VOICE_LOCK_REFUSED = L3(
        en = "That is not your voice, sir. I cannot obey.",
        ur = "یہ آپ کی آواز نہیں ہے، سر۔ میں حکم نہیں مان سکتا۔",
        hi = "यह आपकी आवाज़ नहीं है, सर। मैं आदेश नहीं मान सकता।",
    )
    val VOICE_LOCK_PIN_PROMPT = L3(
        en = "Your voice did not match, sir. Please enter your PIN to continue.",
        ur = "آپ کی آواز میل نہیں کھائی، سر۔ جاری رکھنے کے لیے براہ کرم اپنا پن درج کریں۔",
        hi = "आपकी आवाज़ मेल नहीं खाई, सर। जारी रखने के लिए कृपया अपना पिन दर्ज करें।",
    )
    val VOICE_LOCK_ENROLLED = L3(
        en = "Voice Lock is ready, sir. From now on, only your voice commands me.",
        ur = "وائس لاک تیار ہے، سر۔ اب سے صرف آپ کی آواز پر حکم مانوں گا۔",
        hi = "वॉइस लॉक तैयार है, सर। अब से केवल आपकी आवाज़ पर आदेश मानूँगा।",
    )
    val VOICE_LOCK_DISABLE_NEEDS_PROOF = L3(
        en = "To turn Voice Lock off, I need your voice or your PIN first, sir.",
        ur = "وائس لاک بند کرنے کے لیے پہلے مجھے آپ کی آواز یا آپ کا پن چاہیے، سر۔",
        hi = "वॉइस लॉक बंद करने के लिए पहले मुझे आपकी आवाज़ या आपका पिन चाहिए, सर।",
    )
    val VOICE_LOCK_STATUS_ON = L3(
        en = "Voice Lock is on, sir. Only your voice — or your PIN — can command me.",
        ur = "وائس لاک آن ہے، سر۔ صرف آپ کی آواز — یا آپ کا پن — مجھے حکم دے سکتا ہے۔",
        hi = "वॉइस लॉक चालू है, सर। केवल आपकी आवाज़ — या आपका पिन — मुझे आदेश दे सकता है।",
    )
    val VOICE_LOCK_STATUS_OFF = L3(
        en = "Voice Lock is off, sir. Say 'voice lock on' after enrollment to arm it.",
        ur = "وائس لاک آف ہے، سر۔ اسے فعال کرنے کے لیے اندراج کے بعد کہیں 'وائس لاک آن'۔",
        hi = "वॉइस लॉक बंद है, सर। इसे चालू करने के लिए नामांकन के बाद कहें 'वॉइस लॉक ऑन'।",
    )

    // ------------------------------------------------------------------
    // LIFE FEATURES — warm, friendly, a little playful. Never a cold
    // error dump. (life/ package)
    // ------------------------------------------------------------------

    val PRAYER_TIMES_EMPTY = L3(
        en = "I couldn't reach the prayer-times service and I have nothing saved, sir — check your connection and ask again? 🕌",
        ur = "نماز کے اوقات کی سروس تک رسائی نہیں ہو سکی اور میرے پاس محفوظ بھی کچھ نہیں، سر — کنکشن چیک کر کے دوبارہ پوچھیں؟ 🕌",
        hi = "नमाज़ के समय की सेवा तक पहुँच नहीं हो सकी और मेरे पास सहेजा भी कुछ नहीं, सर — कनेक्शन जाँचकर दोबारा पूछें? 🕌",
    )
    val PRAYER_TIMES_CACHED_NOTE = L3(
        en = "Showing yesterday's saved times, sir — I'll refresh when you're back online. 📶",
        ur = "محفوظ شدہ اوقات دکھا رہا ہوں، سر — آن لائن آتے ہی تازہ کر دوں گا۔ 📶",
        hi = "सहेजे हुए समय दिखा रहा हूँ, सर — ऑनलाइन आते ही ताज़ा कर दूँगा। 📶",
    )
    val QIBLA_NO_SENSOR = L3(
        en = "This phone doesn't have a rotation sensor, sir — but the Qibla from Lahore is roughly 257° west-southwest. 🧭",
        ur = "اس فون میں روٹیشن سینسر نہیں، سر — لیکن لاہور سے قبلہ تقریباً 257° مغرب-جنوب مغرب ہے۔ 🧭",
        hi = "इस फ़ोन में रोटेशन सेंसर नहीं है, सर — लेकिन लाहौर से क़िबला लगभग 257° पश्चिम-दक्षिणपश्चिम है। 🧭",
    )
    val CLIENT_ADDED = L3(
        en = "{name} is on your board, sir ✅ — say 'mere clients batao' any time to see everyone.",
        ur = "{name} آپ کے بورڈ پر آ گیا، سر ✅ — سب کو دیکھنے کے لیے کبھی بھی کہیں 'mere clients batao'۔",
        hi = "{name} आपके बोर्ड पर आ गया, सर ✅ — सबको देखने के लिए कभी भी कहें 'mere clients batao'।",
    )
    val CLIENTS_EMPTY = L3(
        en = "No clients on the board yet, sir — say 'add client Ahmed' and I'll start tracking.",
        ur = "بورڈ پر ابھی کوئی کلائنٹ نہیں، سر — کہیں 'add client Ahmed' اور میں ٹریکنگ شروع کر دوں گا۔",
        hi = "बोर्ड पर अभी कोई क्लाइंट नहीं, सर — कहें 'add client Ahmed' और मैं ट्रैकिंग शुरू कर दूँगा।",
    )
    val EXPENSE_PARSE_FAILED = L3(
        en = "Hmm, I didn't catch an amount there, sir — try '500 kharcha chai'. 💰",
        ur = "ہمم، رقم سمجھ نہیں آئی، سر — '500 kharcha chai' آزمائیں۔ 💰",
        hi = "हमम, राशि समझ नहीं आई, सर — '500 kharcha chai' आज़माएँ। 💰",
    )
    val FOCUS_ALREADY_RUNNING = L3(
        en = "You're already in focus mode, sir 🎯 — finish strong, the break will come.",
        ur = "آپ پہلے ہی فوکس موڈ میں ہیں، سر 🎯 — ڈٹے رہیں، وقفہ آئے گا۔",
        hi = "आप पहले ही फोकस मोड में हैं, सर 🎯 — डटे रहें, ब्रेक आएगा।",
    )
    val FOCUS_STOPPED = L3(
        en = "Focus mode off, sir — DND lifted. Well done on the deep work. 🎯",
        ur = "فوکس موڈ آف، سر — ڈی این ڈی ہٹا دیا۔ گہرے کام پر شاباش۔ 🎯",
        hi = "फोकस मोड बंद, सर — DND हटा दिया। गहरे काम पर शाबाश। 🎯",
    )
    val GEO_SAVED_NEEDS_PIN = L3(
        en = "Noted, sir 📍 — now pin the place on the map so I can watch for it, and I'll remind you the moment you arrive.",
        ur = "نوٹ کر لیا، سر 📍 — اب نقشے پر جگہ پن کریں تاکہ میں نظر رکھ سکوں، اور پہنچتے ہی یاد دلا دوں گا۔",
        hi = "नोट कर लिया, सर 📍 — अब नक्शे पर जगह पिन करें ताकि मैं नज़र रख सकूँ, और पहुँचते ही याद दिला दूँगा।",
    )
    val GEO_LOCATION_NEEDED = L3(
        en = "For location reminders I need location permission, sir — grant it when the system asks, then tell me the reminder again. 📍",
        ur = "لوکیشن یاد دہانی کے لیے مجھے لوکیشن کی اجازت چاہیے، سر — سسٹم کے پوچھنے پر اجازت دیں، پھر یاد دہانی دوبارہ بتائیں۔ 📍",
        hi = "लोकेशन याद दिलाने के लिए मुझे लोकेशन अनुमति चाहिए, सर — सिस्टम के पूछने पर अनुमति दें, फिर याद दोबारा बताएँ। 📍",
    )
    val TRANSLATE_EMPTY = L3(
        en = "What should I translate, sir? Try 'translate karo: where is the station'. 🌐",
        ur = "میں کیا ترجمہ کروں، سر؟ 'translate karo: where is the station' آزمائیں۔ 🌐",
        hi = "मैं क्या अनुवाद करूँ, सर? 'translate karo: where is the station' आज़माएँ। 🌐",
    )
    val CONFIRM_SOS = L3(
        en = "This will send an SOS SMS with your live location to your emergency contact, sir — tap confirm only if you're sure. 🚨",
        ur = "یہ آپ کے ایمرجنسی رابطے کو آپ کی لائیو لوکیشن کے ساتھ SOS ایس ایم ایس بھیجے گا، سر — صرف تب تصدیق کریں جب یقین ہو۔ 🚨",
        hi = "यह आपके आपातकालीन संपर्क को आपकी लाइव लोकेशन के साथ SOS SMS भेजेगा, सर — तभी कन्फ़र्म करें जब यक़ीन हो। 🚨",
    )
    val VIDEO_IDEAS_INTRO = L3(
        en = "Fresh ideas for you, sir 🎬 — pick one and say 'script banao: <topic>' for the outline:",
        ur = "آپ کے لیے تازہ آئیڈیاز، سر 🎬 — ایک چنیں اور خاکے کے لیے کہیں 'script banao: <topic>':",
        hi = "आपके लिए ताज़ा आइडिया, सर 🎬 — एक चुनें और रूपरेखा के लिए कहें 'script banao: <topic>':",
    )
}
