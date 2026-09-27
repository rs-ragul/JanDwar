package `in`.jandwar.app.ai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidSpeechGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechGateway {

    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: SpeechGateway.Listener? = null
    private var langTag: String = "en-IN"
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastStartTime = 0L

    fun setLanguage(bcp47: String) {
        langTag = bcp47
        Log.d("SpeechGateway", "Language set to: $bcp47")
    }

    override fun setListener(listener: SpeechGateway.Listener) {
        this.listener = listener
    }

    override fun start() {
        val now = System.currentTimeMillis()
        if (now - lastStartTime < 800) {
            Log.d("SpeechGateway", "Throttling start - too frequent")
            return
        }
        lastStartTime = now

        if (isListening) {
            Log.d("SpeechGateway", "Already listening, ignoring start")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("SpeechGateway", "Speech recognition not available")
            listener?.onError("Speech recognition not available on this device. Please type instead.")
            return
        }

        try {
            speechRecognizer?.apply {
                try { stopListening() } catch (_: Exception) {}
                try { cancel() } catch (_: Exception) {}
                try { destroy() } catch (_: Exception) {}
            }
            speechRecognizer = null
        } catch (_: Exception) {}

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("SpeechGateway", "Ready for speech: $langTag")
                        isListening = true
                    }
                    override fun onBeginningOfSpeech() {
                        Log.d("SpeechGateway", "Beginning of speech")
                        isListening = true
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        Log.d("SpeechGateway", "End of speech - waiting for results")
                        isListening = false
                    }
                    override fun onError(error: Int) {
                        val wasListening = isListening
                        isListening = false
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech"
                            SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission error"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            else -> "Error $error"
                        }
                        Log.w("SpeechGateway", "Speech error $error: $msg for lang $langTag, wasListening=$wasListening")
                        // Don't call onError if we already got beginning of speech and it's just timeout - let it retry silently
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            listener?.onError(msg)
                        } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                            // Busy - retry after short delay
                            mainHandler.postDelayed({
                                listener?.onError("Recognizer busy - retrying")
                            }, 500)
                        } else {
                            listener?.onError(msg)
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        Log.d("SpeechGateway", "Results: $matches, scores: ${scores?.joinToString()}")
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].trim()
                            // ACCEPT even low confidence if text is meaningful - don't reject
                            if (text.length >= 2) {
                                if (scores != null && scores.isNotEmpty()) {
                                    Log.d("SpeechGateway", "Accepting result with confidence ${scores[0]}: $text")
                                }
                                listener?.onResult(text)
                            } else {
                                Log.w("SpeechGateway", "Result too short: $text")
                                listener?.onError("Empty result")
                            }
                        } else {
                            Log.w("SpeechGateway", "No matches in results")
                            listener?.onError("Empty result")
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val partial = matches[0].trim()
                            if (partial.length >= 2) {
                                listener?.onPartial(partial)
                            }
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                // Don't force offline - allow online for better accuracy in other languages
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                }
            }

            Log.d("SpeechGateway", "Starting listening with lang: $langTag")
            isListening = true
            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            Log.e("SpeechGateway", "Start failed: ${e.message}", e)
            isListening = false
            listener?.onError("Failed to start mic: ${e.message}. Please type instead.")
        }
    }

    override fun stop() {
        try {
            isListening = false
            speechRecognizer?.apply {
                try { stopListening() } catch (_: Exception) {}
                try { cancel() } catch (_: Exception) {}
                try { destroy() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w("SpeechGateway", "Stop failed: ${e.message}")
        }
        speechRecognizer = null
    }

    fun isCurrentlyListening(): Boolean = isListening
}
