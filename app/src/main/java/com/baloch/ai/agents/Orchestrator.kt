package com.baloch.ai.agents

import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.BrainEngine
import com.baloch.ai.brain.BrainResponse
import com.baloch.ai.brain.DeviceAction
import com.baloch.ai.brain.DeviceContext
import com.baloch.ai.brain.Intents
import com.baloch.ai.brain.L3
import com.baloch.ai.callerid.SpamShield
import com.baloch.ai.gesture.GestureCmd
import com.baloch.ai.gesture.GestureControlService
import com.baloch.ai.gesture.GestureCalibrator
import com.baloch.ai.gesture.SensorGestureCmd
import com.baloch.ai.gesture.SensorGestureService
import com.baloch.ai.gesture.gestureReply
import com.baloch.ai.gesture.matchGestureCommand
import com.baloch.ai.gesture.matchSensorGestureCommand
import com.baloch.ai.gesture.sensorGestureReply
import com.baloch.ai.memory.MemoryRepository
import com.baloch.ai.memory.answerRecall
import com.baloch.ai.memory.executeMemoryCommand
import com.baloch.ai.memory.extractAndSaveDetailed
import com.baloch.ai.memory.matchMemoryCommand
import com.baloch.ai.memory.matchRecall
import com.baloch.ai.rules.GuidanceRuleStore
import com.baloch.ai.rules.PersonalDataGuard
import com.baloch.ai.rules.PersonalDataGuard.dataTouch
import com.baloch.ai.rules.PersonalDataGuard.requiresExplicitConfirm
import com.baloch.ai.rules.Verdict
import com.baloch.ai.rules.matchRuleQuery
import com.baloch.ai.rules.parseRuleTeach
import com.baloch.ai.rules.RuleQuery
import com.baloch.ai.schedule.ScheduleKit
import com.baloch.ai.life.LifeExecutor
import com.baloch.ai.life.SosHelper
import com.baloch.ai.life.matchLife
import com.baloch.ai.engine.pro.EmergencyStore
import com.baloch.ai.power.PowerToggles
import com.baloch.ai.power.matchPower
import com.baloch.ai.voice.VoiceLockCmd
import com.baloch.ai.voice.VoiceLockManager
import com.baloch.ai.voice.matchVoiceLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/** Everything the orchestrator hands back to the ViewModel. */
data class OrchestratedResult(
    val reply: String,
    val pending: PendingAction?,
    val chips: List<AgentChip>,
    /** Screen the UI should open (rules, voicelock, settings_gestures…), or null. */
    val navigateTo: String? = null,
)

/**
 * Multi-agent parallel execution.
 *
 * 1. PRE-ROUTING: whole-input commands (scheduling, voice lock, teachable
 *    rules, memory commands, recall, gestures, blocklist) are handled
 *    before the multi-part split — they own the entire utterance.
 * 2. Splits the input on trilingual conjunctions (and/aur/or/phir/then/
 *    اور/پھر/और/फिर). A single part takes the fast path — no split overhead.
 * 3. Runs the brain once per part → (text, actions).
 * 4. EVERY action passes the governance gate before execution:
 *    - GuidanceRules FORBID → blocked with his rule quoted (wins over all).
 *    - PersonalDataGuard MUTATE_TRANSMIT → mandatory confirmation naming
 *      the exact payload, both modes; only his scope-specific standing
 *      allowance relaxes it (blanket "don't ask" cannot).
 *    - GuidanceRules REQUIRE_CONFIRM → confirmation dialog.
 * 5. Routes each action to the first specialist whose [SpecialistAgent.canHandle]
 *    claims it, or the [FallbackAgent] for anything unclaimed.
 * 6. Launches ALL agents concurrently with `async`; chips flip
 *    WORKING → DONE/FAILED through [onChips] as each finishes.
 * 7. Synthesises ONE reply from every part; CompanionAgent answers when no
 *    command intent matched (never a dead end); the memory system learns
 *    from the exchange (READ-class, always allowed).
 *
 * Failure isolation: `supervisorScope` + per-agent try/catch (agents also
 * catch internally via [runGuarded]) — one agent's exception can never
 * cancel or block the others.
 *
 * [DeviceAction.SetMode] is NEVER routed to an agent: it goes through
 * [AgentDeps.onSetMode] so the ViewModel's ModeManager path applies it.
 */
class Orchestrator(
    private val deps: AgentDeps,
    private val brain: BrainEngine,
    briefProviders: BriefProviders = BriefProviders(),
) {
    private val agents: List<SpecialistAgent> = listOf(
        SystemAgent(deps),
        InfoAgent(deps),
        CommsAgent(deps),
        TimeAgent(deps),
        MemoryAgent(deps),
        BriefAgent(deps, briefProviders),
        SettingsAgent(deps),
    )
    private val fallback: SpecialistAgent = FallbackAgent(deps)

    // Governance stores — built once from the app context.
    private val ruleStore: GuidanceRuleStore by lazy { GuidanceRuleStore(deps.appContext) }
    private val voiceLock: VoiceLockManager by lazy { VoiceLockManager(deps.appContext) }
    private val memoryRepo: MemoryRepository by lazy { MemoryRepository.get(deps.appContext) }
    private val companion: CompanionAgent by lazy { CompanionAgent(deps.memory, deps.appContext) }

    suspend fun run(
        input: String,
        ctx: DeviceContext,
        onChips: (List<AgentChip>) -> Unit,
        /** Voice confidence 0..1 when the input came from voice; null for typed. */
        voiceConfidence: Float? = null,
    ): OrchestratedResult = supervisorScope {
        val lang = ctx.language
        val appCtx = deps.appContext

        // ---- PRE-ROUTING: whole-utterance commands own the input ----
        preRoute(input, ctx)?.let { return@supervisorScope it }

        val rawParts = SPLIT_RX.split(input).map { it.trim() }.filter { it.isNotEmpty() }
        val parts = rawParts.ifEmpty { listOf(input.trim()) }
        val multi = parts.size > 1

        // One brain pass per part.
        data class PartPlan(
            val partText: String,
            val base: BrainResponse,
            /** Brain answered fully in text; nothing to execute. */
            val textOnly: Boolean,
        )
        val plans = parts.map { part ->
            val hit = Intents.match(part.lowercase())
            var base = brain.respond(part, ctx)
            if (hit == null && base.actions.isEmpty()) {
                // Conversational fallback — CompanionAgent, never a dead end.
                base = base.copy(text = companion.chat(part, lang))
            }
            val pureInfoHit = hit is Intents.Hit.Greeting || hit is Intents.Hit.Thought ||
                hit is Intents.Hit.Time || hit is Intents.Hit.Battery ||
                hit is Intents.Hit.Stats || hit is Intents.Hit.Notifications ||
                hit is Intents.Hit.NotesList
            PartPlan(part, base, base.actions.isEmpty() || pureInfoHit)
        }

        // Route every action through the governance gate, then to an agent
        // (or the SetMode escape hatch).
        data class Job(
            val key: String,
            val agent: SpecialistAgent,
            val task: AgentTask,
            val planIndex: Int,
        )
        val jobs = mutableListOf<Job>()
        val rulePendings = mutableListOf<PendingAction>()
        val partResults = Array(parts.size) { mutableListOf<AgentResult>() }
        plans.forEachIndexed { pi, plan ->
            if (plan.textOnly) return@forEachIndexed
            plan.base.actions.forEachIndexed { ai, a ->
                if (a is DeviceAction.SetMode) {
                    // Dual-mode switch: applied via the ViewModel, never via agents.
                    deps.onSetMode(a.jarvis)
                    partResults[pi] += AgentResult("Orchestrator", true, plan.base.text)
                    return@forEachIndexed
                }
                // ---- GOVERNANCE GATE ----
                // 1) His FORBID rules win over everything (his word is law).
                val verdict = ruleStore.effectiveVerdict(a)
                if (verdict is Verdict.Forbid) {
                    partResults[pi] += AgentResult("Guidance", false, forbidLine(verdict.reason, lang))
                    return@forEachIndexed
                }
                // 2) Learn-vs-act: transmit-class needs HIS explicit yes, both
                //    modes. Only a scope-specific standing allowance relaxes it.
                if (PersonalDataGuard.requiresExplicitConfirm(a) && !a.needsConfirmation) {
                    val allowance = PersonalDataGuard.standingAllowanceFor(a, ruleStore)
                    if (allowance == null) {
                        var question = PersonalDataGuard.guardConfirmText(a, lang)
                        if (PersonalDataGuard.needsPayloadReconfirm(a, voiceConfidence)) {
                            question = L3(
                                "One more check, sir — I want to be sure I heard right. $question",
                                "ایک بار پھر تصدیق کر لیں، سر — میں یقینی بنانا چاہتا ہوں کہ میں نے صحیح سنا۔ $question",
                                "एक बार फिर पुष्टि कर लें, सर — मैं सुनिश्चित करना चाहता हूँ कि मैंने सही सुना। $question",
                            ).pick(lang)
                        }
                        rulePendings += PendingAction(question, a)
                        return@forEachIndexed
                    }
                } else if (verdict is Verdict.RequireConfirm && !a.needsConfirmation) {
                    // 3) His REQUIRE_CONFIRM rules (beyond the built-in risky tier).
                    rulePendings += PendingAction(ruleConfirmLine(plan.base.text, lang), a)
                    return@forEachIndexed
                }
                val agent = agents.firstOrNull { it.canHandle(a) } ?: fallback
                jobs += Job(
                    key = "${agent.name}#$pi-$ai",
                    agent = agent,
                    task = AgentTask(a, lang, plan.partText),
                    planIndex = pi,
                )
            }
        }

        // Fire all agents at once; flip chips as each lands.
        val chipState = jobs
            .associate { it.key to AgentChip(it.agent.name, it.agent.icon, ChipState.WORKING) }
            .toMutableMap()
        fun emitChips() = onChips(chipState.values.toList())
        emitChips()

        val finished = jobs.map { job ->
            async(Dispatchers.IO) {
                val result = try {
                    job.agent.execute(job.task)
                } catch (e: Exception) {
                    // Belt and braces: agents already guard internally.
                    AgentResult(
                        job.agent.name, false,
                        failedLine(lang, e.message ?: e.javaClass.simpleName),
                    )
                }
                synchronized(chipState) {
                    chipState[job.key] = AgentChip(
                        job.agent.name, job.agent.icon,
                        if (result.ok) ChipState.DONE else ChipState.FAILED,
                    )
                    emitChips()
                }
                job.planIndex to result
            }
        }
        finished.awaitAll().forEach { (pi, r) -> partResults[pi] += r }
        emitChips()

        // Governance pendings first, then the agents' own risky-action pendings.
        val pending = rulePendings.firstOrNull()
            ?: partResults.flatMap { it }
                .firstOrNull { it.needsConfirmation && it.pendingAction != null }
                ?.let { PendingAction(it.line, it.pendingAction!!) }

        // One unified reply.
        var reply = if (!multi) {
            val results = partResults[0]
            val failed = results.firstOrNull { !it.ok }
            if (failed == null) plans[0].base.text
            else plans[0].base.text + " — (${failed.agentName} hit a snag: ${failed.line})"
        } else {
            val intro = L3(
                en = "Done, sir —",
                ur = "ہو گیا، سر —",
                hi = "हो गया, सर —",
            ).pick(lang)
            val lines = plans.mapIndexed { pi, plan ->
                val results = partResults[pi]
                if (results.isEmpty()) plan.base.text
                else results.joinToString("; ") { r ->
                    if (r.ok) r.line else "(${r.agentName} hit a snag: ${r.line})"
                }
            }
            "$intro\n• " + lines.joinToString("\n• ")
        }

        // Memory learns from every exchange (READ-class — always allowed).
        // Never asks "should I remember?"; just remembers, like a friend.
        try {
            val extraction = extractAndSaveDetailed(input, memoryRepo, lang)
            if (extraction.savedPermanent) {
                memoryRepo.upgradeLatestTemporaryConversation()
            }
            extraction.confirmation?.let { reply = "$reply\n$it" }
        } catch (_: Exception) {
            // Learning must never break answering.
        }

        OrchestratedResult(reply, pending, chipState.values.toList())
    }

    // ==================================================================
    // PRE-ROUTING — whole-utterance commands
    // ==================================================================

    private suspend fun preRoute(input: String, ctx: DeviceContext): OrchestratedResult? {
        val lang = ctx.language
        val appCtx = deps.appContext
        val lower = input.lowercase()

        // 1) Task scheduling — explicit orders, fire in BOTH modes.
        ScheduleKit.parse(input)?.let { draft ->
            val id = ScheduleKit.commit(appCtx, draft)
            return OrchestratedResult(ScheduleKit.confirmText(draft, lang), null, emptyList())
        }

        // 2) Voice lock commands.
        matchVoiceLock(lower)?.let { cmd ->
            return when (cmd) {
                VoiceLockCmd.Enable -> {
                    if (!voiceLock.isEnrolled(appCtx)) {
                        OrchestratedResult(
                            L3(
                                "Voice lock needs your voice first, sir — enroll in Settings → Security → Voice Lock (3 quick samples).",
                                "وائس لاک کے لیے پہلے آپ کی آواز چاہیے، سر — سیٹنگز → سیکیورٹی → وائس لاک میں انرول کریں (3 مختصر نمونے)۔",
                                "वॉयस लॉक के लिए पहले आपकी आवाज़ चाहिए, सर — सेटिंग्स → सुरक्षा → वॉयस लॉक में एनरोल करें (3 छोटे सैंपल)।",
                            ).pick(lang), null, emptyList(), navigateTo = "voicelock",
                        )
                    } else {
                        voiceLock.setEnabled(appCtx, true)
                        OrchestratedResult(
                            L3(
                                "Voice lock engaged, sir — only your voice commands me now. 🔐",
                                "وائس لاک فعال، سر — اب صرف آپ کی آواز پر عمل کروں گا۔ 🔐",
                                "वॉयस लॉक सक्रिय, सर — अब केवल आपकी आवाज़ पर अमल करूँगा। 🔐",
                            ).pick(lang), null, emptyList(),
                        )
                    }
                }
                VoiceLockCmd.DisableRequest -> {
                    // Turning OFF needs proof it's him — a stranger must never
                    // be able to disarm the lock with words alone.
                    if (voiceLock.canDisable(appCtx)) {
                        voiceLock.setEnabled(appCtx, false)
                        OrchestratedResult(
                            L3(
                                "Voice lock off, sir — verified it was you.",
                                "وائس لاک آف، سر — تصدیق ہو گئی کہ یہ آپ ہی تھے۔",
                                "वॉयस लॉक बंद, सर — सत्यापित हो गया कि यह आप ही थे।",
                            ).pick(lang), null, emptyList(),
                        )
                    } else {
                        OrchestratedResult(
                            L3(
                                "I couldn't verify your voice, sir — say it again, or enter your PIN in Settings → Security → Voice Lock to disable.",
                                "میں آپ کی آواز کی تصدیق نہیں کر سکا، سر — دوبارہ کہیں، یا غیر فعال کرنے کے لیے سیٹنگز → سیکیورٹی → وائس لاک میں اپنا PIN درج کریں۔",
                                "मैं आपकी आवाज़ सत्यापित नहीं कर सका, सर — फिर से कहें, या अक्षम करने के लिए सेटिंग्स → सुरक्षा → वॉयस लॉक में अपना PIN दर्ज करें।",
                            ).pick(lang), null, emptyList(), navigateTo = "voicelock",
                        )
                    }
                }
                VoiceLockCmd.Status -> OrchestratedResult(
                    L3(
                        "Voice lock is ${if (voiceLock.isEnabled(appCtx)) "ON" else "OFF"}, sir${if (voiceLock.isEnrolled(appCtx)) "" else " (not enrolled yet)"}.",
                        "وائس لاک ${if (voiceLock.isEnabled(appCtx)) "آن" else "آف"} ہے، سر${if (voiceLock.isEnrolled(appCtx)) "" else " (ابھی انرول نہیں ہوا)"}۔",
                        "वॉयस लॉक ${if (voiceLock.isEnabled(appCtx)) "चालू" else "बंद"} है, सर${if (voiceLock.isEnrolled(appCtx)) "" else " (अभी एनरोल नहीं हुआ)"}।",
                    ).pick(lang), null, emptyList(),
                )
            }
        }

        // 3) Teachable guidance rules — his words, stored as data.
        parseRuleTeach(input)?.let { rule ->
            val saved = ruleStore.add(rule)
            return OrchestratedResult(
                L3(
                    "Noted as your rule, sir — \"${saved.text}\". I'll follow it from now on. See it anytime in Settings → Security → My Rules. 📏",
                    "آپ کے اصول کے طور پر نوٹ کر لیا، سر — \"${saved.text}\"۔ اب سے اس پر عمل کروں گا۔ سیٹنگز → سیکیورٹی → My Rules میں کبھی بھی دیکھیں۔ 📏",
                    "आपके नियम के रूप में नोट कर लिया, सर — \"${saved.text}\"। अब से इस पर अमल करूँगा। सेटिंग्स → सुरक्षा → My Rules में कभी भी देखें। 📏",
                ).pick(lang), null, emptyList(),
            )
        }
        matchRuleQuery(lower)?.let { query ->
            return when (query) {
                RuleQuery.ListRules -> OrchestratedResult(
                    L3(
                        "Opening your rules, sir — every one is yours to change or delete.",
                        "آپ کے اصول کھول رہا ہوں، سر — ہر ایک آپ بدل یا حذف کر سکتے ہیں۔",
                        "आपके नियम खोल रहा हूँ, सर — हर एक आप बदल या हटा सकते हैं।",
                    ).pick(lang), null, emptyList(), navigateTo = "rules",
                )
                is RuleQuery.DeleteRule -> {
                    val rules = ruleStore.all()
                    val target = rules.getOrNull(query.index - 1)
                    if (target == null) {
                        OrchestratedResult(
                            L3(
                                "I don't have a rule ${query.index}, sir — say \"show my rules\" to see them all.",
                                "میرے پاس اصول ${query.index} نہیں ہے، سر — سب دیکھنے کے لیے کہیں \"show my rules\"۔",
                                "मेरे पास नियम ${query.index} नहीं है, सर — सब देखने के लिए कहें \"show my rules\"।",
                            ).pick(lang), null, emptyList(),
                        )
                    } else {
                        ruleStore.delete(target.id)
                        OrchestratedResult(
                            L3(
                                "Rule deleted, sir — \"${target.text}\".",
                                "اصول حذف کر دیا، سر — \"${target.text}\"۔",
                                "नियम हटा दिया, सर — \"${target.text}\"।",
                            ).pick(lang), null, emptyList(),
                        )
                    }
                }
            }
        }

        // 4) Two-tier memory commands (pin / forget / make permanent).
        matchMemoryCommand(lower)?.let { cmd ->
            val text = executeMemoryCommand(cmd, input, memoryRepo, lang)
            return OrchestratedResult(text, null, emptyList())
        }

        // 5) Recall — "what do you remember about me".
        if (matchRecall(lower)) {
            return OrchestratedResult(answerRecall(input, memoryRepo, lang), null, emptyList())
        }

        // 6) Camera gesture voice control.
        matchGestureCommand(lower)?.let { cmd ->
            val on = cmd != GestureCmd.Disable
            GestureControlService.setMasterEnabled(appCtx, on)
            val calibrated = GestureCalibrator.isCalibrated(appCtx)
            return OrchestratedResult(
                gestureReply(cmd, on, calibrated, lang),
                null, emptyList(),
                navigateTo = if (cmd == GestureCmd.Enable && !calibrated) "gesturelab" else null,
            )
        }

        // 7) Sensor gesture voice control.
        matchSensorGestureCommand(lower)?.let { cmd ->
            val on = cmd != SensorGestureCmd.Disable
            SensorGestureService.setEnabled(appCtx, on)
            return OrchestratedResult(sensorGestureReply(cmd, on, lang), null, emptyList())
        }

        // 8) Caller-ID blocklist (teachable: "is number ko block kar de").
        SpamShield.matchBlockCommand(lower)?.let { cmd ->
            return when (cmd) {
                is SpamShield.BlockCmd.Block -> {
                    if (cmd.target == SpamShield.LAST_NUMBER) {
                        OrchestratedResult(
                            L3(
                                "Which number should I block, sir? Say it with the number, like \"block 0300 1234567\".",
                                "کون سا نمبر بلاک کروں، سر؟ نمبر کے ساتھ کہیں، جیسے \"block 0300 1234567\"۔",
                                "कौन सा नंबर ब्लॉक करूँ, सर? नंबर के साथ कहें, जैसे \"block 0300 1234567\"।",
                            ).pick(lang), null, emptyList(),
                        )
                    } else {
                        SpamShield.block(appCtx, cmd.target)
                        OrchestratedResult(
                            L3(
                                "Blocked ${cmd.target}, sir — I'll silence it from now on. 🚫",
                                "${cmd.target} بلاک کر دیا، سر — اب سے اسے خاموش رکھوں گا۔ 🚫",
                                "${cmd.target} ब्लॉक कर दिया, सर — अब से इसे शांत रखूँगा। 🚫",
                            ).pick(lang), null, emptyList(),
                        )
                    }
                }
                is SpamShield.BlockCmd.Unblock -> {
                    SpamShield.unblock(appCtx, cmd.target)
                    OrchestratedResult(
                        L3(
                            "Unblocked ${cmd.target}, sir.",
                            "${cmd.target} ان بلاک کر دیا، سر۔",
                            "${cmd.target} अनब्लॉक कर दिया, सर।",
                        ).pick(lang), null, emptyList(),
                    )
                }
            }
        }

        // 9) Voice-controlled settings — every toggle by voice ("driving mode on kar do").
        matchSettingsCommand(input)?.let { cmd ->
            val agent = SettingsAgent(deps)
            val result = agent.execute(AgentTask(cmd.toDeviceAction(), lang, input))
            val nav = when (cmd) {
                is SettingsCmd.OpenSettings -> "settings"
                is SettingsCmd.QuerySettings -> null
                is SettingsCmd.ToggleSetting -> null
            }
            return OrchestratedResult(
                reply = result.line,
                pending = null,
                chips = listOf(AgentChip(agent.name, agent.icon, ChipState.DONE)),
                navigateTo = nav,
            )
        }

        // 10) Life features — prayer, expenses, focus, translate, SOS, …
        matchLife(input)?.let { hit ->
            val out = LifeExecutor.execute(appCtx, hit, lang)
            if (out is LifeExecutor.SosPending) {
                // Transmit-class: SOS always confirms first (PersonalDataGuard).
                val ice = EmergencyStore.get(appCtx)
                if (ice.isNullOrBlank()) {
                    return OrchestratedResult(
                        L3(
                            "Sir, set your emergency contact first — Settings → Emergency → ICE contact. Then \"SOS\" will reach them instantly. 🆘",
                            "سر، پہلے ایمرجنسی رابطہ سیٹ کریں — Settings → Emergency → ICE contact۔ 🆘",
                            "सर, पहले आपातकालीन संपर्क सेट करें — Settings → Emergency → ICE contact। 🆘",
                        ).pick(lang), null, emptyList(),
                        navigateTo = "settings",
                    )
                }
                val loc = try { SosHelper.bestLocation(appCtx) } catch (_: Exception) { null }
                val link = loc?.let {
                    "https://maps.google.com/?q=${it.latitude},${it.longitude}"
                } ?: "location unavailable"
                val question = L3(
                    "Sir, SOS will text your location to $ice — send it?",
                    "سر، SOS آپ کی لوکیشن $ice کو بھیجے گا — بھیجوں؟",
                    "सर, SOS आपकी लोकेशन $ice को भेजेगा — भेजूँ?",
                ).pick(lang)
                return OrchestratedResult(
                    question,
                    PendingAction(question, DeviceAction.SendSosAlert(ice, link)),
                    emptyList(),
                )
            }
            return OrchestratedResult(
                reply = out as String,
                pending = null,
                chips = listOf(AgentChip("Life", "🌿", ChipState.DONE)),
            )
        }

        // 11) Dormant power features — driving/ramadan/cricket/…
        matchPower(input)?.let { hit ->
            val line = try {
                PowerToggles.applyHit(appCtx, hit).pick(lang)
            } catch (e: Exception) {
                L3(
                    "Hmm, that didn't switch, sir.",
                    "ہمم، یہ تبدیل نہیں ہوا، سر۔",
                    "हम्म, यह नहीं बदला, सर।",
                ).pick(lang)
            }
            return OrchestratedResult(
                reply = line,
                pending = null,
                chips = listOf(AgentChip("Power", "⚡", ChipState.DONE)),
            )
        }

        return null
    }

    private fun forbidLine(reason: String, lang: BalochLanguage): String = L3(
        en = "Sir, your rule \"$reason\" stops me there — I won't do that one.",
        ur = "سر، آپ کا اصول \"$reason\" مجھے یہاں روکتا ہے — یہ میں نہیں کروں گا۔",
        hi = "सर, आपका नियम \"$reason\" मुझे यहाँ रोकता है — यह मैं नहीं करूँगा।",
    ).pick(lang)

    private fun ruleConfirmLine(brainText: String, lang: BalochLanguage): String = L3(
        en = "Sir, your rule says I should ask first — $brainText",
        ur = "سر، آپ کے اصول کے مطابق مجھے پہلے پوچھنا چاہیے — $brainText",
        hi = "सर, आपके नियम के अनुसार मुझे पहले पूछना चाहिए — $brainText",
    ).pick(lang)

    companion object {
        /** Trilingual conjunctions: and/aur/or/phir/then + Urdu/Hindi script. */
        private val SPLIT_RX =
            Regex("""\s+(?:and|aur|or|phir|then|اور|پھر|और|फिर)\s+""")
    }
}

/**
 * Last-resort agent: claims nothing via [canHandle], so the orchestrator
 * only reaches it for actions no specialist wants. Executes straight
 * through [com.baloch.ai.engine.DeviceActions].
 */
private class FallbackAgent(private val deps: AgentDeps) : SpecialistAgent {
    override val name = "General"
    override val icon = "⚙️"
    override fun canHandle(a: DeviceAction): Boolean = false
    override suspend fun execute(t: AgentTask): AgentResult = runGuarded(t) {
        viaDeviceActions(deps, t)
    }
}
