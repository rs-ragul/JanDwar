package `in`.jandwar.app.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around Android's on-device/offline-capable [SpeechRecognizer].
 *
 * Reports failures as typed [Problem]s rather than raw error codes so the
 * conversation layer can decide whether to re-prompt, switch to typing, or
 * tell the user the mic is blocked.
 */
@Singleton
class VoiceListener @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class Problem {
        /** Nothing was heard — just ask again. */
        SILENCE,

        /** Heard something but could not decode it. */
        UNCLEAR,

        /** RECORD_AUDIO not granted. */
        PERMISSION,

        /** No recognition service on this device. */
        UNAVAILABLE,

        /** Recogniser needs the network and there is none. */
        OFFLINE,

        /** Anything else. */
        OTHER
    }

    interface Listener {
        fun onReady()
        fun onAmplitude(level: Float)
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onProblem(problem: Problem)
    }

    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private var bcp47 = "en-IN"
    private var listening = false
    private var lastStart = 0L
    private val main = Handler(Looper.getMainLooper())

    fun isAvailable(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(context)
    } catch (e: Exception) {
        false
    }

    fun setLanguage(tag: String) {
        bcp47 = tag
    }

    fun setListener(l: Listener?) {
        listener = l
    }

    fun start() {
        val now = System.currentTimeMillis()
        if (now - lastStart < 600) return
        lastStart = now

        if (!isAvailable()) {
            listener?.onProblem(Problem.UNAVAILABLE)
            return
        }
        stopInternal()

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
                startListening(buildIntent())
            }
            listening = true
        } catch (e: Exception) {
            Log.e(TAG, "start failed: ${e.message}")
            listening = false
            listener?.onProblem(Problem.OTHER)
        }
    }

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, bcp47)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, bcp47)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Villagers often pause mid-sentence; be patient.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                2200L
            )
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listening = true
            listener?.onReady()
        }

        override fun onBeginningOfSpeech() {
            listening = true
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Map roughly -2..10 dB onto 0..1 for the waveform animation.
            val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            listener?.onAmplitude(level)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            listening = false
            listener?.onAmplitude(0f)
        }

        override fun onError(error: Int) {
            listening = false
            listener?.onAmplitude(0f)
            val problem = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> Problem.UNCLEAR
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Problem.SILENCE
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Problem.PERMISSION
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Problem.OFFLINE

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> Problem.OTHER

                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_SERVER -> Problem.OTHER

                else -> Problem.OTHER
            }
            Log.d(TAG, "error $error -> $problem")
            main.post { listener?.onProblem(problem) }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            listener?.onAmplitude(0f)
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty()
            main.post {
                if (text.isBlank()) listener?.onProblem(Problem.UNCLEAR)
                else listener?.onFinal(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty()
            if (text.isNotBlank()) main.post { listener?.onPartial(text) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    fun stop() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        listening = false
    }

    fun cancel() = stopInternal()

    private fun stopInternal() {
        try {
            recognizer?.let {
                runCatching { it.cancel() }
                runCatching { it.destroy() }
            }
        } catch (_: Exception) {
        }
        recognizer = null
        listening = false
    }

    fun release() {
        listener = null
        stopInternal()
    }

    companion object {
        private const val TAG = "VoiceListener"
    }
}
