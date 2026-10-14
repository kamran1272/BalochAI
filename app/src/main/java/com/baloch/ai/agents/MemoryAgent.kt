package com.baloch.ai.agents

import com.baloch.ai.brain.DeviceAction
import com.baloch.ai.brain.L3
import com.baloch.ai.brain.Replies

/**
 * Memory specialist: notes and long-term facts. Writes go straight to
 * the stores (the same calls the ViewModel's old runAction path made),
 * so behaviour is unchanged — only the routing is new.
 */
class MemoryAgent(private val deps: AgentDeps) : SpecialistAgent {
    override val name = "Memory"
    override val icon = "🧠"

    override fun canHandle(a: DeviceAction): Boolean = when (a) {
        is DeviceAction.AddNote,
        is DeviceAction.RememberFact,
        is DeviceAction.ListNotes -> true
        else -> false
    }

    override suspend fun execute(t: AgentTask): AgentResult = runGuarded(t) {
        val line: String = when (val a = t.action) {
            is DeviceAction.AddNote -> {
                deps.notes.add(a.text)
                L3("Noted, sir.", "نوٹ کر لیا، سر۔", "नोट कर लिया, सर।").pick(t.lang)
            }
            is DeviceAction.RememberFact -> {
                deps.memory.saveFact(a.fact)
                L3("I'll remember that, sir.", "یہ یاد رکھوں گا، سر۔", "यह याद रखूँगा, सर।").pick(t.lang)
            }
            is DeviceAction.ListNotes -> {
                val all = deps.notes.all()
                if (all.isEmpty()) {
                    Replies.NOTES_EMPTY.pick(t.lang)
                } else {
                    L3(
                        en = "Your notes, sir:",
                        ur = "آپ کے نوٹس، سر:",
                        hi = "आपके नोट्स, सर:",
                    ).pick(t.lang) + "\n• " + all.take(10).joinToString("\n• ")
                }
            }
            else -> L3("Done, sir.", "ہو گیا، سر۔", "हो गया, सर।").pick(t.lang)
        }
        AgentResult(name, true, line)
    }
}
