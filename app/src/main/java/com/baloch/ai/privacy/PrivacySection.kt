package com.baloch.ai.privacy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.brain.L3
import com.baloch.ai.settings.LanguageManager
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochRed
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FORTRESS PRIVACY — the transparency dashboard.
 *
 * "What Baloch accessed": every private-data touch (photo, contact, file,
 * voiceprint, export) is listed here with an icon and timestamp, straight
 * from [PrivacyLog]. Kamran sees EXACTLY what his data's been doing — and
 * can wipe the log with one tap (confirm-gated).
 *
 * Standalone: the parent routes this into the Settings screen and passes
 * `events` collected from `PrivacyLog.get(context).events()` and
 * `onClear = { PrivacyLog.get(context).clear() }`.
 */
@Composable
fun PrivacySection(
    events: List<PrivacyEvent>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lang = remember { LanguageManager(context.applicationContext).current() }
    var confirmClear by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BalochBlack)
            .padding(16.dp),
    ) {
        Text(
            STR_HEADER.pick(lang),
            color = BalochCyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
        Text(
            STR_SUBTITLE.pick(lang),
            color = BalochTextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            STR_COUNT.format(lang, "n" to events.size.toString()),
            color = BalochCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        if (events.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BalochPanel),
                border = BorderStroke(1.dp, BalochPanelBorder),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    STR_EMPTY.pick(lang),
                    color = BalochTextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Index keys: two events can share a millisecond timestamp.
                itemsIndexed(events.sortedByDescending { it.timestamp }) { index, e ->
                    PrivacyEventRow(
                        event = e,
                        formattedTime = dateFmt.format(Date(e.timestamp)),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { confirmClear = true },
            enabled = events.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BalochRed),
        ) {
            Text(STR_CLEAR.pick(lang), fontFamily = FontFamily.Monospace)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = {
                Text(STR_CLEAR_TITLE.pick(lang), color = BalochRed, fontFamily = FontFamily.Monospace)
            },
            text = {
                Text(STR_CLEAR_BODY.pick(lang), color = BalochText, fontFamily = FontFamily.Monospace)
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text(STR_CLEAR_YES.pick(lang), color = BalochRed, fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(STR_CLEAR_NO.pick(lang), color = BalochTextDim, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = BalochPanel,
        )
    }
}

@Composable
private fun PrivacyEventRow(event: PrivacyEvent, formattedTime: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BalochPanel),
        border = BorderStroke(1.dp, BalochPanelBorder),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = event.kind.icon(),
                fontSize = 22.sp,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.kind.label(),
                    color = BalochCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Text(
                    event.detail,
                    color = BalochText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
                Text(
                    formattedTime,
                    color = BalochTextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun PrivacyKind.icon(): String = when (this) {
    PrivacyKind.PHOTO -> "📷"
    PrivacyKind.CONTACT -> "👤"
    PrivacyKind.FILE -> "📄"
    PrivacyKind.VOICEPRINT -> "🎙"
    PrivacyKind.EXPORT -> "⬆"
    PrivacyKind.OTHER -> "◈"
}

private fun PrivacyKind.label(): String = when (this) {
    PrivacyKind.PHOTO -> "Photo"
    PrivacyKind.CONTACT -> "Contact"
    PrivacyKind.FILE -> "File"
    PrivacyKind.VOICEPRINT -> "Voiceprint"
    PrivacyKind.EXPORT -> "Export"
    PrivacyKind.OTHER -> "Other"
}

// ----------------------------------------------------------------------
// Trilingual strings (L3). Urdu in Urdu script, Hindi in Devanagari —
// same convention as the rest of the app (BalochLanguage.kt).
// ----------------------------------------------------------------------

private val STR_HEADER = L3(
    en = "🛡 What Baloch accessed",
    ur = "🛡 بلوچ نے کیا دیکھا",
    hi = "🛡 बलोच ने क्या देखा",
)

private val STR_SUBTITLE = L3(
    en = "Every touch of your private data is logged here — on this phone only.",
    ur = "آپ کے ذاتی ڈیٹا کی ہر رسائی یہاں درج ہے — صرف اسی فون پر۔",
    hi = "आपके निजी डेटा का हर उपयोग यहाँ दर्ज है — सिर्फ़ इसी फ़ोन पर।",
)

private val STR_COUNT = L3(
    en = "{n} access events",
    ur = "{n} رسائی کے واقعات",
    hi = "{n} एक्सेस इवेंट",
)

private val STR_EMPTY = L3(
    en = "Nothing touched yet, sir. Your photos, contacts and files are untouched — and if anything ever is, it will appear here.",
    ur = "ابھی تک کچھ نہیں دیکھا، سر۔ آپ کی تصاویر، رابطے اور فائلیں محفوظ ہیں — اور اگر کبھی کچھ دیکھا گیا تو یہ یہاں نظر آئے گا۔",
    hi = "अभी तक कुछ नहीं छुआ, सर। आपकी तस्वीरें, संपर्क और फ़ाइलें सुरक्षित हैं — और अगर कभी कुछ छुआ गया तो वो यहाँ दिखेगा।",
)

private val STR_CLEAR = L3(
    en = "Clear log",
    ur = "لاگ صاف کریں",
    hi = "लॉग साफ़ करें",
)

private val STR_CLEAR_TITLE = L3(
    en = "Clear the privacy log?",
    ur = "پرائیویسی لاگ صاف کریں؟",
    hi = "प्राइवेसी लॉग साफ़ करें?",
)

private val STR_CLEAR_BODY = L3(
    en = "This erases the record of what Baloch accessed. Your actual data stays — only this history is cleared.",
    ur = "اس سے بلوچ کی رسائی کا ریکارڈ مٹ جائے گا۔ آپ کا اصل ڈیٹا محفوظ رہے گا — صرف یہ تاریخ صاف ہوگی۔",
    hi = "इससे बलोच की एक्सेस का रिकॉर्ड मिट जाएगा। आपका असली डेटा सुरक्षित रहेगा — सिर्फ़ यह इतिहास साफ़ होगा।",
)

private val STR_CLEAR_YES = L3(
    en = "Clear it",
    ur = "صاف کریں",
    hi = "साफ़ करें",
)

private val STR_CLEAR_NO = L3(
    en = "Keep it",
    ur = "رہنے دیں",
    hi = "रहने दें",
)
