package com.baloch.ai.agents

import android.content.Context
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.DeviceAction
import com.baloch.ai.brain.L3
import com.baloch.ai.engine.ActionResult
import com.baloch.ai.engine.DeviceActions
import com.baloch.ai.engine.NotesStore
import com.baloch.ai.memory.MemoryStore

/**
 * Shared types for Baloch's multi-agent execution layer.
 *
 * The [com.baloch.ai.agents.Orchestrator] splits one user command into
 * parts, routes each part's [DeviceAction] to a [SpecialistAgent], runs
 * them all in parallel, and synthesises a single reply.
 */

/** One unit of work handed to a specialist agent. */
data class AgentTask(
    val action: DeviceAction,
    val lang: BalochLanguage,
    val partText: String,
)

/** What a specialist agent reports back. `line` is the one-line summary
 *  in the active language; on failure it carries the error detail. */
data class AgentResult(
    val agentName: String,
    val ok: Boolean,
    val line: String,
    val needsConfirmation: Boolean = false,
    val pendingAction: DeviceAction? = null,
)

/** UI chip showing a specialist's live status. */
data class AgentChip(val name: String, val icon: String, val state: ChipState)

enum class ChipState { WORKING, DONE, FAILED }

/** A risky action waiting on Kamran's tap. Lives here (not in the UI
 *  package) so the orchestrator can produce it without depending on UI. */
data class PendingAction(val question: String, val action: DeviceAction)

/**
 * Everything a specialist agent may need. [onSetMode] is the dual-mode
 * escape hatch: mode switches go through the ViewModel's runAction path,
 * never through an agent.
 */
data class AgentDeps(
    val deviceActions: DeviceActions,
    val notes: NotesStore,
    val memory: MemoryStore,
    val appContext: Context,
    val onSetMode: (jarvis: Boolean) -> Unit,
)

/** One specialist. [canHandle] decides routing; [execute] must never
 *  throw — failures come back as [AgentResult] with ok=false. */
interface SpecialistAgent {
    val name: String
    val icon: String
    fun canHandle(a: DeviceAction): Boolean
    suspend fun execute(t: AgentTask): AgentResult
}

// ------------------------------------------------------------------
// Shared helpers (internal to the agents package)
// ------------------------------------------------------------------

/**
 * Runs [block], converting ANY thrown exception into a failed
 * [AgentResult]. This is the failure-isolation contract: one agent's
 * crash never blocks the others.
 */
internal suspend fun SpecialistAgent.runGuarded(
    task: AgentTask,
    block: suspend () -> AgentResult,
): AgentResult = try {
    block()
} catch (e: Exception) {
    AgentResult(
        agentName = name,
        ok = false,
        line = L3(
            en = "Hit a snag, sir: ${e.message ?: e.javaClass.simpleName}",
            ur = "مشکل پیش آئی، سر: ${e.message ?: e.javaClass.simpleName}",
            hi = "दिक्कत आई, सर: ${e.message ?: e.javaClass.simpleName}",
        ).pick(task.lang),
    )
}

/** Maps a raw [ActionResult] to an [AgentResult] with a trilingual line. */
internal fun SpecialistAgent.viaDeviceActions(deps: AgentDeps, t: AgentTask): AgentResult =
    when (val r = deps.deviceActions.execute(t.action)) {
        is ActionResult.Ok -> AgentResult(name, true, confirmLine(t.action, t.lang))
        is ActionResult.NeedPermission -> AgentResult(name, false, permissionLine(t.lang, r.message))
        is ActionResult.Failed -> AgentResult(name, false, failedLine(t.lang, r.message))
    }

/** Trilingual "I need a permission" line, with the engine's human hint. */
internal fun permissionLine(lang: BalochLanguage, message: String): String = L3(
    en = message,
    ur = "اجازت درکار ہے، سر۔ $message",
    hi = "अनुमति चाहिए, सर। $message",
).pick(lang)

/** Trilingual failure line carrying the engine's human message. */
internal fun failedLine(lang: BalochLanguage, message: String): String = L3(
    en = message,
    ur = "کام مکمل نہیں ہو سکا، سر۔ $message",
    hi = "काम पूरा नहीं हो सका, सर। $message",
).pick(lang)

/** One-line trilingual confirmation for a completed action. */
internal fun confirmLine(action: DeviceAction, lang: BalochLanguage): String = when (action) {
    is DeviceAction.OpenApp -> L3(
        "Opening ${action.appName}, sir.",
        "${action.appName} کھول رہا ہوں، سر۔",
        "${action.appName} खोल रहा हूँ, सर।").pick(lang)
    is DeviceAction.SetAlarm -> {
        val t = "%d:%02d".format(action.hour, action.minute)
        L3("Alarm set for $t, sir.", "$t کا الارم لگا دیا، سر۔", "$t का अलार्म लगा दिया, सर।").pick(lang)
    }
    is DeviceAction.StartTimer -> L3(
        "Timer started, sir.", "ٹائمر شروع کر دیا، سر۔", "टाइमर शुरू कर दिया, सर।").pick(lang)
    is DeviceAction.AddReminder -> L3(
        "I'll remind you in ${action.minutesFromNow} minutes, sir.",
        "${action.minutesFromNow} منٹ میں یاد دلاؤں گا، سر۔",
        "${action.minutesFromNow} मिनट में याद दिलाऊँगा, सर।").pick(lang)
    is DeviceAction.ToggleWifi -> if (action.on) L3(
        "Wi-Fi on, sir.", "وائی فائی آن کر دیا، سر۔", "वाईफाई चालू कर दिया, सर।").pick(lang)
    else L3("Wi-Fi off, sir.", "وائی فائی بند کر دیا، سر۔", "वाईफाई बंद कर दिया, सर।").pick(lang)
    is DeviceAction.ToggleBluetooth -> if (action.on) L3(
        "Bluetooth on, sir.", "بلوٹوتھ آن کر دیا، سر۔", "ब्लूटूथ चालू कर दिया, सर।").pick(lang)
    else L3("Bluetooth off, sir.", "بلوٹوتھ بند کر دیا، سر۔", "ब्लूटूथ बंद कर दिया, सर।").pick(lang)
    is DeviceAction.ToggleFlashlight -> if (action.on) L3(
        "Torch on, sir.", "ٹارچ جلا دی، سر۔", "टॉर्च जला दी, सर।").pick(lang)
    else L3("Torch off, sir.", "ٹارچ بجھا دی، سر۔", "टॉर्च बुझा दी, सर।").pick(lang)
    is DeviceAction.SetVolume -> L3(
        "Volume set to ${action.levelPercent}%, sir.",
        "آواز ${action.levelPercent} فیصد کر دی، سر۔",
        "आवाज़ ${action.levelPercent} प्रतिशत कर दी, सर।").pick(lang)
    is DeviceAction.AdjustVolume -> if (action.up) L3(
        "Volume up, sir.", "آواز تیز کر دی، سر۔", "आवाज़ तेज़ कर दी, सर।").pick(lang)
    else L3("Volume down, sir.", "آواز کم کر دی، سر۔", "आवाज़ कम कर दी, सर।").pick(lang)
    is DeviceAction.SetMuted -> if (action.muted) L3(
        "Muted, sir.", "خاموش کر دیا، سر۔", "म्यूट कर दिया, सर।").pick(lang)
    else L3("Sound back on, sir.", "آواز بحال کر دی، سر۔", "आवाज़ वापस चालू कर दी, सर।").pick(lang)
    is DeviceAction.SetBrightness -> L3(
        "Brightness set to ${action.levelPercent}%, sir.",
        "روشنی ${action.levelPercent} فیصد کر دی، سر۔",
        "रोशनी ${action.levelPercent} प्रतिशत कर दी, सर।").pick(lang)
    is DeviceAction.WebSearch -> L3(
        "Searching for \"${action.query}\", sir.",
        "\"${action.query}\" تلاش کر رہا ہوں، سر۔",
        "\"${action.query}\" खोज रहा हूँ, सर।").pick(lang)
    is DeviceAction.AddNote -> L3(
        "Noted, sir.", "نوٹ کر لیا، سر۔", "नोट कर लिया, सर।").pick(lang)
    is DeviceAction.RememberFact -> L3(
        "I'll remember that, sir.", "یہ یاد رکھوں گا، سر۔", "यह याद रखूँगा, सर।").pick(lang)
    is DeviceAction.CallContact -> L3(
        "Calling ${action.name}, sir.", "${action.name} کو کال کر رہا ہوں، سر۔",
        "${action.name} को कॉल कर रहा हूँ, सर।").pick(lang)
    is DeviceAction.SendSms -> L3(
        "Message sent to ${action.name}, sir.", "${action.name} کو پیغام بھیج دیا، سر۔",
        "${action.name} को संदेश भेज दिया, सर।").pick(lang)
    is DeviceAction.SetDnd -> if (action.on) L3(
        "Do not disturb on, sir.", "ڈو ناٹ ڈسٹرب آن کر دیا، سر۔",
        "डू नॉट डिस्टर्ब चालू कर दिया, सर।").pick(lang)
    else L3("Do not disturb off, sir.", "ڈو ناٹ ڈسٹرب آف کر دیا، سر۔",
        "डू नॉट डिस्टर्ब बंद कर दिया, सर।").pick(lang)
    is DeviceAction.DisableDndDelayed -> L3(
        "DND will lift in ${action.minutes} minutes, sir.",
        "${action.minutes} منٹ میں ڈو ناٹ ڈسٹرب ختم ہو جائے گا، سر۔",
        "${action.minutes} मिनट में डू नॉट डिस्टर्ब खत्म हो जाएगा, सर।").pick(lang)
    is DeviceAction.WhatsAppDraft -> L3(
        "WhatsApp draft ready for ${action.name}, sir.",
        "${action.name} کے لیے واٹس ایپ ڈرافٹ تیار ہے، سر۔",
        "${action.name} के लिए व्हाट्सऐप ड्राफ्ट तैयार है, सर।").pick(lang)
    is DeviceAction.EmergencyCall -> L3(
        "Calling your emergency contact ${action.name}, sir.",
        "آپ کے ایمرجنسی رابطے ${action.name} کو کال کر رہا ہوں، سر۔",
        "आपके आपातकालीन संपर्क ${action.name} को कॉल कर रहा हूँ, सर।").pick(lang)
    else -> L3("Done, sir.", "ہو گیا، سر۔", "हो गया, सर।").pick(lang)
}
