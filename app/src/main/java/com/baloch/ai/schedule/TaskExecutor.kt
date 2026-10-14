package com.baloch.ai.schedule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.baloch.ai.R
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.DeviceAction
import com.baloch.ai.brain.L3
import com.baloch.ai.brain.RuleBasedBrain
import com.baloch.ai.engine.ActionResult
import com.baloch.ai.engine.DeviceActions
import com.baloch.ai.engine.NotesStore
import com.baloch.ai.engine.NotificationReader
import com.baloch.ai.memory.MemoryStore
import com.baloch.ai.settings.LanguageManager
import com.baloch.ai.voice.VoiceOutput
import kotlinx.coroutines.runBlocking

/**
 * The ONE shared execution path for scheduled tasks. [TaskFireReceiver]
 * (exact one-shot alarms) and [TaskWorker] (WorkManager periodic work) both
 * funnel through [fire] — the brain/execute/notify logic exists exactly once.
 *
 * Constructor signatures match the real classes 1:1:
 * RuleBasedBrain(memory, notes, resolveContact, notificationSummary),
 * DeviceActions(context), MemoryStore(context), NotesStore(context),
 * LanguageManager(context), VoiceOutput(context).
 */
object TaskExecutor {

    /**
     * Full fire pipeline: load the task (skip when missing/disabled),
     * execute its command, post the notification + speak it, then advance
     * the trigger (recurring) or delete the task (one-shot).
     */
    fun fire(appContext: Context, taskId: Long) {
        val store = TaskStore(appContext)
        val task = store.get(taskId) ?: return
        if (!task.enabled) return
        val summary = runBlocking { executeScheduledCommand(appContext, task.commandText) }
        announce(appContext, taskId, summary)
        if (task.recurrence == Recurrence.ONCE) {
            store.delete(taskId)
            TaskScheduler.cancel(appContext, taskId) // defensive: clear any residue
        } else {
            task.nextTriggerAfter(System.currentTimeMillis())?.let { next ->
                store.update(task.copy(triggerAtMillis = next))
            }
        }
    }

    /**
     * Runs [commandText] through the real brain and device actions.
     * Returns the user-facing result summary.
     */
    suspend fun executeScheduledCommand(appContext: Context, commandText: String): String {
        return try {
            val lang = LanguageManager(appContext).current()
            val deviceActions = DeviceActions(appContext)
            val memory = MemoryStore(appContext)
            val notes = NotesStore(appContext)
            val brain = RuleBasedBrain(
                memory = memory,
                notes = notes,
                resolveContact = deviceActions::resolveContact,
                notificationSummary = {
                    if (NotificationReader.isEnabled(appContext)) NotificationReader.summary()
                    else null
                },
            )
            val resp = brain.respond(commandText, deviceActions.currentContext(lang))
            val extra = StringBuilder()
            for (action in resp.actions) {
                when {
                    // RISKY: a scheduled task must NEVER auto-dial or auto-text.
                    action.needsConfirmation -> extra.appendLine(CONFIRM_NEEDED.pick(lang))
                    action is DeviceAction.AddNote -> notes.add(action.text)
                    action is DeviceAction.RememberFact -> memory.saveFact(action.fact)
                    // Mode flips are handled by the in-app ViewModel; a
                    // background fire cannot (and must not) change modes.
                    action is DeviceAction.SetMode -> extra.appendLine(MODE_SKIPPED.pick(lang))
                    else -> when (val r = deviceActions.execute(action)) {
                        is ActionResult.Ok -> { /* silent on success */ }
                        is ActionResult.NeedPermission -> extra.appendLine(r.message)
                        is ActionResult.Failed -> extra.appendLine(r.message)
                    }
                }
            }
            val notesText = extra.toString().trim()
            if (notesText.isEmpty()) resp.text else "${resp.text}\n$notesText"
        } catch (e: Exception) {
            "Sorry, sir — your scheduled task \"$commandText\" ran into a problem: ${e.message}"
        }
    }

    /**
     * Posts the "◈ Baloch" notification and speaks the summary aloud.
     * Voice runs on the main thread because TextToSpeech init is async.
     */
    fun announce(appContext: Context, taskId: Long, text: String) {
        val lang = LanguageManager(appContext).current()
        val spoken = text.ifBlank { DONE.pick(lang) }

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Baloch scheduled tasks", NotificationManager.IMPORTANCE_HIGH)
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("◈ Baloch")
            .setContentText(spoken.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(spoken))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(CHANNEL_ID, taskId.toInt(), notification)

        val main = Handler(Looper.getMainLooper())
        main.post {
            try {
                val voice = VoiceOutput(appContext)
                voice.enabled = voiceToggleOn(appContext)
                voice.setLanguage(lang)
                voice.speak(spoken)
                // TTS needs a live instance while it talks; shut down after.
                main.postDelayed({ voice.shutdown() }, 8000)
            } catch (_: Exception) {
                // Voice is best-effort; the notification already landed.
            }
        }
    }

    /**
     * Respects the in-app voice toggle when a persisted value exists;
     * defaults to ON (VoiceOutput's own default, matching the ViewModel).
     */
    private fun voiceToggleOn(appContext: Context): Boolean =
        appContext.getSharedPreferences("baloch_prefs", Context.MODE_PRIVATE)
            .getBoolean("voice_on", true)

    private val CONFIRM_NEEDED = L3(
        en = "That needs your confirmation, sir — open Baloch to approve it.",
        ur = "اس کے لیے آپ کی تصدیق درکار ہے، سر — منظوری کے لیے بلوچ ایپ کھولیں۔",
        hi = "इसके लिए आपकी पुष्टि चाहिए, सर — अनुमति के लिए Baloch ऐप खोलें।",
    )
    private val MODE_SKIPPED = L3(
        en = "Mode changes don't run from scheduled tasks, sir — please switch mode in the app.",
        ur = "شیڈول شدہ کام سے موڈ تبدیل نہیں ہوتا، سر — براہ کرم ایپ میں موڈ بدلیں۔",
        hi = "शेड्यूल किए गए काम से मोड नहीं बदलता, सर — कृपया ऐप में मोड बदलें।",
    )
    private val DONE = L3(
        en = "Done, sir.",
        ur = "ہو گیا، سر۔",
        hi = "हो गया, सर।",
    )

    private const val CHANNEL_ID = "baloch_scheduled"
}
