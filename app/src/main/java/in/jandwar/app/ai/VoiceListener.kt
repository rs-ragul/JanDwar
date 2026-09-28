package `in`.jandwar.app.ai

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
 * Wrapper around Android's [SpeechRecognizer].
 *
 * Reports failures as typed [Problem]s rather than raw error codes so the
 * conversation layer can decide whether to re-prompt, switch to typing, or
 * tell the user the mic is blocked.
 *
 * Two hard-won rules are encoded here:
 *
 *  1. **Never force `EXTRA_PREFER_OFFLINE`.** Most phones ship the offline
 *     pack for English only. Forcing offline made Tamil / Hindi / Telugu /
 *     Kannada / Malayalam fail instantly — the mic appeared to switch on and
 *     then straight back off. Offline is now requested only when there is no
 *     network to fall back on.
 *  2. **A dropped `start()` must be rescheduled, not swallowed.** The old
 *     de-bounce silently returned, which left the UI showing "Listening"
 *     while nothing was actually recording.
 */
@Singleton
class VoiceListener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val earcon: MicEarcon
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

        /** No speech model for the selected language on this device. */
        LANGUAGE,

        /** Anything else. */
        OTHER
    }

    interface Listener {
        fun onReady()
        fun onAmplitude(level: Float)
        fun onPartial(text: String)
        fun onFinal(text: String)

        /** Mic is definitively closed — the UI must stop saying "Listening". */
        fun onMicClosed()
        fun onProblem(problem: Problem)
    }

    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private var bcp47 = "en-IN"
    private var lastStart = 0L
    private var pendingStart: Runnable? = null
    private val main = Handler(Looper.getMainLooper())

    /** True between onReadyForSpeech and the close of that recognition turn. */
    @Volatile
    var micLive = false
        private set

    fun isAvailable(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(context)
    } catch (e: Exception) {
        false
    }

    fun setLanguage(tag: String) {
        bcp47 = tag
    }

    private fun hasNetwork(): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) {
        false
    }

    fun setListener(l: Listener?) {
        listener = l
    }

    fun start() {
        // Cancel any start we had already queued so we never stack two.
        pendingStart?.let { main.removeCallbacks(it) }
        pendingStart = null

        val since = System.currentTimeMillis() - lastStart
        if (since < MIN_GAP_MS) {
            // Too soon after the previous session — the recogniser would throw
            // ERROR_RECOGNIZER_BUSY. Re-schedule instead of dropping it.
            val r = Runnable {
                pendingStart = null
                startNow()
            }
            pendingStart = r
            main.postDelayed(r, MIN_GAP_MS - since)
            return
        }
        startNow()
    }

    private fun startNow() {
        lastStart = System.currentTimeMillis()

        if (!isAvailable()) {
            micLive = false
            listener?.onProblem(Problem.UNAVAILABLE)
            return
        }
        stopInternal()

        // Our own soft cue plays first, in the clear; the platform's harsh
        // start/stop beeps are muted for the duration of the session.
        if (earcon.enabled) {
            earcon.playOpen()
            main.postDelayed({ openRecogniser() }, CUE_MS)
        } else {
            openRecogniser()
        }
    }

    private fun openRecogniser() {
        try {
            earcon.muteSystem()
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
                startListening(buildIntent())
            }
        } catch (e: Exception) {
            Log.e(TAG, "start failed: ${e.message}")
            earcon.unmuteSystem()
            micLive = false
            listener?.onMicClosed()
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
            // Only ask for offline when there is no connection anyway. Forcing
            // it is what broke every non-English language.
            if (!hasNetwork()) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

    private fun closeMic() {
        val wasLive = micLive
        micLive = false
        earcon.unmuteSystem()
        if (wasLive) {
            earcon.playClose()
            listener?.onMicClosed()
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            micLive = true
            listener?.onReady()
        }

        override fun onBeginningOfSpeech() {
            micLive = true
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Map roughly -2..10 dB onto 0..1 for the waveform animation.
            val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            listener?.onAmplitude(level)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            listener?.onAmplitude(0f)
            closeMic()
        }

        override fun onError(error: Int) {
            listener?.onAmplitude(0f)
            closeMic()
            val problem = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> Problem.UNCLEAR
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Problem.SILENCE
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Problem.PERMISSION
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Problem.OFFLINE

                // API 33+ language codes, referenced numerically so the app
                // still compiles and runs on older platforms.
                ERROR_LANGUAGE_NOT_SUPPORTED,
                ERROR_LANGUAGE_UNAVAILABLE -> Problem.LANGUAGE

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> Problem.OTHER

                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_SERVER -> Problem.OTHER

                else -> Problem.OTHER
            }
            Log.d(TAG, "error $error -> $problem (lang=$bcp47)")
            main.post { listener?.onProblem(problem) }
        }

        override fun onResults(results: Bundle?) {
            listener?.onAmplitude(0f)
            closeMic()
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
        closeMic()
    }

    fun cancel() = stopInternal()

    private fun stopInternal() {
        pendingStart?.let { main.removeCallbacks(it) }
        pendingStart = null
        earcon.unmuteSystem()
        try {
            recognizer?.let {
                runCatching { it.cancel() }
                runCatching { it.destroy() }
            }
        } catch (_: Exception) {
        }
        recognizer = null
        closeMic()
    }

    fun release() {
        listener = null
        stopInternal()
    }

    companion object {
        private const val TAG = "VoiceListener"

        /** Minimum gap between two recognition sessions. */
        private const val MIN_GAP_MS = 600L

        /** Time for the soft "your turn" cue to finish before the mic opens. */
        private const val CUE_MS = 130L

        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / _UNAVAILABLE are
        // API 33; the constants are stable, so use the literals to keep
        // minSdk 24 working.
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
