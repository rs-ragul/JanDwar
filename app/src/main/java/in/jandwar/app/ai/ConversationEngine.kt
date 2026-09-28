package `in`.jandwar.app.ai

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the whole spoken interview: speak → listen → understand → speak again.
 *
 * Design rules that come straight from the problem statement:
 *  - It is a *conversation*, not a form. Every turn acknowledges the answer.
 *  - It must never dead-end. Mic missing, permission denied, offline, no API
 *    key — each of those degrades to something the user can still finish
 *    (typing, or the scripted on-device interview).
 *  - The user can always take over by typing, and can always stop early once
 *    enough is known.
 */
@Singleton
class ConversationEngine @Inject constructor(
    private val tts: TtsSpeaker,
    private val voice: VoiceListener,
    private val nlu: HybridNlu
) {

    enum class Phase {
        /** Nothing running yet. */
        IDLE,

        /** Assistant is speaking. */
        SPEAKING,

        /** Microphone is open. */
        LISTENING,

        /** Waiting on the NLU. */
        THINKING,

        /** Interview complete — profile ready for matching. */
        DONE
    }

    enum class InputMode { VOICE, TEXT }

    data class Turn(val fromUser: Boolean, val text: String)

    data class State(
        val phase: Phase = Phase.IDLE,
        val turns: List<Turn> = emptyList(),
        val partial: String = "",
        val amplitude: Float = 0f,
        val profile: ProfileFragment = ProfileFragment(),
        val progress: Float = 0f,
        val inputMode: InputMode = InputMode.VOICE,
        val usingCloud: Boolean = false,
        /** Localised, non-fatal hint (mic unavailable, offline, …). */
        val notice: String? = null,
        /** True once enough is known to run matching even if not finished. */
        val canFinishEarly: Boolean = false,
        val micPermissionNeeded: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Invoked once when the interview completes, on the main thread. */
    var onComplete: ((ProfileFragment) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var lang = "en"
    private var askedSlot: ProfileFragment.Slot? = null
    private var consecutiveFailures = 0
    private var started = false
    private var stopped = false

    // ── Lifecycle ───────────────────────────────────────────────────────────

    fun start(langCode: String, micGranted: Boolean) {
        if (started) return
        started = true
        stopped = false
        lang = langCode
        nlu.reset()
        consecutiveFailures = 0
        askedSlot = null

        val voiceOk = micGranted && voice.isAvailable()
        _state.value = State(
            phase = Phase.SPEAKING,
            inputMode = if (voiceOk) InputMode.VOICE else InputMode.TEXT,
            usingCloud = nlu.cloudAvailable(),
            micPermissionNeeded = !micGranted && voice.isAvailable(),
            notice = when {
                !voice.isAvailable() -> notice("mic_missing")
                !micGranted -> notice("mic_denied")
                else -> null
            }
        )

        voice.setLanguage(InterviewFlow.bcp47(lang))
        voice.setListener(voiceCallbacks)

        tts.init(lang) { ok ->
            if (!ok) {
                Log.i(TAG, "No TTS voice for $lang — showing text only")
                update { it.copy(notice = it.notice ?: notice("tts_missing")) }
            }
            // Empty utterance = "open the conversation".
            think("")
        }
    }

    fun stop() {
        stopped = true
        started = false
        tts.stop()
        voice.cancel()
        update { it.copy(phase = Phase.IDLE, amplitude = 0f, partial = "") }
    }

    fun release() {
        stop()
        voice.release()
        tts.shutdown()
        onComplete = null
    }

    fun reset() {
        stop()
        _state.value = State()
    }

    // ── User actions ────────────────────────────────────────────────────────

    /** User tapped the mic to answer now. */
    fun listenNow() {
        if (stopped) return
        tts.stop()
        beginListening()
    }

    /** User typed instead of speaking. */
    fun submitText(text: String) {
        val t = text.trim()
        if (t.isBlank() || stopped) return
        tts.stop()
        voice.cancel()
        addTurn(Turn(fromUser = true, text = t))
        think(t)
    }

    fun setInputMode(mode: InputMode) {
        update { it.copy(inputMode = mode) }
        if (mode == InputMode.TEXT) {
            voice.cancel()
            update { it.copy(amplitude = 0f, partial = "") }
        }
    }

    /** User is happy to stop and see results now. */
    fun finishEarly() {
        val p = _state.value.profile
        if (!p.isUsable()) return
        tts.stop()
        voice.cancel()
        complete(p)
    }

    /** Re-speak the assistant's last line. */
    fun repeatLast() {
        val last = _state.value.turns.lastOrNull { !it.fromUser } ?: return
        speakThenListen(last.text, alreadyShown = true)
    }

    fun onMicPermissionResult(granted: Boolean) {
        update {
            it.copy(
                micPermissionNeeded = false,
                inputMode = if (granted) InputMode.VOICE else InputMode.TEXT,
                notice = if (granted) null else notice("mic_denied")
            )
        }
        if (granted && _state.value.phase == Phase.IDLE) beginListening()
    }

    // ── Core loop ───────────────────────────────────────────────────────────

    private fun think(utterance: String) {
        if (stopped) return
        update { it.copy(phase = Phase.THINKING, partial = "", amplitude = 0f) }

        val snapshot = _state.value.profile.copyOf()
        nlu.understand(utterance, lang, snapshot, askedSlot) { result ->
            if (stopped) return@understand

            val updated = _state.value.profile.copyOf().apply { merge(result.fragment) }
            askedSlot = updated.missingSlots().firstOrNull()

            update {
                it.copy(
                    profile = updated,
                    progress = updated.filledSlotCount().toFloat() /
                            ProfileFragment.TOTAL_SLOTS.toFloat(),
                    usingCloud = result.source == NluEngine.Source.CLOUD,
                    canFinishEarly = updated.isUsable()
                )
            }

            val line = result.fragment.nextQuestion?.takeIf { it.isNotBlank() }
                ?: if (result.finished) InterviewFlow.closing(lang)
                else InterviewFlow.reprompt(lang, 0)

            if (result.finished) {
                addTurn(Turn(fromUser = false, text = line))
                update { it.copy(phase = Phase.SPEAKING) }
                tts.speak(line) { main.post { complete(updated) } }
            } else {
                speakThenListen(line)
            }
        }
    }

    private fun speakThenListen(line: String, alreadyShown: Boolean = false) {
        if (stopped) return
        if (!alreadyShown) addTurn(Turn(fromUser = false, text = line))
        update { it.copy(phase = Phase.SPEAKING, partial = "") }
        tts.speak(line) {
            main.post {
                if (stopped) return@post
                if (_state.value.inputMode == InputMode.VOICE) beginListening()
                else update { it.copy(phase = Phase.IDLE) }
            }
        }
    }

    private fun beginListening() {
        if (stopped) return
        if (_state.value.inputMode != InputMode.VOICE) {
            update { it.copy(phase = Phase.IDLE) }
            return
        }
        update { it.copy(phase = Phase.LISTENING, partial = "") }
        voice.start()
    }

    private fun complete(profile: ProfileFragment) {
        if (_state.value.phase == Phase.DONE) return
        update { it.copy(phase = Phase.DONE, amplitude = 0f, partial = "") }
        onComplete?.invoke(profile)
    }

    // ── Speech callbacks ────────────────────────────────────────────────────

    private val voiceCallbacks = object : VoiceListener.Listener {
        override fun onReady() {
            update { it.copy(phase = Phase.LISTENING) }
        }

        override fun onAmplitude(level: Float) {
            update { it.copy(amplitude = level) }
        }

        override fun onPartial(text: String) {
            update { it.copy(partial = text) }
        }

        override fun onFinal(text: String) {
            consecutiveFailures = 0
            addTurn(Turn(fromUser = true, text = text))
            think(text)
        }

        override fun onProblem(problem: VoiceListener.Problem) {
            if (stopped) return
            when (problem) {
                VoiceListener.Problem.PERMISSION -> {
                    update {
                        it.copy(
                            phase = Phase.IDLE,
                            inputMode = InputMode.TEXT,
                            micPermissionNeeded = true,
                            notice = notice("mic_denied")
                        )
                    }
                }

                VoiceListener.Problem.UNAVAILABLE -> {
                    update {
                        it.copy(
                            phase = Phase.IDLE,
                            inputMode = InputMode.TEXT,
                            notice = notice("mic_missing")
                        )
                    }
                }

                VoiceListener.Problem.SILENCE,
                VoiceListener.Problem.UNCLEAR,
                VoiceListener.Problem.OFFLINE,
                VoiceListener.Problem.OTHER -> handleRetry(problem)
            }
        }
    }

    private fun handleRetry(problem: VoiceListener.Problem) {
        consecutiveFailures++
        when {
            consecutiveFailures == 1 -> {
                // Just listen again quietly — most first failures are a pause.
                update { it.copy(phase = Phase.IDLE) }
                main.postDelayed({ if (!stopped) beginListening() }, 350)
            }

            consecutiveFailures == 2 -> {
                val line = InterviewFlow.reprompt(lang, consecutiveFailures)
                speakThenListen(line)
            }

            else -> {
                // Stop fighting the microphone; offer typing.
                update {
                    it.copy(
                        phase = Phase.IDLE,
                        inputMode = InputMode.TEXT,
                        notice = notice(
                            if (problem == VoiceListener.Problem.OFFLINE) "asr_offline"
                            else "asr_hard"
                        )
                    )
                }
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun addTurn(turn: Turn) {
        update { it.copy(turns = it.turns + turn) }
    }

    private inline fun update(block: (State) -> State) {
        val next = block(_state.value)
        if (next != _state.value) _state.value = next
    }

    private fun notice(kind: String): String {
        val table = NOTICES[kind] ?: return ""
        return table[lang] ?: table["en"] ?: ""
    }

    companion object {
        private const val TAG = "ConversationEngine"

        private val NOTICES: Map<String, Map<String, String>> = mapOf(
            "mic_missing" to mapOf(
                "en" to "Voice input is not available on this phone. You can type your answers.",
                "ta" to "இந்த மொபைலில் குரல் வசதி இல்லை. பதில்களை தட்டச்சு செய்யலாம்.",
                "hi" to "इस फ़ोन में आवाज़ सुविधा नहीं है। आप जवाब टाइप कर सकते हैं।",
                "te" to "ఈ ఫోన్‌లో వాయిస్ సౌకర్యం లేదు. మీరు టైప్ చేయవచ్చు.",
                "kn" to "ಈ ಫೋನ್‌ನಲ್ಲಿ ಧ್ವನಿ ಸೌಲಭ್ಯವಿಲ್ಲ. ನೀವು ಟೈಪ್ ಮಾಡಬಹುದು.",
                "ml" to "ഈ ഫോണിൽ ശബ്ദ സൗകര്യം ഇല്ല. നിങ്ങൾക്ക് ടൈപ്പ് ചെയ്യാം."
            ),
            "mic_denied" to mapOf(
                "en" to "Microphone permission is off. Allow it to speak, or type your answers.",
                "ta" to "மைக்ரோஃபோன் அனுமதி இல்லை. அனுமதி கொடுங்கள், அல்லது தட்டச்சு செய்யுங்கள்.",
                "hi" to "माइक की अनुमति बंद है। अनुमति दें, या टाइप करें।",
                "te" to "మైక్ అనుమతి లేదు. అనుమతి ఇవ్వండి, లేదా టైప్ చేయండి.",
                "kn" to "ಮೈಕ್ ಅನುಮತಿ ಇಲ್ಲ. ಅನುಮತಿ ನೀಡಿ, ಅಥವಾ ಟೈಪ್ ಮಾಡಿ.",
                "ml" to "മൈക്ക് അനുമതി ഇല്ല. അനുമതി നൽകൂ, അല്ലെങ്കിൽ ടൈപ്പ് ചെയ്യൂ."
            ),
            "tts_missing" to mapOf(
                "en" to "Voice playback is unavailable, but you can read each question on screen.",
                "ta" to "குரல் ஒலிக்கவில்லை, ஆனால் கேள்விகளை திரையில் படிக்கலாம்.",
                "hi" to "आवाज़ नहीं चल रही, पर सवाल स्क्रीन पर पढ़ सकते हैं।",
                "te" to "వాయిస్ వినిపించడం లేదు, కానీ ప్రశ్నలు స్క్రీన్‌లో చదవవచ్చు.",
                "kn" to "ಧ್ವನಿ ಕೇಳಿಸುತ್ತಿಲ್ಲ, ಆದರೆ ಪ್ರಶ್ನೆಗಳನ್ನು ಪರದೆಯಲ್ಲಿ ಓದಬಹುದು.",
                "ml" to "ശബ്ദം കേൾക്കുന്നില്ല, പക്ഷേ ചോദ്യങ്ങൾ സ്ക്രീനിൽ വായിക്കാം."
            ),
            "asr_offline" to mapOf(
                "en" to "Voice needs a connection right now. Please type your answer.",
                "ta" to "இப்போது குரலுக்கு இணைப்பு தேவை. பதிலை தட்டச்சு செய்யுங்கள்.",
                "hi" to "अभी आवाज़ के लिए इंटरनेट चाहिए। कृपया टाइप करें।",
                "te" to "ఇప్పుడు వాయిస్‌కు ఇంటర్నెట్ కావాలి. దయచేసి టైప్ చేయండి.",
                "kn" to "ಈಗ ಧ್ವನಿಗೆ ಇಂಟರ್ನೆಟ್ ಬೇಕು. ದಯವಿಟ್ಟು ಟೈಪ್ ಮಾಡಿ.",
                "ml" to "ഇപ്പോൾ ശബ്ദത്തിന് ഇന്റർനെറ്റ് വേണം. ദയവായി ടൈപ്പ് ചെയ്യൂ."
            ),
            "asr_hard" to mapOf(
                "en" to "I am having trouble hearing you. Please type your answer instead.",
                "ta" to "உங்கள் குரல் சரியாக கேட்கவில்லை. பதிலை தட்டச்சு செய்யுங்கள்.",
                "hi" to "आपकी आवाज़ ठीक से नहीं सुनाई दे रही। कृपया टाइप करें।",
                "te" to "మీ గొంతు సరిగ్గా వినిపించడం లేదు. దయచేసి టైప్ చేయండి.",
                "kn" to "ನಿಮ್ಮ ಧ್ವನಿ ಸರಿಯಾಗಿ ಕೇಳಿಸುತ್ತಿಲ್ಲ. ದಯವಿಟ್ಟು ಟೈಪ್ ಮಾಡಿ.",
                "ml" to "നിങ്ങളുടെ ശബ്ദം ശരിയായി കേൾക്കുന്നില്ല. ദയവായി ടൈപ്പ് ചെയ്യൂ."
            )
        )
    }
}
