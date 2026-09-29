package `in`.jandwar.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.jandwar.app.ai.ConversationEngine
import `in`.jandwar.app.ai.GroqClient
import `in`.jandwar.app.ai.InterviewFlow
import `in`.jandwar.app.ai.ProfileFragment
import `in`.jandwar.app.data.model.MatchedRole
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin presentation layer over [ConversationEngine].
 *
 * All conversation state lives in the engine; this view model only owns what
 * happens *after* the interview: narrating the recommendations and telling the
 * screen when to navigate.
 */
@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val engine: ConversationEngine,
    private val groq: GroqClient
) : ViewModel() {

    val state: StateFlow<ConversationEngine.State> = engine.state

    /** Emitted when results are ready and the screen should navigate. */
    var navigateToResults by mutableStateOf(false)
        private set

    var narration by mutableStateOf("")
        private set

    var isNarrating by mutableStateOf(false)
        private set

    private var lang = "en"
    private var completionHandler: ((ProfileFragment) -> Unit)? = null

    /**
     * @param onProfileReady invoked with the finished profile; the caller runs
     *                       matching and hands results back via [narrateResults].
     */
    fun start(
        langCode: String,
        micGranted: Boolean,
        onProfileReady: (ProfileFragment) -> Unit
    ) {
        lang = langCode
        completionHandler = onProfileReady
        navigateToResults = false
        narration = ""
        isNarrating = false
        engine.onComplete = { profile -> completionHandler?.invoke(profile) }
        engine.start(langCode, micGranted)
    }

    fun restart(langCode: String, micGranted: Boolean) {
        engine.reset()
        engine.onComplete = { profile -> completionHandler?.invoke(profile) }
        engine.start(langCode, micGranted)
    }

    // ── User actions ────────────────────────────────────────────────────────

    fun listenNow() = engine.listenNow()
    fun submitText(text: String) = engine.submitText(text)
    fun repeatLast() = engine.repeatLast()
    fun finishEarly() = engine.finishEarly()
    fun stop() = engine.stop()

    fun useVoiceInput() = engine.setInputMode(ConversationEngine.InputMode.VOICE)
    fun useTextInput() = engine.setInputMode(ConversationEngine.InputMode.TEXT)

    fun onMicPermissionResult(granted: Boolean) = engine.onMicPermissionResult(granted)

    // ── Result narration ────────────────────────────────────────────────────

    /**
     * Speaks a short, warm summary of the matches. Uses the LLM when it is
     * available, otherwise a clean template built from the match data itself.
     * Always calls back so navigation can never stall.
     */
    fun narrateResults(
        results: List<MatchedRole>,
        profileSummary: String,
        onFinished: () -> Unit
    ) {
        if (results.isEmpty()) {
            val line = InterviewFlow.noResult(lang)
            narration = line
            speakAndFinish(line, onFinished)
            return
        }

        val intro = InterviewFlow.resultIntro(lang, results.size)
        val lines = results.take(3).map { m ->
            buildString {
                append(m.role.job_role)
                append(" (NSQF ").append(m.role.nsqf_level.ifBlank { "-" }).append(", ")
                append(m.role.durationLabel().ifBlank { "-" }).append(")")
                if (m.reason.isNotBlank()) append(" — ").append(m.reason)
                m.centre?.let { append(" — centre: ").append(it.name).append(", ").append(it.district) }
            }
        }

        isNarrating = true
        groq.explainResults(lang, profileSummary, lines) { llm ->
            val text = llm ?: buildTemplateNarration(intro, results)
            narration = text
            isNarrating = false
            speakAndFinish(text, onFinished)
        }
    }

    /** User does not want to wait for the summary to be read out. */
    fun skipNarration() {
        engine.stopNarration()
        navigateToResults = true
    }

    private fun buildTemplateNarration(intro: String, results: List<MatchedRole>): String =
        buildString {
            append(intro).append(' ')
            results.take(3).forEach { m ->
                append(m.role.job_role).append(". ")
                if (m.reason.isNotBlank()) append(m.reason).append(". ")
                m.centre?.let { append(it.name).append(", ").append(it.district).append(". ") }
            }
            append(InterviewFlow.resultOutro(lang))
        }

    /**
     * Hands the summary to the engine so it lands in the transcript as a
     * normal assistant turn — the user reads it on screen while it is spoken,
     * and only then does the app move to the results page.
     */
    private fun speakAndFinish(text: String, onFinished: () -> Unit) {
        engine.narrate(text) {
            viewModelScope.launch {
                navigateToResults = true
                onFinished()
            }
        }
    }

    fun consumeNavigation() {
        navigateToResults = false
    }

    override fun onCleared() {
        super.onCleared()
        engine.stop()
    }
}
