package `in`.jandwar.app.ui.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.jandwar.app.ai.ConversationController
import `in`.jandwar.app.ai.ProfileFragment
import `in`.jandwar.app.data.model.ConversationMessage
import javax.inject.Inject

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val conversationController: ConversationController
) : ViewModel() {

    var currentQuestion by mutableStateOf("")
        private set

    var transcript by mutableStateOf("")
        private set

    var status by mutableStateOf("Listening…")
        private set

    var isSpeaking by mutableStateOf(false)
        private set

    var isListening by mutableStateOf(false)
        private set

    var profile by mutableStateOf(ProfileFragment())
        private set

    var isDone by mutableStateOf(false)
        private set

    var finalProfile by mutableStateOf<ProfileFragment?>(null)
        private set

    var conversationHistory by mutableStateOf(listOf<ConversationMessage>())
        private set

    fun start(lang: String, onFieldExtracted: (ProfileFragment) -> Unit, onDone: (ProfileFragment) -> Unit) {
        isDone = false
        transcript = ""
        status = "Listening…"
        currentQuestion = "..."
        conversationHistory = emptyList()

        conversationController.init(lang, object : ConversationController.Listener {
            override fun onQuestion(questionText: String) {
                currentQuestion = questionText
                transcript = ""
                // Add assistant message to history
                conversationHistory = conversationHistory + ConversationMessage(
                    role = "assistant",
                    text = questionText
                )
            }

            override fun onPartialTranscript(partial: String) {
                transcript = partial
            }

            override fun onTranscriptResult(full: String) {
                transcript = full
                if (full.isNotBlank()) {
                    conversationHistory = conversationHistory + ConversationMessage(
                        role = "user",
                        text = full
                    )
                }
            }

            override fun onFieldExtracted(updated: ProfileFragment) {
                profile = updated
                onFieldExtracted(updated)
            }

            override fun onDone(finalProfile: ProfileFragment) {
                isDone = true
                this@VoiceViewModel.finalProfile = finalProfile
                onDone(finalProfile)
            }

            override fun onStatus(statusText: String) {
                // Pitch safety: never show technical Groq/API errors
                val lower = statusText.lowercase()
                if (lower.contains("error") && (lower.contains("groq") || lower.contains("model") || lower.contains("api") || lower.contains("http"))) {
                    status = when (lang) {
                        "ta" -> "புரிந்து கொள்கிறேன்…"
                        "hi" -> "समझ रहा हूँ…"
                        else -> "Understanding…"
                    }
                    isListening = false
                } else {
                    status = statusText
                    // Infer listening state from status text
                    isListening = lower.contains("listening") || lower.contains("கேட்கிறேன்") || lower.contains("सुन रहा") || lower.contains("speak now") || lower.contains("speak naturally")
                }
            }

            override fun onSpeaking(isSpeaking: Boolean) {
                this@VoiceViewModel.isSpeaking = isSpeaking
                if (isSpeaking) {
                    isListening = false
                }
            }
        })

        conversationController.start()
    }

    fun stop() {
        conversationController.stop()
    }

    fun resumeListening() {
        conversationController.resumeListening()
    }

    fun repeatQuestion() {
        conversationController.repeatCurrentQuestion()
    }

    fun processTypedAnswer(text: String) {
        if (text.isBlank()) return
        transcript = text
        // Add to history immediately
        conversationHistory = conversationHistory + ConversationMessage(
            role = "user",
            text = text
        )
        conversationController.processAnswer(text)
    }

    override fun onCleared() {
        super.onCleared()
        conversationController.stop()
    }
}
