package `in`.jandwar.app.ai

import android.util.Log
import `in`.jandwar.app.util.NetworkMonitor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes each utterance to the best available brain and **always** produces a
 * usable turn.
 *
 *  1. Cloud (Groq) when a key is configured and the device is online — this
 *     gives the free-flowing, genuinely conversational experience the problem
 *     statement asks for.
 *  2. On-device extraction + [InterviewFlow] script otherwise, or whenever the
 *     cloud call fails/times out.
 *
 * Crucially, the on-device path also runs on every cloud turn so that slots the
 * LLM missed (a district name, an interest keyword) are still captured, and the
 * cloud path can never regress the profile.
 */
@Singleton
class HybridNlu @Inject constructor(
    private val groq: GroqClient,
    private val onDevice: OnDeviceNlu,
    private val network: NetworkMonitor
) : NluEngine {

    private var turn = 0

    fun reset() {
        turn = 0
    }

    /** True when the richer cloud conversation is currently possible. */
    fun cloudAvailable(): Boolean = groq.isConfigured() && network.isOnline()

    override fun understand(
        utterance: String,
        lang: String,
        current: ProfileFragment,
        askedSlot: ProfileFragment.Slot?,
        callback: NluEngine.Callback
    ) {
        turn++

        // Always mine the utterance locally first — cheap, instant, and it
        // guards against the LLM dropping a field it already saw.
        val local = if (utterance.isBlank()) ProfileFragment()
        else if (askedSlot != null) onDevice.extractForSlot(utterance, lang, askedSlot)
        else onDevice.extract(utterance, lang)

        val blank = utterance.isBlank()

        if (!cloudAvailable()) {
            callback.onResult(finishLocally(local, current, lang, blank))
            return
        }

        groq.understand(utterance, lang, current, askedSlot) { cloudFrag ->
            if (cloudFrag == null) {
                Log.i(TAG, "Cloud unavailable for this turn — using on-device engine")
                callback.onResult(finishLocally(local, current, lang, blank))
                return@understand
            }

            // Merge: start from the local read, then let the cloud refine it.
            val merged = local.copyOf()
            merged.merge(cloudFrag)
            // Keep interests normalised to our fixed vocabulary.
            merged.interests = merged.interests
                .map { it.lowercase().trim() }
                .filter { it in VALID_INTERESTS }
                .distinct()
                .toMutableList()

            val projected = current.copyOf().apply { merge(merged) }
            val finished = !blank && projected.isUsable() &&
                    (cloudFrag.isComplete || projected.isFullyComplete() || turn >= MAX_TURNS)

            if (merged.nextQuestion.isNullOrBlank()) {
                merged.nextQuestion = nextScriptedLine(projected, lang, finished)
            }
            callback.onResult(NluEngine.Result(merged, NluEngine.Source.CLOUD, finished))
        }
    }

    // ── On-device turn construction ─────────────────────────────────────────

    private fun finishLocally(
        local: ProfileFragment,
        current: ProfileFragment,
        lang: String,
        utteranceWasEmpty: Boolean
    ): NluEngine.Result {
        val projected = current.copyOf().apply { merge(local) }
        val gainedSomething = local.filledSlotCount() > 0
        val finished = !utteranceWasEmpty && projected.isUsable() &&
                (projected.isFullyComplete() || turn >= MAX_TURNS)

        local.nextQuestion = when {
            finished -> InterviewFlow.closing(lang)
            turn == 1 && utteranceWasEmpty -> {
                val first = projected.missingSlots().firstOrNull()
                val greet = InterviewFlow.greeting(lang, turn)
                if (first == null) greet
                else "$greet ${InterviewFlow.question(first, lang, turn)}"
            }

            !gainedSomething -> InterviewFlow.reprompt(lang, turn)
            else -> {
                val ack = InterviewFlow.acknowledgement(lang, turn)
                val slot = projected.missingSlots().firstOrNull()
                if (slot == null) InterviewFlow.closing(lang)
                else "$ack ${InterviewFlow.question(slot, lang, turn)}"
            }
        }
        return NluEngine.Result(local, NluEngine.Source.ON_DEVICE, finished)
    }

    private fun nextScriptedLine(
        projected: ProfileFragment,
        lang: String,
        finished: Boolean
    ): String {
        if (finished) return InterviewFlow.closing(lang)
        val slot = projected.missingSlots().firstOrNull() ?: return InterviewFlow.closing(lang)
        return InterviewFlow.question(slot, lang, turn)
    }

    /** The slot the assistant is expecting an answer to next. */
    fun expectedSlot(current: ProfileFragment): ProfileFragment.Slot? =
        current.missingSlots().firstOrNull()

    companion object {
        private const val TAG = "HybridNlu"

        /** Safety valve so an offline interview can never loop forever. */
        private const val MAX_TURNS = 12

        private val VALID_INTERESTS = setOf(
            "dairy", "cattle", "goat", "poultry", "farming",
            "food", "machine", "textile", "construction", "tailor"
        )
    }
}
