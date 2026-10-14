package com.baloch.ai.engine.pro

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Currency conversion over the free, keyless exchangerate API
 * (https://open.er-api.com/v6/latest/{FROM}).
 *
 * The last good rates are cached per base currency in app-private
 * storage. When the network is unreachable we fall back to the cache
 * and flag the result as [ConvertResult.dated] so the brain can say
 * "rates may be dated, sir" honestly.
 */
object CurrencyConverter {

    /** Everyday words → ISO 4217 codes (checked case-insensitively). */
    private val WORDS: Map<String, String> = mapOf(
        "dollar" to "USD", "dollars" to "USD", "usd" to "USD",
        "buck" to "USD", "bucks" to "USD",
        "pkr" to "PKR", "rupee" to "PKR", "rupees" to "PKR", "rs" to "PKR",
        "روپے" to "PKR", "روپیہ" to "PKR",
        "euro" to "EUR", "euros" to "EUR", "eur" to "EUR",
        "pound" to "GBP", "pounds" to "GBP", "gbp" to "GBP",
        "inr" to "INR", "indian rupee" to "INR", "रुपए" to "INR",
        "aed" to "AED", "dirham" to "AED", "dirhams" to "AED",
        "sar" to "SAR", "riyal" to "SAR", "riyals" to "SAR", "saudi riyal" to "SAR",
        "cad" to "CAD", "canadian dollar" to "CAD",
        "aud" to "AUD", "australian dollar" to "AUD",
        "cny" to "CNY", "yuan" to "CNY",
        "jpy" to "JPY", "yen" to "JPY",
    )

    fun codeFor(word: String): String? = WORDS[word.trim().lowercase()]

    data class ConvertResult(
        val amount: Double,
        val from: String,
        val converted: Double,
        val to: String,
        /** True when this came from the offline cache. */
        val dated: Boolean,
    )

    /**
     * @param context nullable — without it there is no cache, only live rates.
     * @return null when neither live nor cached rates are available.
     */
    suspend fun convert(
        context: Context?,
        amount: Double,
        from: String,
        to: String,
    ): ConvertResult? = withContext(Dispatchers.IO) {
        if (from == to) return@withContext ConvertResult(amount, from, amount, to, dated = false)
        val live = try {
            fetchLive(from, to)
        } catch (_: Exception) {
            null
        }
        if (live != null) {
            saveCache(context, from, live.second)
            return@withContext ConvertResult(amount, from, amount * live.first, to, dated = false)
        }
        val cached = readCache(context, from, to)
        if (cached != null) {
            return@withContext ConvertResult(amount, from, amount * cached, to, dated = true)
        }
        null
    }

    /** @return (rate, full rates JSON string) */
    private fun fetchLive(from: String, to: String): Pair<Double, String>? {
        val url = URL("https://open.er-api.com/v6/latest/$from")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "BalochAI/1.0")
        }
        return try {
            conn.connect()
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val rates = JSONObject(body).optJSONObject("rates") ?: return null
            if (!rates.has(to)) return null
            rates.getDouble(to) to body
        } finally {
            conn.disconnect()
        }
    }

    private fun cacheFile(context: Context?): File? =
        context?.let { File(it.filesDir, "baloch_fx_cache.json") }

    private fun saveCache(context: Context?, base: String, body: String) {
        val file = cacheFile(context) ?: return
        try {
            val all = if (file.exists()) JSONObject(file.readText()) else JSONObject()
            all.put(
                base, JSONObject()
                    .put("at", System.currentTimeMillis())
                    .put("body", body)
            )
            file.writeText(all.toString())
        } catch (_: Exception) {
            // Cache is best-effort; live rates already succeeded.
        }
    }

    private fun readCache(context: Context?, base: String, to: String): Double? {
        val file = cacheFile(context) ?: return null
        return try {
            if (!file.exists()) return null
            val entry = JSONObject(file.readText()).optJSONObject(base) ?: return null
            val rates = JSONObject(entry.getString("body")).optJSONObject("rates") ?: return null
            if (!rates.has(to)) return null
            rates.getDouble(to)
        } catch (_: Exception) {
            null
        }
    }
}
