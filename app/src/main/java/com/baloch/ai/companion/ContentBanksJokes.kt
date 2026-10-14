package com.baloch.ai.companion

import com.baloch.ai.brain.L3

/**
 * Trilingual content banks for the CompanionAgent.
 *
 * - [COMPANION_JOKES]: clean tech / programmer / freelancer humour.
 * - [COMPANION_FACTS]: tech, space, human body and history mix.
 * - [COMPANION_STORIES]: short ~1-minute warm, slightly motivational stories.
 * - Small-talk banks: "how are you" / "tum kaise ho" / "aur sunao".
 *
 * Every item is an [L3]: English (en-GB spelling), Urdu script, Devanagari.
 * Urdu script is kept TTS-safe per the L3 contract (ur-PK voice pronounces
 * script correctly; Roman Urdu would be mispronounced by TTS). Roman-Urdu
 * flavour lives in the natural spoken register of the replies instead.
 *
 * Picks go through [CompanionPicker] so the same item never repeats twice
 * in a row.
 */

// ------------------------------------------------------------------
// Jokes — 44, clean, warm, never mean
// ------------------------------------------------------------------

val COMPANION_JOKES: List<L3> = listOf(
    L3(
        en = "Why do programmers prefer dark mode? Because light attracts bugs.",
        ur = "پروگرامر ڈارک موڈ کیوں پسند کرتے ہیں؟ کیونکہ روشنی کیڑوں کو اپنی طرف کھینچتی ہے۔",
        hi = "प्रोग्रामर डार्क मोड क्यों पसंद करते हैं? क्योंकि रोशनी कीड़ों को आकर्षित करती है।",
    ),
    L3(
        en = "There are only 10 kinds of people in the world: those who understand binary, and those who don't.",
        ur = "دنیا میں صرف 10 قسم کے لوگ ہیں: جو بائنری سمجھتے ہیں، اور جو نہیں سمجھتے۔",
        hi = "दुनिया में सिर्फ़ 10 तरह के लोग हैं: जो बाइनरी समझते हैं, और जो नहीं समझते।",
    ),
    L3(
        en = "A SQL query walks into a bar, sees two tables and asks: 'Mind if I JOIN you?'",
        ur = "ایک ایس کیو ایل کوئری بار میں داخل ہوئی، دو ٹیبل دیکھے اور بولی: 'کیا میں JOIN ہو سکتی ہوں؟'",
        hi = "एक SQL क्वेरी बार में गई, दो टेबल देखे और बोली: 'क्या मैं JOIN हो सकती हूँ?'",
    ),
    L3(
        en = "Why did the developer go broke? He used up all his cache.",
        ur = "ڈویلپر کنگال کیوں ہو گیا؟ اس نے اپنا سارا کیش خرچ کر دیا تھا۔",
        hi = "डेवलपर कंगाल क्यों हो गया? उसने अपना सारा कैश खर्च कर दिया था।",
    ),
    L3(
        en = "I'd tell you a UDP joke, but you might not get it — and I wouldn't care.",
        ur = "میں آپ کو یو ڈی پی والا لطیفہ سناتا، لیکن شاید آپ کو سمجھ نہ آئے — اور مجھے پروا بھی نہ ہو۔",
        hi = "मैं आपको UDP वाला चुटकुला सुनाता, लेकिन शायद आपको समझ न आए — और मुझे परवाह भी न हो।",
    ),
    L3(
        en = "Debugging is like being the detective in a crime movie where you are also the murderer.",
        ur = "ڈی بگنگ ایسے ہے جیسے آپ کسی جرائم کی فلم میں جاسوس ہوں — اور قاتل بھی آپ خود ہوں۔",
        hi = "डिबगिंग ऐसी है जैसे आप किसी क्राइम फिल्म में जासूस हों — और कातिल भी आप खुद हों।",
    ),
    L3(
        en = "Why do Java developers wear glasses? Because they don't C#.",
        ur = "جاوا ڈویلپر عینک کیوں لگاتے ہیں؟ کیونکہ انہیں C# نظر نہیں آتا۔",
        hi = "जावा डेवलपर चश्मा क्यों लगाते हैं? क्योंकि उन्हें C# नज़र नहीं आता।",
    ),
    L3(
        en = "Real programmers count from zero, sir. Everyone else is just rounding up.",
        ur = "اصل پروگرامر صفر سے گنتی شروع کرتے ہیں، سر۔ باقی سب بس اندازہ لگاتے ہیں۔",
        hi = "असली प्रोग्रामर शून्य से गिनती शुरू करते हैं, सर। बाकी सब बस अंदाज़ा लगाते हैं।",
    ),
    L3(
        en = "My code never has bugs. It just develops random features.",
        ur = "میرے کوڈ میں کبھی بگ نہیں ہوتے۔ بس کبھی کبھار نئے فیچر خود بن جاتے ہیں۔",
        hi = "मेरे कोड में कभी बग नहीं होते। बस कभी-कभी नए फ़ीचर खुद बन जाते हैं।",
    ),
    L3(
        en = "'It works on my machine' — the freelancer's ancient and sacred motto.",
        ur = "'میرے کمپیوٹر پر تو چل رہا تھا' — فری لانسر کا قدیم اور مقدس نعرہ۔",
        hi = "'मेरे कंप्यूटर पर तो चल रहा था' — फ्रीलांसर का प्राचीन और पवित्र नारा।",
    ),
    L3(
        en = "Why did the function break up with the loop? Too many iterations of the same argument.",
        ur = "فنکشن نے لوپ سے رشتہ کیوں توڑا؟ ایک ہی بحث کو بار بار دہرانے سے تنگ آ گیا تھا۔",
        hi = "फ़ंक्शन ने लूप से रिश्ता क्यों तोड़ा? एक ही बहस को बार-बार दोहराने से तंग आ गया था।",
    ),
    L3(
        en = "A client said 'just make the logo bigger'. So I made my invoice bigger too.",
        ur = "کلائنٹ نے کہا 'بس لوگو بڑا کر دو'۔ تو میں نے اپنا بل بھی بڑا کر دیا۔",
        hi = "क्लाइंट ने कहा 'बस लोगो बड़ा कर दो'। तो मैंने अपना बिल भी बड़ा कर दिया।",
    ),
    L3(
        en = "There are two hard things in computer science: cache invalidation, naming things, and off-by-one errors.",
        ur = "کمپیوٹر سائنس میں دو مشکل کام ہیں: کیش انویلیڈیشن، نام رکھنا، اور آف بائی ون ایرر۔",
        hi = "कंप्यूटर साइंस में दो मुश्किल काम हैं: कैश इनवैलिडेशन, नाम रखना, और ऑफ़-बाय-वन एरर।",
    ),
    L3(
        en = "Why was the JavaScript developer sad? He didn't Node how to Express himself.",
        ur = "جاوا اسکرپٹ ڈویلپر اداس کیوں تھا؟ اسے سمجھ نہیں آیا کہ اپنے جذبات کا اظہار کیسے کرے۔",
        hi = "जावास्क्रिप्ट डेवलपर उदास क्यों था? उसे समझ नहीं आया कि अपने जज़्बात कैसे ज़ाहिर करे।",
    ),
    L3(
        en = "A bug in production is just an undocumented feature with ambition.",
        ur = "پروڈکشن میں بگ دراصل ایک غیر دستاویزی فیچر ہے جس کے خواب بڑے ہیں۔",
        hi = "प्रोडक्शन में बग दरअसल एक गैर-दस्तावेज़ी फ़ीचर है जिसके सपने बड़े हैं।",
    ),
    L3(
        en = "I changed my password to 'incorrect' — so when I forget it, the computer helpfully tells me my password is incorrect.",
        ur = "میں نے اپنا پاس ورڈ 'incorrect' رکھ دیا — اب جب بھولتا ہوں تو کمپیوٹر خود بتا دیتا ہے کہ پاس ورڈ غلط ہے۔",
        hi = "मैंने अपना पासवर्ड 'incorrect' रख दिया — अब जब भूलता हूँ तो कंप्यूटर खुद बता देता है कि पासवर्ड ग़लत है।",
    ),
    L3(
        en = "The best thing about a boolean is that even when you're wrong, you're only off by a bit.",
        ur = "بولین کی سب سے اچھی بات یہ ہے کہ غلط ہونے پر بھی آپ صرف ایک بِٹ سے غلط ہوتے ہیں۔",
        hi = "बूलियन की सबसे अच्छी बात यह है कि ग़लत होने पर भी आप सिर्फ़ एक बिट से ग़लत होते हैं।",
    ),
    L3(
        en = "Why did the developer quit his job? He didn't get arrays.",
        ur = "ڈویلپر نے نوکری کیوں چھوڑی؟ اسے arrays (تنخواہ میں اضافہ) نہیں ملا۔",
        hi = "डेवलपर ने नौकरी क्यों छोड़ी? उसे arrays (तनख़्वाह में बढ़ोतरी) नहीं मिला।",
    ),
    L3(
        en = "My Git commits read like my promises: 'final', 'final2', 'final_FINAL_v3'.",
        ur = "میرے گِٹ کمٹس میرے وعدوں جیسے ہیں: 'final'، 'final2'، 'final_FINAL_v3'۔",
        hi = "मेरे गिट कमिट्स मेरे वादों जैसे हैं: 'final', 'final2', 'final_FINAL_v3'।",
    ),
    L3(
        en = "A programmer put two glasses by his bed: one full of water in case he got thirsty, one empty in case he didn't.",
        ur = "ایک پروگرامر نے بستر کے پاس دو گلاس رکھے: ایک پانی سے بھرا کہ پیاس لگے تو، ایک خالی کہ نہ لگے تو۔",
        hi = "एक प्रोग्रामर ने बिस्तर के पास दो गिलास रखे: एक पानी से भरा कि प्यास लगे तो, एक खाली कि न लगे तो।",
    ),
    L3(
        en = "Why did the CSS developer go to therapy? Too many unresolved issues with his parents.",
        ur = "سی ایس ایس ڈویلپر تھراپی کے لیے کیوں گیا؟ اپنے پیرنٹس کے ساتھ بہت سے حل نہ ہونے والے مسائل تھے۔",
        hi = "CSS डेवलपर थेरेपी के लिए क्यों गया? अपने पैरेंट्स के साथ बहुत से अनसुलझे मुद्दे थे।",
    ),
    L3(
        en = "Algorithm: a word programmers use when they don't want to explain what they did.",
        ur = "الگورتھم: وہ لفظ جو پروگرامر تب استعمال کرتے ہیں جب وہ بتانا نہیں چاہتے کہ انہوں نے کیا کیا۔",
        hi = "एल्गोरिदम: वह शब्द जो प्रोग्रामर तब इस्तेमाल करते हैं जब वे बताना नहीं चाहते कि उन्होंने क्या किया।",
    ),
    L3(
        en = "My code is like my room: I know exactly where everything is, but nobody else does.",
        ur = "میرا کوڈ میرے کمرے جیسا ہے: مجھے معلوم ہے ہر چیز کہاں ہے، لیکن کسی اور کو نہیں۔",
        hi = "मेरा कोड मेरे कमरे जैसा है: मुझे मालूम है हर चीज़ कहाँ है, लेकिन किसी और को नहीं।",
    ),
    L3(
        en = "Why did the programmer get stuck in the shower? The instructions said: lather, rinse, repeat.",
        ur = "پروگرامر شاور میں کیوں پھنس گیا؟ ہدایات میں لکھا تھا: صابن لگاؤ، دھوؤ، دہراؤ۔",
        hi = "प्रोग्रामर शावर में क्यों फँस गया? निर्देशों में लिखा था: साबुन लगाओ, धोओ, दोहराओ।",
    ),
    L3(
        en = "Documentation is a love letter to your future self. Write it kindly, sir.",
        ur = "ڈاکیومنٹیشن آپ کے مستقبل کے آپ کے نام ایک محبت نامہ ہے۔ اسے پیار سے لکھیں، سر۔",
        hi = "डॉक्यूमेंटेशन आपके भविष्य के आप के नाम एक प्रेम पत्र है। इसे प्यार से लिखें, सर।",
    ),
    L3(
        en = "The cloud is just someone else's computer — a very expensive, very reliable someone else.",
        ur = "کلاؤڈ دراصل کسی اور کا کمپیوٹر ہے — بس وہ کوئی بہت مہنگا اور بہت قابلِ اعتماد ہے۔",
        hi = "क्लाउड दरअसल किसी और का कंप्यूटर है — बस वह कोई बहुत महँगा और बहुत भरोसेमंद है।",
    ),
    L3(
        en = "A freelancer's balanced diet: coffee, deadlines, and the sweet taste of a paid invoice.",
        ur = "فری لانسر کی متوازن خوراک: کافی، ڈیڈ لائنز، اور ادا شدہ بل کا میٹھا ذائقہ۔",
        hi = "फ्रीलांसर का संतुलित आहार: कॉफ़ी, डेडलाइन्स, और चुकाए गए बिल का मीठा स्वाद।",
    ),
    L3(
        en = "What do you call eight hobbits? A hobbyte.",
        ur = "آٹھ ہوبٹس کو کیا کہتے ہیں؟ ایک ہوب بائٹ۔",
        hi = "आठ हॉबिट्स को क्या कहते हैं? एक हॉबबाइट।",
    ),
    L3(
        en = "I asked my code for life advice. It said: 'Have you tried turning yourself off and on again?'",
        ur = "میں نے اپنے کوڈ سے زندگی کا مشورہ مانگا۔ اس نے کہا: 'کیا آپ نے خود کو بند کر کے دوبارہ آن کرنے کی کوشش کی؟'",
        hi = "मैंने अपने कोड से ज़िंदगी की सलाह माँगी। उसने कहा: 'क्या आपने ख़ुद को बंद करके दोबारा चालू करने की कोशिश की?'",
    ),
    L3(
        en = "Why don't programmers trust atoms? Because they make up everything.",
        ur = "پروگرامر ایٹم پر بھروسہ کیوں نہیں کرتے؟ کیونکہ وہ ہر چیز بنا لیتے ہیں۔",
        hi = "प्रोग्रामर एटम पर भरोसा क्यों नहीं करते? क्योंकि वे हर चीज़ बना लेते हैं।",
    ),
    L3(
        en = "A client asked for a 'simple' website. Three months later it has a shopping cart, a forum, and feelings.",
        ur = "کلائنٹ نے 'سادہ' ویب سائٹ مانگی تھی۔ تین ماہ بعد اس میں شاپنگ کارٹ، فورم، اور جذبات بھی ہیں۔",
        hi = "क्लाइंट ने 'साधारण' वेबसाइट माँगी थी। तीन महीने बाद उसमें शॉपिंग कार्ट, फ़ोरम, और जज़्बात भी हैं।",
    ),
    L3(
        en = "My laptop and I have a wonderful relationship. I press keys; it judges me silently.",
        ur = "میرے لیپ ٹاپ اور میرے درمیان بہترین رشتہ ہے۔ میں بٹن دباتا ہوں؛ وہ خاموشی سے میرا فیصلہ کرتا ہے۔",
        hi = "मेरे लैपटॉप और मेरे बीच बेहतरीन रिश्ता है। मैं बटन दबाता हूँ; वह ख़ामोशी से मेरा फ़ैसला करता है।",
    ),
    L3(
        en = "Why was the developer calm during the outage? He'd already accepted that some things are beyond his control.",
        ur = "آؤٹیج کے دوران ڈویلپر پُرسکون کیوں تھا؟ وہ پہلے ہی مان چکا تھا کہ کچھ چیزیں اس کے بس سے باہر ہیں۔",
        hi = "आउटेज के दौरान डेवलपर शांत क्यों था? वह पहले ही मान चुका था कि कुछ चीज़ें उसके बस से बाहर हैं।",
    ),
    L3(
        en = "What does a programmer wear to a wedding? Formal syntax.",
        ur = "پروگرامر شادی میں کیا پہنتا ہے؟ فارمل سِنٹیکس۔",
        hi = "प्रोग्रामर शादी में क्या पहनता है? फ़ॉर्मल सिंटैक्स।",
    ),
    L3(
        en = "The first rule of debugging: it's always DNS. The second rule: it's never DNS — but check anyway.",
        ur = "ڈی بگنگ کا پہلا اصول: قصور ہمیشہ DNS کا ہوتا ہے۔ دوسرا اصول: کبھی DNS کا نہیں ہوتا — لیکن پھر بھی چیک کر لو۔",
        hi = "डिबगिंग का पहला नियम: क़सूर हमेशा DNS का होता है। दूसरा नियम: कभी DNS का नहीं होता — लेकिन फिर भी जाँच लो।",
    ),
    L3(
        en = "I told the Wi-Fi a secret. It said the connection was unstable — just like my secrets.",
        ur = "میں نے وائی فائی کو ایک راز بتایا۔ اس نے کہا کنکشن غیر مستحکم ہے — بالکل میرے رازوں کی طرح۔",
        hi = "मैंने वाई-फ़ाई को एक राज़ बताया। उसने कहा कनेक्शन अस्थिर है — बिल्कुल मेरे राज़ों की तरह।",
    ),
    L3(
        en = "A designer walks into a bar with a laptop and says: 'Can you make the logo pop?'",
        ur = "ایک ڈیزائنر لیپ ٹاپ لے کر بار میں آیا اور بولا: 'کیا لوگو کو تھوڑا نمایاں کر سکتے ہو؟'",
        hi = "एक डिज़ाइनर लैपटॉप लेकर बार में आया और बोला: 'क्या लोगो को थोड़ा नुमायाँ कर सकते हो?'",
    ),
    L3(
        en = "Why did the database administrator leave his wife? She had too many one-to-many relationships.",
        ur = "ڈیٹابیس ایڈمنسٹریٹر نے اپنی بیوی کو کیوں چھوڑا؟ اس کے بہت سے ون ٹو مینی تعلقات تھے۔",
        hi = "डेटाबेस एडमिनिस्ट्रेटर ने अपनी पत्नी को क्यों छोड़ा? उसके बहुत से वन-टू-मेनी रिश्ते थे।",
    ),
    L3(
        en = "Programming is 10% writing code and 90% figuring out why it doesn't work.",
        ur = "پروگرامنگ 10 فیصد کوڈ لکھنا ہے اور 90 فیصد یہ سمجھنا کہ یہ چل کیوں نہیں رہا۔",
        hi = "प्रोग्रामिंग 10 प्रतिशत कोड लिखना है और 90 प्रतिशत यह समझना कि यह चल क्यों नहीं रहा।",
    ),
    L3(
        en = "My favourite exercise is a cross between a lunge and a crunch. I call it lunch.",
        ur = "میری پسندیدہ ورزش لنج اور کرنچ کا امتزاج ہے۔ میں اسے لنچ کہتا ہوں۔",
        hi = "मेरी पसंदीदा कसरत लंज और क्रंच का मेल है। मैं इसे लंच कहता हूँ।",
    ),
    L3(
        en = "A QA engineer walks into a bar. Orders 1 beer, orders 0 beers, orders -1 beers, orders a lizard.",
        ur = "ایک کیو اے انجینئر بار میں آیا: ایک بیئر منگوائی، صفر بیئر منگوائی، منفی ایک بیئر منگوائی، اور ایک چھپکلی منگوائی۔",
        hi = "एक QA इंजीनियर बार में आया: एक बियर मँगवाई, शून्य बियर मँगवाई, माइनस-एक बियर मँगवाई, और एक छिपकली मँगवाई।",
    ),
    L3(
        en = "Why do programmers hate nature? Too many bugs and no debugger in sight.",
        ur = "پروگرامر فطرت سے نفرت کیوں کرتے ہیں؟ بہت سے بگ ہیں اور ڈی بگر کہیں نظر نہیں آتا۔",
        hi = "प्रोग्रामर प्रकृति से नफ़रत क्यों करते हैं? बहुत से बग हैं और डिबगर कहीं नज़र नहीं आता।",
    ),
    L3(
        en = "I asked a freelancer for a discount. He said: 'Sure — I'll remove the same percentage of features.'",
        ur = "میں نے فری لانسر سے رعایت مانگی۔ اس نے کہا: 'ضرور — میں اتنے ہی فیصد فیچر کم کر دوں گا۔'",
        hi = "मैंने फ्रीलांसर से छूट माँगी। उसने कहा: 'ज़रूर — मैं उतने ही प्रतिशत फ़ीचर कम कर दूँगा।'",
    ),
    L3(
        en = "Semicolons: the tiny soldiers standing guard at the end of every JavaScript line. Respect them.",
        ur = "سیمی کولن: وہ ننھے سپاہی جو ہر جاوا اسکرپٹ لائن کے آخر میں پہرہ دیتے ہیں۔ ان کی عزت کریں۔",
        hi = "सेमीकोलन: वे नन्हे सिपाही जो हर जावास्क्रिप्ट लाइन के आख़िर में पहरा देते हैं। उनका सम्मान करें।",
    ),
)

// ------------------------------------------------------------------
// Fun facts — 44: tech, space, human body, history
// ------------------------------------------------------------------

val COMPANION_FACTS: List<L3> = listOf(
    L3(
        en = "Honey never spoils, sir. Archaeologists found 3,000-year-old honey in Egyptian tombs — still edible.",
        ur = "شہد کبھی خراب نہیں ہوتا، سر۔ ماہرینِ آثار کو مصری قبروں میں 3000 سال پرانا شہد ملا — اب بھی کھانے کے قابل۔",
        hi = "शहद कभी ख़राब नहीं होता, सर। पुरातत्वविदों को मिस्र की क़ब्रों में 3000 साल पुराना शहद मिला — अब भी खाने लायक।",
    ),
    L3(
        en = "The first computer programmer was Ada Lovelace — in the 1840s, before computers even existed.",
        ur = "پہلی کمپیوٹر پروگرامر ایڈا لولیس تھیں — 1840 کی دہائی میں، کمپیوٹر کے وجود سے پہلے۔",
        hi = "पहली कंप्यूटर प्रोग्रामर एडा लवलेस थीं — 1840 के दशक में, कंप्यूटर के अस्तित्व से पहले।",
    ),
    L3(
        en = "A day on Venus is longer than its entire year. Imagine waiting for the weekend there!",
        ur = "زہرہ پر ایک دن اس کے پورے سال سے لمبا ہوتا ہے۔ وہاں ویک اینڈ کا انتظار کیسا ہوگا!",
        hi = "शुक्र पर एक दिन उसके पूरे साल से लंबा होता है। वहाँ वीकेंड का इंतज़ार कैसा होगा!",
    ),
    L3(
        en = "Your brain runs on about 20 watts — roughly the same as a dim light bulb.",
        ur = "آپ کا دماغ تقریباً 20 واٹ پر چلتا ہے — یعنی ایک مدھم بلب جتنی بجلی۔",
        hi = "आपका दिमाग़ लगभग 20 वॉट पर चलता है — यानी एक धीमे बल्ब जितनी बिजली।",
    ),
    L3(
        en = "The first domain name ever registered was symbolics.com, back in 1985.",
        ur = "سب سے پہلا ڈومین نام symbolics.com تھا، جو 1985 میں رجسٹر ہوا۔",
        hi = "सबसे पहला डोमेन नाम symbolics.com था, जो 1985 में रजिस्टर हुआ।",
    ),
    L3(
        en = "Octopuses have three hearts — and their blood is blue.",
        ur = "آکٹوپس کے تین دل ہوتے ہیں — اور اس کا خون نیلا ہوتا ہے۔",
        hi = "ऑक्टोपस के तीन दिल होते हैं — और उसका ख़ून नीला होता है।",
    ),
    L3(
        en = "The Eiffel Tower grows about 15 centimetres taller in summer, thanks to heat expansion.",
        ur = "ایفل ٹاور گرمیوں میں تقریباً 15 سینٹی میٹر لمبا ہو جاتا ہے، حرارت کی توسیع کی وجہ سے۔",
        hi = "एफिल टावर गर्मियों में लगभग 15 सेंटीमीटर लंबा हो जाता है, गर्मी के फैलाव की वजह से।",
    ),
    L3(
        en = "'Wi-Fi' doesn't actually stand for anything — it was just a catchy made-up brand name.",
        ur = "'وائی فائی' کا دراصل کوئی مطلب نہیں — یہ بس ایک دلکش بنایا ہوا برانڈ نام تھا۔",
        hi = "'वाई-फ़ाई' का दरअसल कोई मतलब नहीं — यह बस एक आकर्षक बनाया हुआ ब्रांड नाम था।",
    ),
    L3(
        en = "Bananas are berries, but strawberries aren't. Botany loves a plot twist.",
        ur = "کیلے دراصل بیری ہیں، لیکن اسٹرابیری نہیں۔ نباتات کو ڈرامائی موڑ بہت پسند ہے۔",
        hi = "केले दरअसल बेरी हैं, लेकिन स्ट्रॉबेरी नहीं। वनस्पति विज्ञान को नाटकीय मोड़ बहुत पसंद है।",
    ),
    L3(
        en = "The world's first webcam was built to watch a coffee pot at Cambridge University.",
        ur = "دنیا کا پہلا ویب کیم کیمبرج یونیورسٹی میں کافی کے برتن پر نظر رکھنے کے لیے بنایا گیا تھا۔",
        hi = "दुनिया का पहला वेबकैम कैम्ब्रिज यूनिवर्सिटी में कॉफ़ी के बर्तन पर नज़र रखने के लिए बनाया गया था।",
    ),
    L3(
        en = "Your nose can remember around 50,000 different scents.",
        ur = "آپ کی ناک تقریباً 50,000 مختلف خوشبوؤں کو یاد رکھ سکتی ہے۔",
        hi = "आपकी नाक लगभग 50,000 अलग-अलग खुशबुओं को याद रख सकती है।",
    ),
    L3(
        en = "There are more possible chess games than atoms in the observable universe.",
        ur = "شطرنج کے ممکنہ کھیل قابلِ مشاہدہ کائنات کے ایٹموں سے بھی زیادہ ہیں۔",
        hi = "शतरंज के संभावित खेल देखने योग्य ब्रह्मांड के परमाणुओं से भी ज़्यादा हैं।",
    ),
    L3(
        en = "The first computer bug was a real moth, found inside a Harvard computer in 1947.",
        ur = "پہلا کمپیوٹر بگ ایک اصلی پتنگا تھا، جو 1947 میں ہارورڈ کے کمپیوٹر میں ملا۔",
        hi = "पहला कंप्यूटर बग एक असली पतंगा था, जो 1947 में हार्वर्ड के कंप्यूटर में मिला।",
    ),
    L3(
        en = "Sunlight takes about 8 minutes to reach Earth — so you're always seeing the Sun's past.",
        ur = "سورج کی روشنی کو زمین تک پہنچنے میں تقریباً 8 منٹ لگتے ہیں — یعنی آپ ہمیشہ سورج کا ماضی دیکھتے ہیں۔",
        hi = "सूरज की रोशनी को धरती तक पहुँचने में लगभग 8 मिनट लगते हैं — यानी आप हमेशा सूरज का अतीत देखते हैं।",
    ),
    L3(
        en = "Your stomach gets a brand-new lining every 3 to 4 days, so it doesn't digest itself.",
        ur = "آپ کے معدے کی اندرونی تہہ ہر 3 سے 4 دن میں نئی بن جاتی ہے، تاکہ وہ خود کو ہضم نہ کر لے۔",
        hi = "आपके पेट की अंदरूनी परत हर 3 से 4 दिन में नई बन जाती है, ताकि वह ख़ुद को पचा न ले।",
    ),
    L3(
        en = "Nintendo was founded in 1889 — it started by making hand-painted playing cards.",
        ur = "ننٹینڈو 1889 میں قائم ہوا تھا — اس نے ہاتھ سے بنی تاش کے پتوں سے آغاز کیا۔",
        hi = "निंटेंडो 1889 में स्थापित हुआ था — इसने हाथ से बने ताश के पत्तों से शुरुआत की।",
    ),
    L3(
        en = "Hot water can freeze faster than cold water. Scientists call it the Mpemba effect.",
        ur = "گرم پانی ٹھنڈے پانی سے جلدی جم سکتا ہے۔ سائنس دان اسے مپیمبا ایفیکٹ کہتے ہیں۔",
        hi = "गरम पानी ठंडे पानी से जल्दी जम सकता है। वैज्ञानिक इसे म्पेम्बा प्रभाव कहते हैं।",
    ),
    L3(
        en = "The first text message ever sent, in 1992, simply said 'Merry Christmas'.",
        ur = "1992 میں بھیجا گیا پہلا ٹیکسٹ پیغام بس اتنا تھا: 'میری کرسمس'۔",
        hi = "1992 में भेजा गया पहला टेक्स्ट संदेश बस इतना था: 'मेरी क्रिसमस'।",
    ),
    L3(
        en = "Sharks have existed on Earth longer than trees have.",
        ur = "شارک مچھلیاں درختوں سے بھی پہلے سے زمین پر موجود ہیں۔",
        hi = "शार्क मछलियाँ पेड़ों से भी पहले से धरती पर मौजूद हैं।",
    ),
    L3(
        en = "You can't hum while holding your nose closed. Go on, try it.",
        ur = "ناک بند کر کے آپ گنگنا نہیں سکتے۔ چلیں، آزما کر دیکھیں۔",
        hi = "नाक बंद करके आप गुनगुना नहीं सकते। चलिए, आज़मा कर देखिए।",
    ),
    L3(
        en = "The Apollo 11 guidance computer had less processing power than a modern pocket calculator.",
        ur = "اپالو 11 کے گائیڈنس کمپیوٹر میں آج کے جیبی کیلکولیٹر سے بھی کم طاقت تھی۔",
        hi = "अपोलो 11 के गाइडेंस कंप्यूटर में आज के जेबी कैलकुलेटर से भी कम ताक़त थी।",
    ),
    L3(
        en = "The Anglo-Zanzibar War of 1896 lasted just 38 minutes — the shortest war in history.",
        ur = "1896 کی اینگلو زنزیبار جنگ صرف 38 منٹ چلی — تاریخ کی مختصر ترین جنگ۔",
        hi = "1896 का एंग्लो-ज़ांज़ीबार युद्ध सिर्फ़ 38 मिनट चला — इतिहास का सबसे छोटा युद्ध।",
    ),
    L3(
        en = "Your fingerprints form before you're born — and never change for the rest of your life.",
        ur = "آپ کے انگلیوں کے نشان پیدائش سے پہلے بن جاتے ہیں — اور زندگی بھر نہیں بدلتے۔",
        hi = "आपकी उँगलियों के निशान जन्म से पहले बन जाते हैं — और ज़िंदगी भर नहीं बदलते।",
    ),
    L3(
        en = "The first programmers of the ENIAC, the early giant computer, were six women.",
        ur = "اینیاک کے پہلے پروگرامر، جو ایک دیوہیکل ابتدائی کمپیوٹر تھا، چھ خواتین تھیں۔",
        hi = "एनियाक के पहले प्रोग्रामर, जो एक विशाल शुरुआती कंप्यूटर था, छह महिलाएँ थीं।",
    ),
    L3(
        en = "Saturn is less dense than water — in a big enough ocean, it would float.",
        ur = "زحل پانی سے کم گھنا ہے — اتنے بڑے سمندر میں وہ تیرتا رہے گا۔",
        hi = "शनि पानी से कम घना है — इतने बड़े समंदर में वह तैरता रहेगा।",
    ),
    L3(
        en = "The human eye can distinguish roughly 10 million different colours.",
        ur = "انسانی آنکھ تقریباً ایک کروڑ مختلف رنگ پہچان سکتی ہے۔",
        hi = "मानव आँख लगभग एक करोड़ अलग-अलग रंग पहचान सकती है।",
    ),
    L3(
        en = "Oxford University is older than the Aztec Empire.",
        ur = "آکسفورڈ یونیورسٹی ایزٹیک سلطنت سے بھی پرانی ہے۔",
        hi = "ऑक्सफ़ोर्ड यूनिवर्सिटी एज़्टेक साम्राज्य से भी पुरानी है।",
    ),
    L3(
        en = "A single Google search uses more computing power than the entire Apollo space programme had.",
        ur = "ایک گوگل سرچ میں اپالو خلائی پروگرام کے پورے کمپیوٹنگ سے زیادہ طاقت استعمال ہوتی ہے۔",
        hi = "एक गूगल सर्च में अपोलो अंतरिक्ष कार्यक्रम की पूरी कंप्यूटिंग से ज़्यादा ताक़त लगती है।",
    ),
    L3(
        en = "The first 1GB hard drive, built in 1980, weighed over 200 kg and cost $40,000.",
        ur = "1980 میں بنی پہلی 1GB ہارڈ ڈرائیو کا وزن 200 کلو سے زیادہ تھا اور قیمت 40,000 ڈالر تھی۔",
        hi = "1980 में बनी पहली 1GB हार्ड ड्राइव का वज़न 200 किलो से ज़्यादा था और क़ीमत 40,000 डॉलर थी।",
    ),
    L3(
        en = "The word 'robot' comes from the Czech word 'robota', meaning forced labour.",
        ur = "لفظ 'روبوٹ' چیک زبان کے لفظ 'روبوتا' سے آیا ہے، جس کا مطلب جبری مشقت ہے۔",
        hi = "शब्द 'रोबोट' चेक भाषा के शब्द 'रोबोता' से आया है, जिसका मतलब बेगार है।",
    ),
    L3(
        en = "Dolphins have names for each other — unique signature whistles they answer to.",
        ur = "ڈولفن ایک دوسرے کے نام رکھتی ہیں — منفرد سیٹیوں کی آوازیں جن کا وہ جواب دیتی ہیں۔",
        hi = "डॉल्फ़िन एक-दूसरे के नाम रखती हैं — अनूठी सीटी जैसी आवाज़ें जिनका वे जवाब देती हैं।",
    ),
    L3(
        en = "Parts of the Great Wall of China are held together by sticky-rice mortar.",
        ur = "دیوارِ چین کے کچھ حصے چپکنے والے چاول کے مسالے سے جوڑے گئے ہیں۔",
        hi = "चीन की दीवार के कुछ हिस्से चिपचिपे चावल के गारे से जोड़े गए हैं।",
    ),
    L3(
        en = "Your phone has more computing power than NASA had for the entire Moon landing in 1969.",
        ur = "آپ کے فون میں 1969 میں چاند پر اترنے والے پورے ناسا مشن سے زیادہ کمپیوٹنگ طاقت ہے۔",
        hi = "आपके फ़ोन में 1969 में चाँद पर उतरने वाले पूरे नासा मिशन से ज़्यादा कंप्यूटिंग ताक़त है।",
    ),
    L3(
        en = "Cleopatra lived closer in time to the Moon landing than to the building of the pyramids.",
        ur = "کلیوپیٹرا اہراموں کی تعمیر کی نسبت چاند پر اترنے کے زمانے کے زیادہ قریب رہتی تھیں۔",
        hi = "क्लियोपेट्रा पिरामिडों के निर्माण की तुलना में चाँद पर उतरने के ज़माने के ज़्यादा क़रीब रहती थीं।",
    ),
    L3(
        en = "Sound travels about four times faster in water than it does in air.",
        ur = "آواز پانی میں ہوا کی نسبت تقریباً چار گنا تیز سفر کرتی ہے۔",
        hi = "आवाज़ पानी में हवा की तुलना में लगभग चार गुना तेज़ सफ़र करती है।",
    ),
    L3(
        en = "The first email was sent in 1971 — and the sender forgot what it said.",
        ur = "پہلا ای میل 1971 میں بھیجا گیا تھا — اور بھیجنے والا بھول گیا کہ اس میں کیا لکھا تھا۔",
        hi = "पहला ईमेल 1971 में भेजा गया था — और भेजने वाला भूल गया कि उसमें क्या लिखा था।",
    ),
    L3(
        en = "Wombat droppings are cube-shaped — the only known cube poop in nature.",
        ur = "ومبٹ کا فضلہ مکعب نما ہوتا ہے — فطرت میں واحد معلوم مکعب فضلہ۔",
        hi = "वॉम्बैट का मल घनाकार होता है — प्रकृति में इकलौता ज्ञात घनाकार मल।",
    ),
    L3(
        en = "You spend roughly six months of your life waiting for red traffic lights.",
        ur = "آپ اپنی زندگی کے تقریباً چھ ماہ سرخ ٹریفک لائٹس کے انتظار میں گزارتے ہیں۔",
        hi = "आप अपनी ज़िंदगी के लगभग छह महीने लाल ट्रैफ़िक लाइट के इंतज़ार में गुज़ारते हैं।",
    ),
    L3(
        en = "A group of flamingos is called a 'flamboyance'. Honestly, it fits.",
        ur = "فلیمنگو کے گروہ کو 'فلیمبوئنس' کہتے ہیں۔ سچ کہوں تو نام بالکل درست ہے۔",
        hi = "फ्लेमिंगो के झुंड को 'फ्लैम्बॉयन्स' कहते हैं। सच कहूँ तो नाम बिल्कुल सही है।",
    ),
    L3(
        en = "The QWERTY keyboard layout was designed to slow typists down — to stop old typewriters jamming.",
        ur = "کیورٹی کی بورڈ اس لیے بنایا گیا تھا کہ ٹائپسٹ سست ہو جائیں — تاکہ پرانی ٹائپ رائٹر جام نہ ہوں۔",
        hi = "क्वर्टी कीबोर्ड इसलिए बनाया गया था कि टाइपिस्ट धीमे हो जाएँ — ताकि पुराने टाइपराइटर जाम न हों।",
    ),
    L3(
        en = "Your bones are stronger than concrete, ounce for ounce.",
        ur = "آپ کی ہڈیاں وزن کے حساب سے کنکریٹ سے بھی مضبوط ہیں۔",
        hi = "आपकी हड्डियाँ वज़न के हिसाब से कंक्रीट से भी मज़बूत हैं।",
    ),
    L3(
        en = "The human brain has about 86 billion neurons — each one connected to thousands of others.",
        ur = "انسانی دماغ میں تقریباً 86 ارب نیورون ہوتے ہیں — ہر ایک ہزاروں دوسروں سے جڑا ہوا۔",
        hi = "मानव मस्तिष्क में लगभग 86 अरब न्यूरॉन होते हैं — हर एक हज़ारों दूसरों से जुड़ा हुआ।",
    ),
    L3(
        en = "The Apollo programme's computers were famously weak — your smartwatch could run circles around them.",
        ur = "اپالو پروگرام کے کمپیوٹر مشہور طور پر کمزور تھے — آپ کی اسمارٹ واچ ان سے کہیں آگے ہے۔",
        hi = "अपोलो कार्यक्रम के कंप्यूटर मशहूर तौर पर कमज़ोर थे — आपकी स्मार्टवॉच उनसे कहीं आगे है।",
    ),
    L3(
        en = "Glass is neither fully liquid nor fully solid — it's an 'amorphous solid', stuck in between.",
        ur = "شیشہ نہ مکمل مائع ہے نہ مکمل ٹھوس — یہ 'ایمورفس ٹھوس' ہے، درمیان میں پھنسا ہوا۔",
        hi = "शीशा न पूरी तरह तरल है न पूरी तरह ठोस — यह 'अमॉर्फस ठोस' है, बीच में फँसा हुआ।",
    ),
)
