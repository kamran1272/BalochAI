package com.baloch.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.brain.L3
import com.baloch.ai.settings.LanguageManager
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim

/**
 * FORTRESS PRIVACY — the first-launch privacy promise.
 *
 * Shown ONCE on first launch (the parent gates it on a "privacy_accepted"
 * flag and calls [onAccept] when Kamran taps the button). Plain words,
 * three bullets, zero legalese — the promise Kamran can hold Baloch to:
 *
 *  1. Everything stays on this phone (on-device only).
 *  2. You control the memory — see it, search it, delete it.
 *  3. Nothing private leaves silently — every access is logged.
 *
 * Language comes from [LanguageManager] so the promise reads in whichever
 * language Kamran picked (English / Urdu / Hindi).
 */
@Composable
fun PrivacyOnboarding(onAccept: () -> Unit) {
    val context = LocalContext.current
    val lang = remember { LanguageManager(context.applicationContext).current() }

    // Box + verticalScroll + Center: in landscape (or at 1.3x font scale)
    // the content is taller than the viewport — it scrolls instead of
    // clipping. When it fits, it stays optically centered.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BalochBlack)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Text("🛡", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            STR_TITLE.pick(lang),
            color = BalochCyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            STR_PROMISE.pick(lang),
            color = BalochText,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = BalochPanel),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BalochPanelBorder),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Bullet(STR_BULLET_1.pick(lang))
                Bullet(STR_BULLET_2.pick(lang))
                Bullet(STR_BULLET_3.pick(lang))
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = BalochCyan,
                contentColor = BalochBlack,
            ),
        ) {
            Text(
                STR_ACCEPT.pick(lang),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            STR_FOOTER.pick(lang),
            color = BalochTextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("◈ ", color = BalochCyan, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Text(
            text,
            color = BalochText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }
}

private val STR_TITLE = L3(
    en = "FORTRESS PRIVACY",
    ur = "فورٹریس پرائیویسی",
    hi = "फ़ोर्ट्रेस प्राइवेसी",
)

private val STR_PROMISE = L3(
    en = "Your order is simple, sir: your personal data never leaves this phone. This is Baloch's promise — read it once, hold me to it always.",
    ur = "آپ کا حکم واضح ہے، سر: آپ کا ذاتی ڈیٹا کبھی اس فون سے باہر نہیں جائے گا۔ یہ بلوچ کا وعدہ ہے — ایک بار پڑھ لیں، ہمیشہ مجھ سے یہی توقع رکھیں۔",
    hi = "आपका हुक्म साफ़ है, सर: आपका निजी डेटा कभी इस फ़ोन से बाहर नहीं जाएगा। यह बलोच का वादा है — एक बार पढ़ लें, हमेशा मुझसे यही उम्मीद रखें।",
)

private val STR_BULLET_1 = L3(
    en = "Everything Baloch knows lives on this phone only. Nothing is ever uploaded to any server — there is no cloud, no account, no sync.",
    ur = "بلوچ جو کچھ جانتا ہے وہ صرف اسی فون میں رہتا ہے۔ کچھ بھی کبھی کسی سرور پر اپ لوڈ نہیں ہوتا — نہ کلاؤڈ، نہ اکاؤنٹ، نہ سنک۔",
    hi = "बलोच जो कुछ जानता है वो सिर्फ़ इसी फ़ोन में रहता है। कुछ भी कभी किसी सर्वर पर अपलोड नहीं होता — न क्लाउड, न अकाउंट, न सिंक।",
)

private val STR_BULLET_2 = L3(
    en = "You control the memory. See it, search it, delete anything — or wipe everything with your PIN. Your data obeys you.",
    ur = "میموری آپ کے اختیار میں ہے۔ دیکھیں، تلاش کریں، کچھ بھی ڈیلیٹ کریں — یا اپنے PIN سے سب کچھ مٹا دیں۔ آپ کا ڈیٹا آپ کے حکم کا پابند ہے۔",
    hi = "मेमोरी आपके अधिकार में है। देखें, खोजें, कुछ भी डिलीट करें — या अपने PIN से सब कुछ मिटा दें। आपका डेटा आपके हुक्म का पाबंद है।",
)

private val STR_BULLET_3 = L3(
    en = "Nothing private happens silently. Every time Baloch touches a photo, contact or file, it is written to the Privacy log — you can check it anytime.",
    ur = "کوئی نجی کام چپکے سے نہیں ہوتا۔ جب بھی بلوچ کسی تصویر، رابطے یا فائل کو چھوتا ہے، وہ پرائیویسی لاگ میں درج ہوتا ہے — آپ کسی بھی وقت دیکھ سکتے ہیں۔",
    hi = "कोई निजी काम चुपके से नहीं होता। जब भी बलोच किसी तस्वीर, संपर्क या फ़ाइल को छूता है, वह प्राइवेसी लॉग में दर्ज होता है — आप कभी भी देख सकते हैं।",
)

private val STR_ACCEPT = L3(
    en = "Understood — let's begin",
    ur = "سمجھ گیا — شروع کریں",
    hi = "समझ गया — शुरू करें",
)

private val STR_FOOTER = L3(
    en = "The full guarantee lives in PRIVACY.md — ask Baloch anytime and it will explain itself.",
    ur = "مکمل ضمانت PRIVACY.md میں ہے — کبھی بھی بلوچ سے پوچھیں، وہ خود سمجھائے گا۔",
    hi = "पूरी गारंटी PRIVACY.md में है — कभी भी बलोच से पूछें, वह खुद समझाएगा।",
)
