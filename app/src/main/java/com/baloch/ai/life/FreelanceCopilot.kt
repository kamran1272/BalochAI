package com.baloch.ai.life

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.settings.isJarvisMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Freelance Copilot — Kamran's client tracker.
 *
 * Voice: "add client Ahmed", "mere clients batao", "freelance summary".
 * Follow-up nudges fire daily via [FollowUpNudgeWorker], JARVIS-gated:
 * MANUAL mode stays silent.
 */

enum class ClientStatus { LEAD, ACTIVE, PAUSED, DONE }

data class Client(
    val id: Long = 0L,
    val name: String,
    val project: String = "",
    val amount: Double = 0.0,
    val status: ClientStatus = ClientStatus.LEAD,
    /** Epoch millis of last contact; 0 = never. */
    val lastContact: Long = 0L,
    /** Epoch millis when the next follow-up is due; 0 = none. */
    val followUpDue: Long = 0L,
)

/** Atomic JSON store (temp + rename), same pattern as TaskStore. */
class ClientStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun add(client: Client): Long {
        val all = all().toMutableList()
        val nextId = (all.maxOfOrNull { it.id } ?: 0L) + 1L
        all.add(client.copy(id = nextId))
        save(all)
        return nextId
    }

    @Synchronized
    fun all(): List<Client> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i -> fromJson(arr.getJSONObject(i)) }
                .sortedByDescending { it.lastContact }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun get(id: Long): Client? = all().firstOrNull { it.id == id }

    @Synchronized
    fun update(client: Client) {
        save(all().map { if (it.id == client.id) client else it })
    }

    @Synchronized
    fun touchContact(id: Long) {
        val c = get(id) ?: return
        update(c.copy(lastContact = System.currentTimeMillis()))
    }

    @Synchronized
    fun delete(id: Long) {
        save(all().filter { it.id != id })
    }

    private fun save(clients: List<Client>) {
        val arr = JSONArray()
        clients.forEach { arr.put(toJson(it)) }
        val tmp = File(file.parent, "$FILE_NAME.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) file.writeText(arr.toString())
    }

    private fun toJson(c: Client): JSONObject = JSONObject()
        .put("id", c.id)
        .put("name", c.name)
        .put("project", c.project)
        .put("amount", c.amount)
        .put("status", c.status.name)
        .put("lastContact", c.lastContact)
        .put("followUpDue", c.followUpDue)

    private fun fromJson(o: JSONObject): Client = Client(
        id = o.optLong("id", 0L),
        name = o.optString("name", ""),
        project = o.optString("project", ""),
        amount = o.optDouble("amount", 0.0),
        status = runCatching { ClientStatus.valueOf(o.optString("status", "LEAD")) }
            .getOrDefault(ClientStatus.LEAD),
        lastContact = o.optLong("lastContact", 0L),
        followUpDue = o.optLong("followUpDue", 0L),
    )

    companion object {
        private const val FILE_NAME = "freelance_clients.json"
    }
}

/** Clients whose last contact is older than [staleDays] and not DONE. */
fun staleClients(clients: List<Client>, now: Long, staleDays: Int = 5): List<Client> =
    clients.filter {
        it.status != ClientStatus.DONE &&
            (now - it.lastContact) > staleDays * 24L * 3_600_000L
    }

/** Warm, trilingual earnings + pipeline summary. */
fun clientSummary(clients: List<Client>, lang: BalochLanguage, currency: String): String {
    val active = clients.filter { it.status == ClientStatus.ACTIVE }
    val leads = clients.filter { it.status == ClientStatus.LEAD }
    val earned = active.sumOf { it.amount }
    val fmt = LifeSettingsAmount.format(earned, currency)
    return when (lang) {
        BalochLanguage.ENGLISH ->
            "Here's your freelance board, sir 📊\n" +
                "• Active clients: ${active.size}\n" +
                "• Warm leads: ${leads.size}\n" +
                "• Pipeline value: $fmt\n" +
                if (active.isEmpty() && leads.isEmpty())
                    "No clients yet, sir — say 'add client Ahmed' and I'll start tracking."
                else ""
        BalochLanguage.URDU ->
            "سر، آپ کا فری لانس بورڈ 📊\n" +
                "• فعال کلائنٹس: ${active.size}\n" +
                "• گرم لیڈز: ${leads.size}\n" +
                "• پائپ لائن مالیت: $fmt\n" +
                if (active.isEmpty() && leads.isEmpty())
                    "ابھی کوئی کلائنٹ نہیں، سر — کہیں 'add client Ahmed' اور میں ٹریکنگ شروع کر دوں گا۔"
                else ""
        BalochLanguage.HINDI ->
            "सर, आपका फ्रीलांस बोर्ड 📊\n" +
                "• सक्रिय क्लाइंट: ${active.size}\n" +
                "• गर्म लीड: ${leads.size}\n" +
                "• पाइपलाइन मूल्य: $fmt\n" +
                if (active.isEmpty() && leads.isEmpty())
                    "अभी कोई क्लाइंट नहीं, सर — कहें 'add client Ahmed' और मैं ट्रैकिंग शुरू कर दूँगा।"
                else ""
    }
}

/** Nudge text for one stale client: "Ahmed ko 5 din se follow-up nahi kiya, sir". */
fun followUpNudge(client: Client, lang: BalochLanguage): String {
    val days = ((System.currentTimeMillis() - client.lastContact) / (24L * 3_600_000L))
        .coerceAtLeast(1L)
    return when (lang) {
        BalochLanguage.ENGLISH ->
            "${client.name} ko $days din se follow-up nahi kiya, sir — ek chhota sa 'just checking in' message bhej dein? 📩"
        BalochLanguage.URDU ->
            "${client.name} کو $days دن سے فالو اپ نہیں کیا، سر — ایک چھوٹا سا 'just checking in' پیغام بھیج دیں؟ 📩"
        BalochLanguage.HINDI ->
            "${client.name} को $days दिन से फॉलो-अप नहीं किया, सर — एक छोटा सा 'just checking in' संदेश भेज दें? 📩"
    }
}

/** Daily follow-up nudge worker — JARVIS-gated, respects the copilot toggle. */
class FollowUpNudgeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!isJarvisMode(applicationContext)) return Result.success()
        if (!LifeSettings(applicationContext).copilotNudgesNow()) return Result.success()

        val clients = ClientStore(applicationContext).all()
        val stale = staleClients(clients, System.currentTimeMillis()).take(3)
        if (stale.isEmpty()) return Result.success()

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        if (prefs.getString(KEY_LAST_NUDGE_DAY, "") == today) return Result.success()

        val lang = activeLanguage(applicationContext)
        val text = stale.joinToString("\n") { "• " + followUpNudge(it, lang) }
        val title = when (lang) {
            BalochLanguage.ENGLISH -> "Follow-up reminders, sir 📩"
            BalochLanguage.URDU -> "فالو اپ یاد دہانی، سر 📩"
            BalochLanguage.HINDI -> "फॉलो-अप याद दिलाना, सर 📩"
        }
        LifeNotify.send(
            applicationContext,
            LifeNotify.CH_COPILOT, "Freelance copilot",
            2001, title, text,
        )
        prefs.edit().putString(KEY_LAST_NUDGE_DAY, today).apply()
        return Result.success()
    }

    companion object {
        private const val PREFS = "baloch_life_copilot"
        private const val KEY_LAST_NUDGE_DAY = "last_nudge_day"
        private const val WORK_NAME = "baloch_followup_nudges"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<FollowUpNudgeWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

/** Currency formatting without a Context (summary text building). */
internal object LifeSettingsAmount {
    fun format(amount: Double, currency: String): String {
        val rounded = if (amount % 1.0 == 0.0) amount.toLong().toString()
        else "%.2f".format(amount)
        return when (currency) {
            LifeSettings.CURRENCY_USD -> "\$$rounded"
            LifeSettings.CURRENCY_INR -> "₹$rounded"
            else -> "Rs $rounded"
        }
    }
}
