package com.baloch.ai.life

import android.content.Context
import com.baloch.ai.brain.BalochLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * Expense tracker — voice-first logging, monthly hisaab.
 *
 * Voice: "500 kharcha chai", "خرچ 800 کرایہ", "1200 खर्च किराया".
 * Digits are accepted in ASCII, Urdu/Persian (۰۱۲۳۴۵۶۷۸۹), Arabic
 * (٠١٢٣٤٥٦٧٨٩) and Devanagari (०१२३४५६७८९) scripts.
 */

// ------------------------------------------------------------------
// Model + store
// ------------------------------------------------------------------

data class Expense(
    val id: Long = 0L,
    val amount: Double,
    val category: String,
    val note: String = "",
    /** Epoch millis. */
    val at: Long = System.currentTimeMillis(),
)

class ExpenseStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun add(expense: Expense): Long {
        val all = all().toMutableList()
        val nextId = (all.maxOfOrNull { it.id } ?: 0L) + 1L
        all.add(expense.copy(id = nextId))
        save(all)
        return nextId
    }

    @Synchronized
    fun all(): List<Expense> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i -> fromJson(arr.getJSONObject(i)) }
                .sortedByDescending { it.at }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun delete(id: Long) {
        save(all().filter { it.id != id })
    }

    private fun save(expenses: List<Expense>) {
        val arr = JSONArray()
        expenses.forEach { arr.put(toJson(it)) }
        val tmp = File(file.parent, "$FILE_NAME.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) file.writeText(arr.toString())
    }

    private fun toJson(e: Expense): JSONObject = JSONObject()
        .put("id", e.id)
        .put("amount", e.amount)
        .put("category", e.category)
        .put("note", e.note)
        .put("at", e.at)

    private fun fromJson(o: JSONObject): Expense = Expense(
        id = o.optLong("id", 0L),
        amount = o.optDouble("amount", 0.0),
        category = o.optString("category", "Other"),
        note = o.optString("note", ""),
        at = o.optLong("at", System.currentTimeMillis()),
    )

    companion object {
        private const val FILE_NAME = "expenses.json"
    }
}

// ------------------------------------------------------------------
// Categories — trilingual keyword map
// ------------------------------------------------------------------

object ExpenseCategories {
    const val FOOD = "Food"
    const val TRANSPORT = "Transport"
    const val BILLS = "Bills"
    const val SHOPPING = "Shopping"
    const val HEALTH = "Health"
    const val WORK = "Work"
    const val OTHER = "Other"

    private val KEYWORDS: Map<String, List<String>> = mapOf(
        FOOD to listOf(
            "chai", "tea", "coffee", "food", "khana", "کھانا", "کھانا", "چائے",
            "lunch", "dinner", "breakfast", "nashta", "ناشتہ", "restaurant",
            "کھانا", "खाना", "चाय", "नाश्ता", "लंच", "डिनर",
        ),
        TRANSPORT to listOf(
            "petrol", "fuel", "rickshaw", "rikshaw", "bus", "taxi", "careem",
            "uber", "bike", "پٹرول", "ایندھن", "کرایہ", "kiraya",
            "पेट्रोल", "रिक्शा", "किराया", "बस",
        ),
        BILLS to listOf(
            "bill", "bijli", "electricity", "gas", "water", "pani", "internet",
            "mobile", "load", "recharge", "بل", "بجلی", "گیس",
            "बिल", "बिजली", "रिचार्ज",
        ),
        SHOPPING to listOf(
            "shopping", "kapre", "clothes", "shoes", "jootay", "grocery",
            "soda", "سودا", "کپڑے", "خریداری", "shopping",
            "खरीदारी", "कपड़े", "जूते", "सौदा",
        ),
        HEALTH to listOf(
            "doctor", "dawai", "medicine", "dawa", "hospital", "clinic",
            "ڈاکٹر", "دوا", "دوائی", "डॉक्टर", "दवा", "दवाई",
        ),
        WORK to listOf(
            "domain", "hosting", "server", "vps", "software", "subscription",
            "freelancer fee", "ڈومین", "ہوسٹنگ", "डोमेन", "होस्टिंग",
        ),
    )

    /** Guesses a category from the free-text note; defaults to Other. */
    fun guess(note: String): String {
        val lower = note.lowercase()
        return KEYWORDS.entries.firstOrNull { (_, words) ->
            words.any { lower.contains(it) }
        }?.key ?: OTHER
    }

    /** Category label in the active language. */
    fun label(category: String, lang: BalochLanguage): String {
        if (lang == BalochLanguage.ENGLISH) return category
        val map = when (lang) {
            BalochLanguage.URDU -> mapOf(
                FOOD to "کھانا", TRANSPORT to "آمدورفت", BILLS to "بل",
                SHOPPING to "خریداری", HEALTH to "صحت", WORK to "کام", OTHER to "دیگر",
            )
            else -> mapOf(
                FOOD to "खाना", TRANSPORT to "यातायात", BILLS to "बिल",
                SHOPPING to "खरीदारी", HEALTH to "स्वास्थ्य", WORK to "काम", OTHER to "अन्य",
            )
        }
        return map[category] ?: category
    }
}

// ------------------------------------------------------------------
// Voice parsing — trilingual, all four digit scripts
// ------------------------------------------------------------------

/** Normalises Urdu/Persian, Arabic-Indic and Devanagari digits to ASCII. */
fun normaliseDigits(s: String): String {
    val sb = StringBuilder(s.length)
    for (ch in s) {
        val v = when (ch) {
            in '۰'..'۹' -> ch - '۰' + '0'.code // Urdu/Persian digits
            in '٠'..'٩' -> ch - '٠' + '0'.code // Arabic-Indic digits
            in '०'..'९' -> ch - '०' + '0'.code // Devanagari digits
            else -> ch.code
        }
        sb.append(v.toChar())
    }
    return sb.toString()
}

data class ParsedExpense(val amount: Double, val note: String, val category: String)

/**
 * Parses voice input like:
 *   "500 kharcha chai" · "kharcha 500 chai" ·
 *   "خرچ ۸۰۰ چائے" · "1200 खर्च किराया"
 *
 * Returns null when no usable amount is found.
 */
fun parseExpenseVoice(rawInput: String): ParsedExpense? {
    val norm = normaliseDigits(rawInput.lowercase())
    val isExpense = listOf("kharcha", "kharach", "expense", "خرچ", "खर्च", "खरच")
        .any { norm.contains(it) }
    if (!isExpense) return null

    val amountMatch = Regex("""(\d+(?:\.\d+)?)""").find(norm) ?: return null
    val amount = amountMatch.value.toDoubleOrNull()?.takeIf { it > 0 } ?: return null

    // Note = everything except the amount and the expense keyword.
    var note = norm.replace(amountMatch.value, " ")
    listOf("kharcha", "kharach", "expense", "خرچ", "खर्च", "खरच", "ka", "ki", "ke")
        .forEach { note = note.replace(it, " ") }
    note = note.replace(Regex("[—–-]"), " ").replace(Regex("\\s+"), " ").trim()

    return ParsedExpense(
        amount = amount,
        note = note,
        category = ExpenseCategories.guess(note),
    )
}

// ------------------------------------------------------------------
// Monthly hisaab
// ------------------------------------------------------------------

data class CategoryTotal(val category: String, val total: Double, val count: Int)

/** Expenses for the given month (1–12) in Asia/Karachi time. */
fun expensesForMonth(expenses: List<Expense>, year: Int, month: Int): List<Expense> {
    val tz = TimeZone.getTimeZone("Asia/Karachi")
    return expenses.filter {
        val c = Calendar.getInstance(tz).apply { timeInMillis = it.at }
        c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) + 1 == month
    }
}

/** Category totals, biggest first. */
fun monthlyHisaab(expenses: List<Expense>): List<CategoryTotal> =
    expenses.groupBy { it.category }
        .map { (cat, list) -> CategoryTotal(cat, list.sumOf { it.amount }, list.size) }
        .sortedByDescending { it.total }

/** Warm monthly summary, trilingual, en-GB spelling. */
fun monthlySummaryText(
    totals: List<CategoryTotal>,
    grandTotal: Double,
    count: Int,
    currency: String,
    lang: BalochLanguage,
): String {
    val fmt = { v: Double ->
        val r = if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v)
        when (currency) {
            LifeSettings.CURRENCY_USD -> "\$$r"
            LifeSettings.CURRENCY_INR -> "₹$r"
            else -> "Rs $r"
        }
    }
    if (totals.isEmpty()) return when (lang) {
        BalochLanguage.ENGLISH ->
            "No expenses logged this month, sir — either you're very disciplined, or you forgot to tell me. 😄"
        BalochLanguage.URDU ->
            "اس مہینے کوئی خرچ درج نہیں، سر — یا تو آپ بہت منظم ہیں، یا مجھے بتانا بھول گئے۔ 😄"
        BalochLanguage.HINDI ->
            "इस महीने कोई खर्च दर्ज नहीं, सर — या तो आप बहुत अनुशासित हैं, या मुझे बताना भूल गए। 😄"
    }
    val lines = totals.take(6).joinToString("\n") { t ->
        "• ${ExpenseCategories.label(t.category, lang)}: ${fmt(t.total)} (${t.count})"
    }
    return when (lang) {
        BalochLanguage.ENGLISH ->
            "This month's hisaab, sir 💰\n$lines\nTotal: ${fmt(grandTotal)} across $count entries."
        BalochLanguage.URDU ->
            "اس مہینے کا حساب، سر 💰\n$lines\nکل: $count اندراجات میں ${fmt(grandTotal)}۔"
        BalochLanguage.HINDI ->
            "इस महीने का हिसाब, सर 💰\n$lines\nकुल: $count प्रविष्टियों में ${fmt(grandTotal)}।"
    }
}

/** Confirmation after logging one expense. */
fun expenseLoggedText(parsed: ParsedExpense, currency: String, lang: BalochLanguage): String {
    val fmt = LifeSettingsAmount.format(parsed.amount, currency)
    val cat = ExpenseCategories.label(parsed.category, lang)
    val noteBit = parsed.note.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
    return when (lang) {
        BalochLanguage.ENGLISH -> "Logged $fmt$noteBit under $cat, sir. ✅"
        BalochLanguage.URDU -> "$fmt$noteBit $cat میں درج کر لیا، سر۔ ✅"
        BalochLanguage.HINDI -> "$fmt$noteBit $cat में दर्ज कर लिया, सर। ✅"
    }
}
