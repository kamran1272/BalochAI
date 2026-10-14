package com.baloch.ai.life

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.baloch.ai.brain.BalochLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Focus Mode — Pomodoro cycles (25 focus / 5 break by default, tunable
 * in Settings), DND on during focus, history of completed sessions.
 *
 * Voice: "focus mode start karo", "focus start 30".
 *
 * DND needs the notification-policy grant. When it is missing, focus
 * still runs — Baloch just says so honestly with the exact grant path
 * instead of failing silently.
 */

// ------------------------------------------------------------------
// DND helpers
// ------------------------------------------------------------------

/** True when Baloch may toggle Do-Not-Disturb. */
fun hasDndAccess(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return nm.isNotificationPolicyAccessGranted
}

/** Opens the exact system screen that grants DND access. */
fun dndGrantIntent(): Intent =
    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

/** Sets DND on/off; returns false when the grant is missing. */
fun setDnd(context: Context, on: Boolean): Boolean {
    if (!hasDndAccess(context)) return false
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val filter = if (on) NotificationManager.INTERRUPTION_FILTER_NONE
    else NotificationManager.INTERRUPTION_FILTER_ALL
    return try {
        nm.setInterruptionFilter(filter)
        true
    } catch (_: SecurityException) {
        false
    }
}

// ------------------------------------------------------------------
// History
// ------------------------------------------------------------------

data class FocusSession(
    val id: Long = 0L,
    val focusMinutes: Int,
    val completed: Boolean,
    /** Epoch millis when the focus block started. */
    val startedAt: Long = System.currentTimeMillis(),
)

class FocusHistoryStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun record(session: FocusSession) {
        val all = all().toMutableList()
        val nextId = (all.maxOfOrNull { it.id } ?: 0L) + 1L
        all.add(session.copy(id = nextId))
        // Keep the last 200 sessions — history, not a ledger.
        val trimmed = all.sortedByDescending { it.startedAt }.take(200)
        val arr = JSONArray()
        trimmed.forEach { arr.put(toJson(it)) }
        val tmp = File(file.parent, "$FILE_NAME.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) file.writeText(arr.toString())
    }

    @Synchronized
    fun all(): List<FocusSession> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                FocusSession(
                    id = o.optLong("id", 0L),
                    focusMinutes = o.optInt("focusMinutes", 25),
                    completed = o.optBoolean("completed", false),
                    startedAt = o.optLong("startedAt", 0L),
                )
            }.sortedByDescending { it.startedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun completedCount(): Int = all().count { it.completed }

    private fun toJson(s: FocusSession): JSONObject = JSONObject()
        .put("id", s.id)
        .put("focusMinutes", s.focusMinutes)
        .put("completed", s.completed)
        .put("startedAt", s.startedAt)

    companion object {
        private const val FILE_NAME = "focus_history.json"
    }
}

// ------------------------------------------------------------------
// Scheduling — AlarmManager boundaries, WorkManager fallback
// ------------------------------------------------------------------

/**
 * Starts a focus session: DND on now, an alarm for the focus end
 * (DND stays on through the break), then a break-end alarm that lifts
 * DND and logs the completed session.
 */
object FocusManager {

    const val ACTION_FOCUS_END = "com.baloch.ai.life.FOCUS_END"
    const val ACTION_BREAK_END = "com.baloch.ai.life.BREAK_END"
    private const val EXTRA_STARTED_AT = "started_at"
    private const val EXTRA_FOCUS_MIN = "focus_min"

    fun startFocus(context: Context, focusMin: Int, breakMin: Int): FocusStartResult {
        val dndOk = setDnd(context, true)
        val startedAt = System.currentTimeMillis()

        scheduleAlarm(
            context, ACTION_FOCUS_END, startedAt + focusMin * 60_000L,
            focusMin, startedAt, requestCode = 4101,
        )
        scheduleAlarm(
            context, ACTION_BREAK_END, startedAt + (focusMin + breakMin) * 60_000L,
            focusMin, startedAt, requestCode = 4102,
        )
        // Persist the running session so a reboot or kill can't lose it.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_RUNNING_SINCE, startedAt)
            .putInt(KEY_RUNNING_MIN, focusMin)
            .apply()
        return FocusStartResult(focusMin, breakMin, dndOk)
    }

    fun isRunning(context: Context): Boolean {
        val since = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_RUNNING_SINCE, 0L)
        return since > 0L
    }

    fun stopFocus(context: Context) {
        cancelAlarm(context, ACTION_FOCUS_END, 4101)
        cancelAlarm(context, ACTION_BREAK_END, 4102)
        setDnd(context, false)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_RUNNING_SINCE).remove(KEY_RUNNING_MIN).apply()
    }

    private fun scheduleAlarm(
        context: Context,
        action: String,
        triggerAt: Long,
        focusMin: Int,
        startedAt: Long,
        requestCode: Int,
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, FocusAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_FOCUS_MIN, focusMin)
            putExtra(EXTRA_STARTED_AT, startedAt)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Exact alarms need SCHEDULE_EXACT_ALARM on API 31+; degrade
        // gracefully to an inexact alarm when the grant is missing.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            am.canScheduleExactAlarms()
        if (canExact) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                return
            } catch (_: SecurityException) {
                // fall through to inexact
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    private fun cancelAlarm(context: Context, action: String, requestCode: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, FocusAlarmReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
    }

    private const val PREFS = "baloch_life_focus"
    private const val KEY_RUNNING_SINCE = "running_since"
    private const val KEY_RUNNING_MIN = "running_min"
}

data class FocusStartResult(
    val focusMin: Int,
    val breakMin: Int,
    /** False when the DND grant is missing — session still runs. */
    val dndEnabled: Boolean,
)

/**
 * Alarm boundaries: focus end → break notification (DND stays on);
 * break end → DND off, session logged, completion notification.
 * Declared in manifest-snippets-life.txt.
 */
class FocusAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val lang = activeLanguage(context)
        val focusMin = intent.getIntExtra("focus_min", 25)
        val startedAt = intent.getLongExtra("started_at", System.currentTimeMillis())

        when (intent.action) {
            FocusManager.ACTION_FOCUS_END -> {
                LifeNotify.send(
                    context, LifeNotify.CH_FOCUS, "Focus mode",
                    4001,
                    FOCUS_BREAK_TITLE.pick(lang),
                    FOCUS_BREAK_BODY.pick(lang),
                )
            }
            FocusManager.ACTION_BREAK_END -> {
                setDnd(context, false)
                FocusHistoryStore(context.applicationContext)
                    .record(FocusSession(focusMinutes = focusMin, completed = true, startedAt = startedAt))
                context.getSharedPreferences("baloch_life_focus", Context.MODE_PRIVATE)
                    .edit().remove("running_since").remove("running_min").apply()
                LifeNotify.send(
                    context, LifeNotify.CH_FOCUS, "Focus mode",
                    4002,
                    FOCUS_DONE_TITLE.pick(lang),
                    FOCUS_DONE_BODY.format(lang, "min" to focusMin.toString()),
                )
            }
        }
    }
}

/** Safety net: if the break-end alarm is ever missed, this worker closes the session. */
class FocusSweepWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("baloch_life_focus", Context.MODE_PRIVATE)
        val since = prefs.getLong("running_since", 0L)
        val min = prefs.getInt("running_min", 25)
        if (since > 0L && System.currentTimeMillis() - since > (min + 5) * 60_000L + 10 * 60_000L) {
            // Session overran badly — close it out honestly as incomplete.
            FocusManager.stopFocus(applicationContext)
            FocusHistoryStore(applicationContext)
                .record(FocusSession(focusMinutes = min, completed = false, startedAt = since))
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = OneTimeWorkRequestBuilder<FocusSweepWorker>()
                .setInitialDelay(3, TimeUnit.HOURS)
                .setInputData(workDataOf("sweep" to true))
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}

// ------------------------------------------------------------------
// Reply text
// ------------------------------------------------------------------

private val FOCUS_START = com.baloch.ai.brain.L3(
    en = "Focus mode on, sir 🎯 {min} minutes of deep work — DND is {dnd}, phone face-down, let's go.",
    ur = "فوکس موڈ آن، سر 🎯 {min} منٹ گہرا کام — ڈی این ڈی {dnd}، فون الٹا رکھیں، چلیں۔",
    hi = "फोकस मोड चालू, सर 🎯 {min} मिनट गहरा काम — DND {dnd}, फ़ोन उल्टा रखें, चलें।",
)

private val FOCUS_START_NO_DND = com.baloch.ai.brain.L3(
    en = "Focus mode on for {min} minutes, sir 🎯 — but I couldn't silence notifications: grant me DND access in Settings → Apps → Baloch → Do Not Disturb access, and I'll handle it next time. 🤫",
    ur = "فوکس موڈ {min} منٹ کے لیے آن، سر 🎯 — لیکن اطلاعات خاموش نہیں کر سکا: سیٹنگز → ایپس → بلوچ → Do Not Disturb access میں اجازت دیں، اگلی بار میں سنبھال لوں گا۔ 🤫",
    hi = "फोकस मोड {min} मिनट के लिए चालू, सर 🎯 — लेकिन सूचनाएँ चुप नहीं कर सका: सेटिंग्स → ऐप्स → Baloch → Do Not Disturb access में अनुमति दें, अगली बार मैं सँभाल लूँगा। 🤫",
)

fun focusStartText(result: FocusStartResult, lang: BalochLanguage): String =
    if (result.dndEnabled) {
        val dndWord = when (lang) {
            BalochLanguage.ENGLISH -> "on"
            BalochLanguage.URDU -> "آن ہے"
            BalochLanguage.HINDI -> "चालू है"
        }
        FOCUS_START.format(lang, "min" to result.focusMin.toString(), "dnd" to dndWord)
    } else {
        FOCUS_START_NO_DND.format(lang, "min" to result.focusMin.toString())
    }

private val FOCUS_BREAK_TITLE = com.baloch.ai.brain.L3(
    en = "Break time, sir ☕",
    ur = "وقفے کا وقت، سر ☕",
    hi = "ब्रेक का समय, सर ☕",
)

private val FOCUS_BREAK_BODY = com.baloch.ai.brain.L3(
    en = "Deep work done — stretch, water, eyes off the screen. DND stays on till the break ends.",
    ur = "گہرا کام مکمل — ذرا چہل قدمی، پانی، اسکرین سے نظریں ہٹائیں۔ وقفہ ختم ہونے تک ڈی این ڈی آن رہے گا۔",
    hi = "गहरा काम पूरा — थोड़ा टहलें, पानी पिएँ, स्क्रीन से नज़रें हटाएँ। ब्रेक ख़त्म होने तक DND चालू रहेगा।",
)

private val FOCUS_DONE_TITLE = com.baloch.ai.brain.L3(
    en = "Session complete, sir ✅",
    ur = "سیشن مکمل، سر ✅",
    hi = "सत्र पूरा, सर ✅",
)

private val FOCUS_DONE_BODY = com.baloch.ai.brain.L3(
    en = "{min} focused minutes in the bank, sir. DND is off — the world may now reach you. 🎯",
    ur = "سر، {min} منٹ کی توجہ بینک میں جمع۔ ڈی این ڈی آف — اب دنیا آپ تک پہنچ سکتی ہے۔ 🎯",
    hi = "सर, {min} मिनट का फोकस बैंक में जमा। DND बंद — अब दुनिया आप तक पहुँच सकती है। 🎯",
)
