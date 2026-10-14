package com.baloch.ai.life

import android.content.Context
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Instant translate — ML Kit ON-DEVICE translation (EN ↔ UR ↔ HI).
 *
 * DEPENDENCY NOTE: needs `com.google.mlkit:translate:17.0.3` in
 * app/build.gradle — the parent adds it (see LIFE-DOCS-ADD.md). This
 * file compiles against that exact API:
 *   TranslateLanguage.ENGLISH / .URDU / .HINDI,
 *   TranslatorOptions.Builder().setSourceLanguage().setTargetLanguage(),
 *   Translation.getClient(options),
 *   translator.downloadModelIfNeeded(DownloadConditions),
 *   translator.translate(text).
 *
 * Flow: download the model ONCE (Wi-Fi, ~30 MB per pair), then fully
 * offline forever. When the model isn't downloaded yet, Baloch says so
 * warmly instead of failing — see [TranslateResult.ModelMissing].
 *
 * Voice: "translate karo: where is the station", "iska tarjuma karo".
 */

sealed interface TranslateResult {
    data class Done(val translated: String) : TranslateResult
    /** The model isn't on the device yet — offer the download. */
    data class ModelMissing(val pairName: String) : TranslateResult
    data class Failed(val reason: String) : TranslateResult
}

/** Maps a BalochLanguage to its ML Kit language tag. */
fun mlKitTag(lang: BalochLanguage): String = when (lang) {
    BalochLanguage.ENGLISH -> TranslateLanguage.ENGLISH
    BalochLanguage.URDU -> TranslateLanguage.URDU
    BalochLanguage.HINDI -> TranslateLanguage.HINDI
}

class BalochTranslator(private val context: Context) {

    private val clients = mutableMapOf<String, Translator>()
    private val downloading = mutableSetOf<String>()

    private fun keyFor(src: String, dst: String) = "$src->$dst"

    private fun clientFor(src: String, dst: String): Translator =
        clients.getOrPut(keyFor(src, dst)) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(src)
                .setTargetLanguage(dst)
                .build()
            Translation.getClient(options)
        }

    /**
     * True when the model for this pair is already on the device and
     * translation can run fully offline.
     */
    suspend fun isModelReady(srcLang: BalochLanguage, dstLang: BalochLanguage): Boolean {
        if (srcLang == dstLang) return true
        return try {
            val srcOk = awaitTask {
                modelManager().isModelDownloaded(modelFor(mlKitTag(srcLang)))
            }
            val dstOk = awaitTask {
                modelManager().isModelDownloaded(modelFor(mlKitTag(dstLang)))
            }
            srcOk && dstOk
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Downloads the model for this pair (Wi-Fi required, one-time).
     * Returns null on success, or the failure message.
     */
    suspend fun downloadModel(
        srcLang: BalochLanguage,
        dstLang: BalochLanguage,
        requireWifi: Boolean = true,
    ): String? {
        val src = mlKitTag(srcLang)
        val dst = mlKitTag(dstLang)
        val key = keyFor(src, dst)
        if (key in downloading) return "already downloading"
        downloading.add(key)
        return try {
            val conditions = DownloadConditions.Builder()
                .apply { if (requireWifi) this.requireWifi() }
                .build()
            awaitTask { clientFor(src, dst).downloadModelIfNeeded(conditions) }
            null
        } catch (e: Exception) {
            e.message ?: "download failed"
        } finally {
            downloading.remove(key)
        }
    }

    /** Translates [text]; graceful when the model isn't downloaded yet. */
    suspend fun translate(
        text: String,
        srcLang: BalochLanguage,
        dstLang: BalochLanguage,
    ): TranslateResult {
        if (srcLang == dstLang) return TranslateResult.Done(text)
        val src = mlKitTag(srcLang)
        val dst = mlKitTag(dstLang)
        return try {
            if (!isModelReady(srcLang, dstLang)) {
                return TranslateResult.ModelMissing(
                    "${srcLang.displayName} → ${dstLang.displayName}"
                )
            }
            val out: String = awaitTask { clientFor(src, dst).translate(text) }
            TranslateResult.Done(out)
        } catch (e: Exception) {
            TranslateResult.Failed(e.message ?: "translation failed")
        }
    }

    /** Detects the likely source language from script + keywords. */
    fun detectSource(text: String): BalochLanguage {
        val t = text.trim()
        if (t.any { it in '؀'..'ۿ' }) return BalochLanguage.URDU // Arabic/Urdu block
        if (t.any { it in 'ऀ'..'ॿ' }) return BalochLanguage.HINDI // Devanagari block
        return BalochLanguage.ENGLISH
    }

    fun close() {
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
    }

    // ---------------- ML Kit plumbing ----------------

    private fun modelManager() =
        com.google.mlkit.common.model.RemoteModelManager.getInstance()

    private fun modelFor(language: String) =
        com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(language).build()

    private suspend fun <T> awaitTask(
        block: () -> com.google.android.gms.tasks.Task<T>,
    ): T = suspendCancellableCoroutine { cont ->
        try {
            block()
                .addOnSuccessListener { v -> if (!cont.isCompleted) cont.resume(v) }
                .addOnFailureListener { e ->
                    if (!cont.isCompleted) cont.resumeWithException(e)
                }
                .addOnCanceledListener {
                    if (!cont.isCompleted) cont.cancel()
                }
        } catch (e: Exception) {
            if (!cont.isCompleted) cont.resumeWithException(e)
        }
    }
}

// ------------------------------------------------------------------
// Voice parsing — "translate karo: where is the station"
// ------------------------------------------------------------------

data class ParsedTranslation(val text: String)

/** Extracts the text after "translate karo:" / "tarjuma karo" / etc. */
fun parseTranslateVoice(rawInput: String): ParsedTranslation? {
    val lower = rawInput.lowercase()
    val triggers = listOf(
        "translate karo", "tarjuma karo", "tarjuma",
        "ترجمہ کرو", "ترجمہ", "अनुवाद करो", "अनुवाद", "translate",
    )
    val trigger = triggers.firstOrNull { lower.contains(it) } ?: return null
    val after = rawInput.substring(lower.indexOf(trigger) + trigger.length)
        .trimStart(':', ' ', '—', '-', '۔')
        .trim()
    if (after.isBlank()) return null
    return ParsedTranslation(after)
}

// ------------------------------------------------------------------
// Reply text
// ------------------------------------------------------------------

val TRANSLATE_MODEL_MISSING = L3(
    en = "The {pair} translation model isn't downloaded yet, sir — it's a one-time ~30 MB download on Wi-Fi, then it works fully offline. Say 'download translation model' or tap it in Settings → Translator. 📥",
    ur = "{pair} ترجمے کا ماڈل ابھی ڈاؤن لوڈ نہیں ہوا، سر — وائی فائی پر ایک بار ~30 MB ڈاؤن لوڈ، پھر مکمل آف لائن کام کرے گا۔ 'download translation model' کہیں یا سیٹنگز → Translator میں تھپتھپائیں۔ 📥",
    hi = "{pair} अनुवाद मॉडल अभी डाउनलोड नहीं हुआ है, सर — वाई-फ़ाई पर एक बार ~30 MB डाउनलोड, फिर पूरी तरह ऑफ़लाइन काम करेगा। 'download translation model' कहें या सेटिंग्स → Translator में टैप करें। 📥",
)

val TRANSLATE_FAILED = L3(
    en = "Hmm, the translation didn't go through, sir — {reason}. Try again?",
    ur = "ہمم، ترجمہ نہیں ہو سکا، سر — {reason}۔ دوبارہ کوشش کریں؟",
    hi = "हमम, अनुवाद नहीं हो सका, सर — {reason}। दोबारा कोशिश करें?",
)

val TRANSLATE_DOWNLOADING = L3(
    en = "Downloading the {pair} model on Wi-Fi now, sir — I'll tell you the moment it's ready. 📥",
    ur = "{pair} ماڈل وائی فائی پر ڈاؤن لوڈ ہو رہا ہے، سر — تیار ہوتے ہی بتا دوں گا۔ 📥",
    hi = "{pair} मॉडल वाई-फ़ाई पर डाउनलोड हो रहा है, सर — तैयार होते ही बता दूँगा। 📥",
)

val TRANSLATE_DOWNLOADED = L3(
    en = "Done, sir ✅ — the {pair} model is on your phone now. Translation works fully offline from here.",
    ur = "ہو گیا، سر ✅ — {pair} ماڈل اب آپ کے فون پر ہے۔ یہاں سے ترجمہ مکمل آف لائن کام کرے گا۔",
    hi = "हो गया, सर ✅ — {pair} मॉडल अब आपके फ़ोन पर है। यहाँ से अनुवाद पूरी तरह ऑफ़लाइन काम करेगा।",
)
