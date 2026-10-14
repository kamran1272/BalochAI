package com.baloch.ai.power

import android.content.Context
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The ONE execution path for every power-feature toggle.
 *
 * Voice ("driving mode on karo") → [matchPower] → [applyHit].
 * Settings screen → the same [applyHit] (or the feature's own setOn).
 * Two callers, one path — a toggle can never work on screen but not by
 * voice, or vice versa.
 *
 * Returns an [L3] reply the caller speaks/shows. Side effects (worker
 * scheduling, notifications) happen inside.
 */
object PowerToggles {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun applyHit(context: Context, hit: PowerHit): L3 {
        val lang = PowerPrefs.activeLang(context)
        return when (hit.feature) {
            PowerFeature.DRIVING -> when (hit.command) {
                PowerCommand.TOGGLE_ON -> {
                    DrivingMode.setOn(context, true)
                    L3(
                        "Driving mode ON, sir 🚗 — I'll auto-reply to SMS and read messages aloud. " +
                            "It switches off automatically after ${DrivingMode.autoOffMinutes(context)} minutes. " +
                            "(Needs SMS permission for the auto-reply.)",
                        "ڈرائیونگ موڈ آن، سر 🚗 — ایس ایم ایس کا خودکار جواب دوں گا اور پیغامات پڑھ کر سناؤں گا۔ " +
                            "${DrivingMode.autoOffMinutes(context)} منٹ بعد خود بند ہو جائے گا۔ " +
                            "(آٹو ریپلائی کے لیے ایس ایم ایس کی اجازت چاہیے۔)",
                        "ड्राइविंग मोड ऑन, सर 🚗 — एसएमएस का ऑटो-जवाब दूँगा और संदेश पढ़कर सुनाऊँगा। " +
                            "${DrivingMode.autoOffMinutes(context)} मिनट बाद खुद बंद हो जाएगा। " +
                            "(ऑटो-रिप्लाई के लिए एसएमएस अनुमति चाहिए।)",
                    )
                }
                PowerCommand.TOGGLE_OFF -> {
                    DrivingMode.setOn(context, false)
                    L3(
                        "Driving mode OFF, sir — drive safe. 🚗",
                        "ڈرائیونگ موڈ بند، سر — محفوظ ڈرائیونگ۔ 🚗",
                        "ड्राइविंग मोड बंद, सर — सुरक्षित ड्राइविंग। 🚗",
                    )
                }
                else -> L3(
                    "Driving mode is ${onOff(context, PowerPrefs.DRIVING, lang)}, sir.",
                    "ڈرائیونگ موڈ ${onOff(context, PowerPrefs.DRIVING, lang)} ہے، سر۔",
                    "ड्राइविंग मोड ${onOff(context, PowerPrefs.DRIVING, lang)} है, सर।",
                )
            }

            PowerFeature.RAMADAN -> when (hit.command) {
                PowerCommand.TOGGLE_ON -> {
                    RamadanMode.setOn(context, true)
                    L3(
                        "Ramadan mode ON, sir 🌙 — sehri/iftari alerts for ${RamadanMode.city(context)} are armed.",
                        "رمضان موڈ آن، سر 🌙 — ${RamadanMode.city(context)} کے لیے سحری/افطاری الرٹس تیار ہیں۔",
                        "रमज़ान मोड ऑन, सर 🌙 — ${RamadanMode.city(context)} के लिए सहरी/इफ़्तार अलर्ट तैयार हैं।",
                    )
                }
                PowerCommand.TOGGLE_OFF -> {
                    RamadanMode.setOn(context, false)
                    L3(
                        "Ramadan mode OFF, sir.",
                        "رمضان موڈ بند، سر۔",
                        "रमज़ान मोड बंद, सर।",
                    )
                }
                else -> same(RamadanMode.countdownText(context, lang))
            }

            PowerFeature.CRICKET -> when (hit.command) {
                PowerCommand.TOGGLE_ON -> {
                    CricketAlerts.setOn(context, true)
                    L3(
                        "Pakistan cricket alerts ON, sir 🏏 — I'll buzz you on match news.",
                        "پاکستان کرکٹ الرٹس آن، سر 🏏 — میچ کی خبروں پر مطلع کروں گا۔",
                        "पाकिस्तान क्रिकेट अलर्ट ऑन, सर 🏏 — मैच की ख़बरों पर सूचित करूँगा।",
                    )
                }
                PowerCommand.TOGGLE_OFF -> {
                    CricketAlerts.setOn(context, false)
                    L3("Cricket alerts OFF, sir.", "کرکٹ الرٹس بند، سر۔", "क्रिकेट अलर्ट बंद, सर।")
                }
                else -> {
                    val status = CricketAlerts.sourceStatus(context, lang)
                    L3(
                        "Cricket alerts are ${onOff(context, PowerPrefs.CRICKET, lang)}, sir. $status",
                        "کرکٹ الرٹس ${onOff(context, PowerPrefs.CRICKET, lang)} ہیں، سر۔ $status",
                        "क्रिकेट अलर्ट ${onOff(context, PowerPrefs.CRICKET, lang)} हैं, सर। $status",
                    )
                }
            }

            PowerFeature.CURRENCY -> when (hit.command) {
                PowerCommand.TEACH -> {
                    val n = hit.number ?: return needNumber("dollar 290 par alert")
                    CurrencyAlerts.setThreshold(context, n)
                    L3(
                        "Done, sir — I'll alert you when the dollar crosses Rs ${"%.0f".format(n)}. 💵",
                        "ہو گیا، سر — ڈالر ${"%.0f".format(n)} روپے عبور کرے گا تو مطلع کروں گا۔ 💵",
                        "हो गया, सर — डॉलर ${"%.0f".format(n)} रुपये पार करेगा तो सूचित करूँगा। 💵",
                    )
                }
                PowerCommand.TOGGLE_ON -> {
                    CurrencyAlerts.setOn(context, true)
                    L3(
                        "Dollar alert ON, sir 💵 — watching Rs ${"%.0f".format(CurrencyAlerts.threshold(context))}.",
                        "ڈالر الرٹ آن، سر 💵 — ${"%.0f".format(CurrencyAlerts.threshold(context))} روپے پر نظر ہے۔",
                        "डॉलर अलर्ट ऑन, सर 💵 — ${"%.0f".format(CurrencyAlerts.threshold(context))} रुपये पर नज़र है।",
                    )
                }
                PowerCommand.TOGGLE_OFF -> {
                    CurrencyAlerts.setOn(context, false)
                    L3("Dollar alert OFF, sir.", "ڈالر الرٹ بند، سر۔", "डॉलर अलर्ट बंद, सर।")
                }
                else -> same(CurrencyAlerts.rateText(context, lang))
            }

            PowerFeature.PETROL -> when (hit.command) {
                PowerCommand.TEACH -> {
                    val n = hit.number ?: return needNumber("petrol price 278")
                    val result = PetrolAlerts.teachPrice(context, n)
                    if (result.changed) {
                        PowerNotify.notify(
                            context,
                            L3("⛽ Petrol price change", "⛽ پٹرول قیمت تبدیلی", "⛽ पेट्रोल क़ीमत बदलाव").pick(lang),
                            result.text, 6502,
                        )
                        PowerSpeak.speak(context, lang, result.text)
                    }
                    same(result.text)
                }
                PowerCommand.TOGGLE_ON -> {
                    PetrolAlerts.setOn(context, true)
                    L3(
                        "Petrol alerts ON, sir ⛽ — teach me the price: \"petrol price 278\".",
                        "پٹرول الرٹس آن، سر ⛽ — قیمت سکھا دیں: \"petrol price 278\"۔",
                        "पेट्रोल अलर्ट ऑन, सर ⛽ — क़ीमत सिखा दें: \"petrol price 278\"।",
                    )
                }
                PowerCommand.TOGGLE_OFF -> {
                    PetrolAlerts.setOn(context, false)
                    L3("Petrol alerts OFF, sir.", "پٹرول الرٹس بند، سر۔", "पेट्रोल अलर्ट बंद, सर।")
                }
                else -> same(PetrolAlerts.priceText(context, lang))
            }

            PowerFeature.WORD -> when (hit.command) {
                PowerCommand.TOGGLE_ON -> {
                    DailyWord.setOn(context, true)
                    L3(
                        "Daily word ON, sir 📚 — a new word every morning at 7:30.",
                        "روزانہ لفظ آن، سر 📚 — ہر صبح 7:30 ایک نیا لفظ۔",
                        "रोज़ाना लफ़्ज़ ऑन, सर 📚 — हर सुबह 7:30 एक नया लफ़्ज़।",
                    )
                }
                PowerCommand.TOGGLE_OFF -> {
                    DailyWord.setOn(context, false)
                    L3("Daily word OFF, sir.", "روزانہ لفظ بند، سر۔", "रोज़ाना लफ़्ज़ बंद, सर।")
                }
                else -> same(DailyWord.todayText(lang))
            }

            PowerFeature.RECORDER -> when (hit.command) {
                PowerCommand.TOGGLE_ON -> {
                    if (!MeetingRecorder.isOn(context)) {
                        return L3(
                            "Meeting recorder is switched off in Power Features, sir — turn it on first, then say \"meeting record karo\". 🎙️",
                            "میٹنگ ریکارڈر پاور فیچرز میں بند ہے، سر — پہلے آن کریں، پھر کہیں \"meeting record karo\"۔ 🎙️",
                            "मीटिंग रिकॉर्डर पावर फ़ीचर्स में बंद है, सर — पहले ऑन करें, फिर कहें \"meeting record karo\"। 🎙️",
                        )
                    }
                    val file = MeetingRecorder.startRecording(context)
                    if (file == null) {
                        L3(
                            "Couldn't start recording, sir — check the microphone permission and try again. 🎙️",
                            "ریکارڈنگ شروع نہیں ہو سکی، سر — مائیکروفون کی اجازت چیک کر کے دوبارہ کوشش کریں۔ 🎙️",
                            "रिकॉर्डिंग शुरू नहीं हो सकी, सर — माइक्रोफ़ोन अनुमति जाँचकर दोबारा कोशिश करें। 🎙️",
                        )
                    } else {
                        L3(
                            "Recording started, sir 🎙️ — say \"recording band karo\" when done.",
                            "ریکارڈنگ شروع، سر 🎙️ — ختم ہو تو کہیں \"recording band karo\"۔",
                            "रिकॉर्डिंग शुरू, सर 🎙️ — ख़त्म हो तो कहें \"recording band karo\"।",
                        )
                    }
                }
                else -> { // TOGGLE_OFF / QUERY → stop
                    val file = MeetingRecorder.stopRecording()
                    if (file == null) {
                        L3(
                            "No recording was running, sir.",
                            "کوئی ریکارڈنگ جاری نہیں تھی، سر۔",
                            "कोई रिकॉर्डिंग जारी नहीं थी, सर।",
                        )
                    } else {
                        if (MeetingRecorder.transcribeOn(context)) {
                            transcribeAsync(context, file)
                            L3(
                                "Recording saved, sir 🎙️ — transcribing on-device, I'll notify you when it's ready.",
                                "ریکارڈنگ محفوظ، سر 🎙️ — ڈیوائس پر تحریر بن رہی ہے، تیار ہوتے ہی مطلع کروں گا۔",
                                "रिकॉर्डिंग सहेजी, सर 🎙️ — डिवाइस पर लिखावट बन रही है, तैयार होते ही सूचित करूँगा।",
                            )
                        } else {
                            L3(
                                "Recording saved, sir 🎙️ — find it in Power Features → Meeting recorder.",
                                "ریکارڈنگ محفوظ، سر 🎙️ — پاور فیچرز ← میٹنگ ریکارڈر میں ملے گی۔",
                                "रिकॉर्डिंग सहेजी, सर 🎙️ — पावर फ़ीचर्स ← मीटिंग रिकॉर्डर में मिलेगी।",
                            )
                        }
                    }
                }
            }

            PowerFeature.CHARGE -> when (hit.command) {
                PowerCommand.TOGGLE_ON -> {
                    ChargeAlert.setOn(context, true)
                    L3(
                        "Charge alerts ON, sir 🔌 — I'll announce plug-in and 100%.",
                        "چارج الرٹس آن، سر 🔌 — چارجر لگنے اور 100 فیصد پر بتاؤں گا۔",
                        "चार्ज अलर्ट ऑन, सर 🔌 — चार्जर लगने और 100 प्रतिशत पर बताऊँगा।",
                    )
                }
                PowerCommand.TOGGLE_OFF -> {
                    ChargeAlert.setOn(context, false)
                    L3("Charge alerts OFF, sir.", "چارج الرٹس بند، سر۔", "चार्ज अलर्ट बंद, सर।")
                }
                else -> L3(
                    "Charge alerts are ${onOff(context, PowerPrefs.CHARGE, lang)}, sir.",
                    "چارج الرٹس ${onOff(context, PowerPrefs.CHARGE, lang)} ہیں، سر۔",
                    "चार्ज अलर्ट ${onOff(context, PowerPrefs.CHARGE, lang)} हैं, सर।",
                )
            }

            PowerFeature.RAIN -> when (hit.command) {
                PowerCommand.TOGGLE_ON -> {
                    RainAlerts.setOn(context, true)
                    L3(
                        "Rain alerts ON, sir ☔ — I'll check Lahore every morning at 7.",
                        "بارش الرٹس آن، سر ☔ — ہر صبح 7 بجے لاہور کا چیک کروں گا۔",
                        "बारिश अलर्ट ऑन, सर ☔ — हर सुबह 7 बजे लाहौर का चेक करूँगा।",
                    )
                }
                PowerCommand.TOGGLE_OFF -> {
                    RainAlerts.setOn(context, false)
                    L3("Rain alerts OFF, sir.", "بارش الرٹس بند، سر۔", "बारिश अलर्ट बंद, सर।")
                }
                else -> same(RainAlerts.chanceText(context, lang))
            }

            PowerFeature.APPLOCK -> when (hit.command) {
                PowerCommand.TOGGLE_ON -> {
                    AppLock.setOn(context, true)
                    L3(
                        "App lock ON, sir 🔒 — Baloch will ask for your fingerprint or PIN before opening.",
                        "ایپ لاک آن، سر 🔒 — بلوچ کھلنے سے پہلے فنگر پرنٹ یا پن مانگے گا۔",
                        "ऐप लॉक ऑन, सर 🔒 — बलोच खुलने से पहले फ़िंगरप्रिंट या पिन माँगेगा।",
                    )
                }
                else -> {
                    AppLock.setOn(context, false)
                    L3("App lock OFF, sir.", "ایپ لاک بند، سر۔", "ऐप लॉक बंद, सर।")
                }
            }
        }
    }

    private fun onOff(context: Context, key: String, lang: BalochLanguage): String {
        val on = PowerPrefs.isOn(context, key)
        return when (lang) {
            BalochLanguage.ENGLISH -> if (on) "ON" else "OFF"
            BalochLanguage.URDU -> if (on) "آن" else "بند"
            BalochLanguage.HINDI -> if (on) "ऑन" else "बंद"
        }
    }

    /** Wraps text that is already in the picked language. */
    private fun same(text: String): L3 = L3(text, text, text)

    private fun needNumber(example: String): L3 = L3(
        "I need a number, sir — try \"$example\".",
        "مجھے ایک عدد چاہیے، سر — کہیں \"$example\"۔",
        "मुझे एक संख्या चाहिए, सर — कहें \"$example\"।",
    )

    private fun transcribeAsync(context: Context, file: java.io.File) {
        val appCtx = context.applicationContext
        scope.launch {
            val text = MeetingTranscriber.transcribe(appCtx, file)
            val lang = PowerPrefs.activeLang(appCtx)
            if (text != null) {
                val preview = text.take(400) + if (text.length > 400) "…" else ""
                PowerNotify.notify(
                    appCtx,
                    L3("🎙️ Transcript ready", "🎙️ تحریر تیار", "🎙️ लिखावट तैयार").pick(lang),
                    preview, 6901,
                )
            } else {
                PowerNotify.notify(
                    appCtx,
                    L3("🎙️ Transcription unavailable", "🎙️ تحریر ممکن نہیں", "🎙️ लिखावट संभव नहीं").pick(lang),
                    L3(
                        "Couldn't transcribe, sir — the offline speech model isn't installed or the audio was unclear.",
                        "تحریر نہیں بن سکی، سر — آف لائن اسپیچ ماڈل انسٹال نہیں یا آڈیو واضح نہیں۔",
                        "लिखावट नहीं बन सकी, सर — ऑफ़लाइन स्पीच मॉडल इंस्टॉल नहीं या ऑडियो स्पष्ट नहीं।",
                    ).pick(lang),
                    6902,
                )
            }
        }
    }
}
