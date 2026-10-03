package `in`.jandwar.app.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delegates every utterance to the on-device engine.
 *
 * The cloud path (Groq) has been removed. The deterministic on-device NLU
 * handles the full interview in six languages with no key and no network.
 * This class is kept as a thin wrapper so that [ConversationEngine] and
 * the DI graph remain unchanged.
 */
@Singleton
class HybridNlu @Inject constructor(
    private val onDevice: OnDeviceNlu
) : NluEngine {

    private var turn = 0

    fun reset() {
        turn = 0
    }

    /** Always false — the cloud path is no longer available. */
    fun cloudAvailable(): Boolean = false

    override fun understand(
        utterance: String,
        lang: String,
        current: ProfileFragment,
        askedSlot: ProfileFragment.Slot?,
        callback: NluEngine.Callback
    ) {
        turn++

        val local = if (utterance.isBlank()) ProfileFragment()
        else if (askedSlot != null) onDevice.extractForSlot(utterance, lang, askedSlot)
        else onDevice.extract(utterance, lang)

        // A district answer is read inside the state the person already gave,
        // so "Tiruvallur" cannot resolve against another state's list and a
        // bare district name is matched against 14-75 candidates, not 187.
        val knownState = (local.state ?: current.state).orEmpty()
        if (askedSlot == ProfileFragment.Slot.DISTRICT && knownState.isNotBlank() &&
            local.district.isNullOrBlank()
        ) {
            onDevice.extractDistrictIn(utterance, knownState)?.let { local.district = it }
        }
        // Never let a district from one state sit under another state's name.
        local.district?.let { d ->
            val owner = onDevice.stateOfDistrict(d)
            if (owner != null && knownState.isNotBlank() && !owner.equals(knownState, true)) {
                if (local.state.isNullOrBlank()) local.district = null else local.state = owner
            }
        }

        val blank = utterance.isBlank()
        callback.onResult(finishLocally(local, current, lang, blank))
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
                else "$greet ${InterviewFlow.question(first, lang, turn, projected.state)}"
            }

            !gainedSomething -> InterviewFlow.reprompt(lang, turn)
            else -> {
                val ack = InterviewFlow.acknowledgement(lang, turn)
                val slot = projected.missingSlots().firstOrNull()
                if (slot == null) InterviewFlow.closing(lang)
                else "$ack ${InterviewFlow.question(slot, lang, turn, projected.state)}"
            }
        }
        return NluEngine.Result(local, NluEngine.Source.ON_DEVICE, finished)
    }

    /** The slot the assistant is expecting an answer to next. */
    fun expectedSlot(current: ProfileFragment): ProfileFragment.Slot? =
        current.missingSlots().firstOrNull()

    companion object {
        private const val TAG = "HybridNlu"

        /**
         * Safety valve so an offline interview can never loop forever.
         * Raised from 12 when the state question made it nine slots.
         */
        private const val MAX_TURNS = 14
    }
}