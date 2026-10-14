package com.baloch.ai

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import com.baloch.ai.agents.AgentDeps
import com.baloch.ai.agents.Orchestrator
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.DeviceContext
import com.baloch.ai.brain.RuleBasedBrain
import com.baloch.ai.companion.CheckInWorker
import com.baloch.ai.engine.DeviceActions
import com.baloch.ai.engine.NotesStore
import com.baloch.ai.engine.NotificationReader
import com.baloch.ai.engine.pro.BatteryGuardianWorker
import com.baloch.ai.engine.pro.ClipboardWatcher
import com.baloch.ai.memory.MemoryStore
import com.baloch.ai.memory.scheduleMemoryJanitor
import com.baloch.ai.rules.GuidanceRuleStore
import com.baloch.ai.service.HandsFreePipeline
import com.baloch.ai.service.HandsFreeWiring
import com.baloch.ai.settings.LanguageManager
import com.baloch.ai.voice.VoiceLockManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Baloch application entry point.
 *
 * onCreate wires the process-lifetime pieces:
 *  - [ClipboardWatcher.init] — clipboard listener for the app's lifetime
 *    (see its KDoc for the honest Android 10+ background limitation).
 *  - [BatteryGuardianWorker.schedule] — 30-min battery guardian (JARVIS-gated
 *    internally; silent in Manual mode).
 *  - [CheckInWorker.schedule] — proactive check-ins (JARVIS mode only,
 *    1–2/day, user-configurable).
 *  - [scheduleMemoryJanitor] — monthly + daily expiry sweep for TEMPORARY
 *    memories; PERMANENT facts are never touched.
 *  - [GuidanceRuleStore.seedDefaultsIfNeeded] — his two starter safety rules
 *    (deletable; they are his, not code locks).
 *  - [HandsFreeWiring.pipeline] — the always-on voice path: wake word →
 *    voice-lock check → [Orchestrator] → spoken reply, all with the
 *    screen off.
 *
 * [instance] gives engine/brain helpers a safe application Context
 * without threading one through every constructor. It is set here,
 * before any Activity or ViewModel exists.
 */
class BalochApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            ClipboardWatcher.init(this)
        } catch (_: Exception) {
            // Clipboard history is best-effort; never crash startup.
        }
        try {
            BatteryGuardianWorker.schedule(this)
        } catch (_: Exception) {
            // Worker scheduling is best-effort; never crash startup.
        }
        try {
            CheckInWorker.schedule(this)
        } catch (_: Exception) {
            // Check-ins are best-effort; never crash startup.
        }
        try {
            scheduleMemoryJanitor(this)
        } catch (_: Exception) {
            // Janitor scheduling is best-effort; never crash startup.
        }
        try {
            GuidanceRuleStore(this).seedDefaultsIfNeeded()
        } catch (_: Exception) {
            // Rule seeding is best-effort; never crash startup.
        }
        wireHandsFreePipeline()
    }

    /**
     * Connects the always-on listener service to the real brain.
     * Contract (see HandsFreeSeams.kt):
     * - verifySpeaker false = NOT Kamran → the listener refuses and
     *   executes nothing. Never wire this to a stub returning true.
     * - execute runs the full Orchestrator (governance gates included),
     *   so voice-lock, GuidanceRules and PersonalDataGuard all apply
     *   hands-free exactly as they do on screen.
     */
    private fun wireHandsFreePipeline() {
        try {
            val appCtx = applicationContext
            val deviceActions = DeviceActions(appCtx)
            val memory = MemoryStore(appCtx)
            val notes = NotesStore(appCtx)
            val voiceLock = VoiceLockManager(appCtx)
            val brain = RuleBasedBrain(
                memory = memory,
                notes = notes,
                resolveContact = deviceActions::resolveContact,
                notificationSummary = {
                    if (NotificationReader.isEnabled(appCtx)) NotificationReader.summary()
                    else null
                },
            )
            val deps = AgentDeps(
                deviceActions = deviceActions,
                notes = notes,
                memory = memory,
                appContext = appCtx,
                onSetMode = { /* hands-free mode switch: handled by the UI layer */ },
            )
            val orchestrator = Orchestrator(deps, brain)
            HandsFreeWiring.pipeline = object : HandsFreePipeline {
                override suspend fun execute(commandText: String, lang: BalochLanguage): String {
                    val ctx = handsFreeContext(appCtx, lang)
                    return try {
                        orchestrator.run(commandText, ctx, onChips = {}).reply
                    } catch (e: Exception) {
                        "Something glitched, sir."
                    }
                }

                override suspend fun verifySpeaker(audio: ShortArray): Boolean =
                    try {
                        voiceLock.verify(appCtx, audio)
                    } catch (_: Exception) {
                        false // Fail closed: unverifiable voice never executes.
                    }

                override fun isVoiceLockEnabled(): Boolean =
                    try {
                        voiceLock.isEnabled(appCtx)
                    } catch (_: Exception) {
                        false
                    }
            }
        } catch (_: Exception) {
            // Until the pipeline is wired, BalochListenerService says so
            // honestly and does nothing else. Never crash startup.
            HandsFreeWiring.pipeline = null
        }
    }

    private fun handsFreeContext(appCtx: Context, lang: BalochLanguage): DeviceContext {
        val bm = appCtx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pct = try {
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (_: Exception) {
            -1
        }
        val now = Date()
        return DeviceContext(
            batteryPercent = pct,
            isCharging = false, // best-effort here; the UI path reads it live
            timeString = SimpleDateFormat("h:mm a", Locale.UK).format(now),
            dateString = SimpleDateFormat("EEE d MMM", Locale.UK).format(now),
            language = try {
                LanguageManager(appCtx).current()
            } catch (_: Exception) {
                BalochLanguage.ENGLISH
            },
        )
    }

    companion object {
        @Volatile
        var instance: BalochApp? = null
            private set
    }
}
