package com.baloch.ai.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim
import kotlinx.coroutines.launch

private data class TourCard(val emoji: String, val title: L3, val body: L3)

private val TOUR_CARDS = listOf(
    TourCard(
        emoji = "🎙️",
        title = L3(
            en = "Voice + wake word",
            ur = "آواز + ویک ورڈ",
            hi = "आवाज़ + वेक वर्ड",
        ),
        body = L3(
            en = "Say “hey baloch” — any time, even with the screen off. Baloch listens, understands Urdu, Hindi and English, and answers in one locked voice, always loud and clear.",
            ur = "”ہے بلوچ“ کہیں — کبھی بھی، اسکرین بند ہو تب بھی۔ بلوچ سنتا ہے، اردو، ہندی اور انگریزی سمجھتا ہے، اور ایک ہی مانوس آواز میں بلند اور واضح جواب دیتا ہے۔",
            hi = "“हे बलोच” कहें — कभी भी, स्क्रीन बंद हो तब भी। बलोच सुनता है, उर्दू, हिंदी और अंग्रेज़ी समझता है, और एक ही जानी-पहचानी आवाज़ में ज़ोरदार और साफ़ जवाब देता है।",
        ),
    ),
    TourCard(
        emoji = "🧠",
        title = L3(
            en = "Memory that never forgets",
            ur = "یادداشت جو کبھی نہیں بھولتی",
            hi = "याददाश्त जो कभी नहीं भूलती",
        ),
        body = L3(
            en = "Facts, people and preferences live encrypted on your phone. Search them, delete any of them — or wipe everything with your PIN.",
            ur = "حقائق، لوگ اور ترجیحات آپ کے فون میں خفیہ محفوظ رہتی ہیں۔ تلاش کریں، کوئی بھی ڈیلیٹ کریں — یا اپنے PIN سے سب کچھ مٹا دیں۔",
            hi = "तथ्य, लोग और प्राथमिकताएँ आपके फ़ोन में एन्क्रिप्टेड रहती हैं। खोजें, कोई भी हटाएँ — या अपने PIN से सब कुछ मिटा दें।",
        ),
    ),
    TourCard(
        emoji = "👆",
        title = L3(
            en = "Gestures",
            ur = "اشارے",
            hi = "इशारे",
        ),
        body = L3(
            en = "Wave to wake, flip to silence, shake for your shortcut — or hold up your palm. Control Baloch without touching the screen, even in the dark.",
            ur = "جگانے کے لیے ہاتھ ہلائیں، خاموش کرنے کے لیے فون الٹائیں، شارٹ کٹ کے لیے ہلائیں — یا ہتھیلی دکھائیں۔ اسکرین چھوئے بغیر بلوچ کو کنٹرول کریں، اندھیرے میں بھی۔",
            hi = "जगाने के लिए हाथ हिलाएँ, चुप कराने के लिए फ़ोन पलटें, शॉर्टकट के लिए हिलाएँ — या हथेली दिखाएँ। स्क्रीन छुए बिना बलोच को नियंत्रित करें, अँधेरे में भी।",
        ),
    ),
    TourCard(
        emoji = "🛡️",
        title = L3(
            en = "Privacy fortress",
            ur = "پرائیویسی کا قلعہ",
            hi = "प्राइवेसी का क़िला",
        ),
        body = L3(
            en = "On-device first: your voice, hands and memories never leave this phone. Every private access is written to the Privacy log — check it any time.",
            ur = "ڈیوائس پہلے: آپ کی آواز، ہاتھ اور یادیں کبھی اس فون سے باہر نہیں جاتیں۔ ہر نجی رسائی پرائیویسی لاگ میں درج ہوتی ہے — کبھی بھی دیکھیں۔",
            hi = "डिवाइस पहले: आपकी आवाज़, हाथ और यादें कभी इस फ़ोन से बाहर नहीं जातीं। हर निजी एक्सेस प्राइवेसी लॉग में दर्ज होता है — कभी भी देखें।",
        ),
    ),
)

private val STR_SKIP = L3(en = "Skip", ur = "چھوڑیں", hi = "छोड़ें")
private val STR_NEXT = L3(en = "Next", ur = "آگے", hi = "आगे")
private val STR_START = L3(en = "Get started", ur = "شروع کریں", hi = "शुरू करें")

/**
 * First-run feature tour — 4 swipeable holographic showcase cards.
 *
 * Shown ONCE: the parent gates this on a prefs flag (e.g.
 * "feature_tour_seen") and calls [onFinish] for both Skip and Get started.
 * Pure UI — no prefs writes here, so the parent stays the single writer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeatureTour(
    lang: BalochLanguage,
    onFinish: () -> Unit,
) {
    val mono = FontFamily.Monospace
    val pagerState = rememberPagerState(pageCount = { TOUR_CARDS.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == TOUR_CARDS.size - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BalochBlack)
            .padding(24.dp),
    ) {
        // Top bar: brand + skip (48dp touch target).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "◈ BALOCH",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = BalochCyan,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            TextButton(onClick = onFinish) {
                Text(
                    text = STR_SKIP.pick(lang),
                    fontFamily = mono,
                    fontSize = 14.sp,
                    color = BalochTextDim,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Swipeable cards.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val card = TOUR_CARDS[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BalochPanel)
                    .border(1.dp, BalochCyan.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = card.emoji, fontSize = 64.sp)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = card.title.pick(lang),
                    fontFamily = mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = BalochCyan,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = card.body.pick(lang),
                    fontFamily = mono,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = BalochText,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Page dots (indicators, not buttons).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(TOUR_CARDS.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .size(if (active) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) BalochCyan
                            else BalochPanelBorder
                        ),
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // Next / Get started.
        Button(
            onClick = {
                if (lastPage) {
                    onFinish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = BalochCyan,
                contentColor = BalochBlack,
            ),
        ) {
            Text(
                text = if (lastPage) STR_START.pick(lang) else STR_NEXT.pick(lang),
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${pagerState.currentPage + 1} / ${TOUR_CARDS.size}",
            fontFamily = mono,
            fontSize = 11.sp,
            color = BalochTextDim,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
