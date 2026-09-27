package `in`.jandwar.app.ai

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class ConversationController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nluExtractor: TieredNluExtractor,
    private val bhashiniGateway: BhashiniGateway,
    private val sarvamGateway: SarvamGateway,
    private val androidTts: AndroidTtsSpeaker,
    private val speechGateway: AndroidSpeechGateway,
    private val prefs: SharedPreferences
) {

    interface Listener {
        fun onQuestion(questionText: String)
        fun onPartialTranscript(partial: String)
        fun onTranscriptResult(full: String)
        fun onFieldExtracted(updated: ProfileFragment)
        fun onDone(finalProfile: ProfileFragment)
        fun onStatus(statusText: String)
        fun onSpeaking(isSpeaking: Boolean)
    }

    private var listener: Listener? = null
    private var langCode: String = "en"
    private val profile = ProfileFragment()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stopped = false
    private var listening = false
    private var mediaPlayer: MediaPlayer? = null
    private var lastQuestion: String = ""
    private var speechRetryCount = 0
    private val maxSpeechRetries = 3

    fun init(lang: String, listener: Listener) {
        this.langCode = lang
        this.listener = listener
        profile.interests.clear()
        profile.skills.clear()
        profile.edu = null
        profile.preference = null
        profile.district = null
        profile.mobility = null
        profile.familyOccupation = null
        profile.currentLivelihood = null
        profile.physicalConstraints = null
        profile.localOpportunity = null
        profile.nextQuestion = null
        stopped = false
        listening = false
        lastQuestion = ""
        speechRetryCount = 0
        speechGateway.setLanguage(speechTag())
    }

    fun start() {
        stopped = false
        speechRetryCount = 0
        androidTts.init(langCode) {
            if (!stopped) {
                processAnswer("")
            }
        }
    }

    fun stop() {
        stopped = true
        listening = false
        speechGateway.stop()
        androidTts.stop()
        stopAudio()
    }

    fun resumeListening() {
        if (!stopped && !listening) {
            speechRetryCount = 0
            startListening()
        }
    }

    fun repeatCurrentQuestion() {
        if (lastQuestion.isNotBlank() && !stopped) {
            speechRetryCount = 0
            listener?.onQuestion(lastQuestion)
            speakThenListen(lastQuestion, true)
        }
    }

    fun processAnswer(answer: String) {
        if (stopped) return
        speechRetryCount = 0 // Reset on successful answer processing
        listener?.onStatus(thinkingText())
        listener?.onSpeaking(false)

        val isOnline = isNetworkAvailable()

        nluExtractor.extract(answer, langCode, profile, isOnline, object : NluExtractor.Callback {
            override fun onResult(fragment: ProfileFragment) {
                if (stopped) return
                profile.merge(fragment)
                listener?.onFieldExtracted(profile)

                if (profile.isComplete()) {
                    // Generate natural closing that summarizes
                    val closing = fragment.nextQuestion ?: generateClosingWithSummary()
                    lastQuestion = closing
                    listener?.onQuestion(closing)
                    speakThenListen(closing, false)
                    mainHandler.postDelayed({
                        if (!stopped) listener?.onDone(profile)
                    }, 3500)
                } else {
                    val nextQ = fragment.nextQuestion ?: generateFallbackQuestion()
                    lastQuestion = nextQ
                    listener?.onQuestion(nextQ)
                    speakThenListen(nextQ, true)
                }
            }

            override fun onError(reason: String) {
                if (stopped) return
                android.util.Log.w("ConversationController", "NLU error hidden: $reason")
                listener?.onStatus(thinkingText())
                val apology = when (langCode) {
                    "ta" -> "மன்னிக்கவும், சிறு தாமதம். மீண்டும் கேட்கிறேன்."
                    "hi" -> "थोड़ी देर, फिर से सुन रहा हूँ।"
                    "te" -> "కొంచెం ఆలస్యం, మళ్లీ వింటున్నాను."
                    "kn" -> "ಸ್ವಲ್ಪ ವಿಳಂಬ, ಮತ್ತೆ ಆಲಿಸುತ್ತಿದ್ದೇನೆ."
                    "ml" -> "ചെറിയ താമസം, വീണ്ടും കേൾക്കുന്നു."
                    else -> "Just a moment, let me listen again..."
                }
                speakThenListen(apology, true)
            }
        })
    }

    private fun generateClosingWithSummary(): String {
        val edu = profile.edu ?: "your education"
        val family = profile.familyOccupation ?: "your family background"
        val current = profile.currentLivelihood ?: "your current situation"
        val interests = if (profile.interests.isNotEmpty()) profile.interests.joinToString(", ") else "your interests"

        return when (langCode) {
            "ta" -> "நன்றி! $edu, குடும்பம் $family, ஆர்வம் $interests என்று புரிந்து கொண்டேன். உங்களுக்கான சிறந்த வாய்ப்புகளை தேடுகிறேன்."
            "hi" -> "धन्यवाद! आप $edu हैं, परिवार $family, रुचि $interests. आपके लिए बेहतरीन विकल्प ढूंढता हूँ।"
            else -> "Thank you! So you're $edu, family background $family, currently $current, interested in $interests. This really helps me find the perfect fit for you. Let me search the best options..."
        }
    }

    private fun speakThenListen(text: String, listenAfter: Boolean) {
        if (stopped) return
        if (text.isBlank()) {
            if (listenAfter) startListening()
            return
        }

        listener?.onSpeaking(true)

        val clean = formatForTts(text)
        if (clean.isBlank()) {
            if (listenAfter) startListening()
            return
        }

        val ttsEngine = prefs.getString("tts_engine", "auto") ?: "auto"

        val onDone: () -> Unit = {
            mainHandler.post {
                listener?.onSpeaking(false)
                if (!stopped && listenAfter) startListening()
            }
            Unit
        }

        when {
            ttsEngine == "sarvam" -> {
                sarvamGateway.synthesize(clean, langCode, object : SarvamGateway.Callback {
                    override fun onSuccess(audioData: ByteArray) {
                        playAudio(audioData, onDone)
                    }
                    override fun onError(error: String) {
                        androidTts.speak(clean, onDone)
                    }
                })
            }
            ttsEngine == "bhashini" || (ttsEngine == "auto" && langCode != "en" && langCode != "hi" && bhashiniGateway.isAvailable()) -> {
                bhashiniGateway.tts(clean, langCode, object : BhashiniGateway.TtsCallback {
                    override fun onAudio(audioBytes: ByteArray) {
                        playAudio(audioBytes, onDone)
                    }
                    override fun onError(reason: String) {
                        androidTts.speak(clean, onDone)
                    }
                })
            }
            else -> {
                androidTts.speak(clean, onDone)
            }
        }
    }

    private fun playAudio(bytes: ByteArray, onDone: () -> Unit) {
        try {
            val tempFile = File.createTempFile("tts", ".mp3", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(bytes) }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    stopAudio()
                    onDone()
                    tempFile.delete()
                }
                setOnErrorListener { _, _, _ ->
                    stopAudio()
                    onDone()
                    tempFile.delete()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            onDone()
        }
    }

    private fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun startListening() {
        if (stopped) return
        listening = true
        listener?.onStatus(listeningText())

        speechGateway.setListener(object : SpeechGateway.Listener {
            override fun onPartial(text: String) {
                mainHandler.post { listener?.onPartialTranscript(text) }
            }

            override fun onResult(text: String) {
                listening = false
                speechRetryCount = 0
                mainHandler.post {
                    listener?.onTranscriptResult(text)
                    processAnswer(text)
                }
            }

            override fun onError(error: String) {
                listening = false
                val lower = error.lowercase()
                val isNoMatch = lower.contains("no match") || lower.contains("no speech") || lower.contains("empty") || lower.contains("speech timeout")

                if (isNoMatch && speechRetryCount < maxSpeechRetries) {
                    speechRetryCount++
                    mainHandler.post {
                        listener?.onStatus(
                            when (langCode) {
                                "ta" -> "கேட்கவில்லை, மீண்டும் கேட்கிறேன்... ($speechRetryCount)"
                                "hi" -> "सुनाई नहीं दिया, फिर से सुन रहा हूँ... ($speechRetryCount)"
                                else -> "Didn't catch that, listening again... ($speechRetryCount/$maxSpeechRetries)"
                            }
                        )
                    }
                    mainHandler.postDelayed({
                        if (!stopped) startListening()
                    }, 800)
                } else {
                    speechRetryCount = 0
                    mainHandler.post {
                        listener?.onStatus(
                            when (langCode) {
                                "ta" -> "பேசுங்கள், கேட்கிறேன்... அல்லது தட்டச்சு செய்யுங்கள்"
                                "hi" -> "बोलिए, मैं सुन रहा हूँ... या टाइप करें"
                                "te" -> "మాట్లాడండి, వింటున్నాను... లేదా టైప్ చేయండి"
                                "kn" -> "ಮಾತನಾಡಿ, ಆಲಿಸುತ್ತಿದ್ದೇನೆ... ಅಥವಾ ಟೈಪ್ ಮಾಡಿ"
                                "ml" -> "സംസാരിക്കൂ, കേൾക്കുന്നു... അല്ലെങ്കിൽ ടൈപ്പ് ചെയ്യൂ"
                                else -> "I'm listening... speak naturally or type below"
                            }
                        )
                    }
                    // Auto-restart listening after showing message, don't require tap
                    mainHandler.postDelayed({
                        if (!stopped) startListening()
                    }, 2500)
                }
            }
        })
        speechGateway.start()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun formatForTts(text: String): String {
        var t = text.replace("*", "").replace("#", "").replace("`", "").replace("\n", " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    private fun speechTag(): String = when (langCode) {
        "ta" -> "ta-IN"
        "hi" -> "hi-IN"
        "te" -> "te-IN"
        "kn" -> "kn-IN"
        "ml" -> "ml-IN"
        else -> "en-IN"
    }

    private fun listeningText(): String = when (langCode) {
        "ta" -> "கேட்கிறேன்… இயல்பாக பேசுங்கள்"
        "hi" -> "सुन रहा हूँ… दिल खोलकर बताइए"
        "te" -> "వింటున్నాను… సహజంగా మాట్లాడండి"
        "kn" -> "ಆಲಿಸುತ್ತಿದ್ದೇನೆ… ಮುಕ್ತವಾಗಿ ಹೇಳಿ"
        "ml" -> "കേൾക്കുന്നു… മനസ്സ് തുറന്ന് പറയൂ"
        else -> "Listening... speak naturally, I'm here"
    }

    private fun thinkingText(): String = when (langCode) {
        "ta" -> "புரிந்து கொள்கிறேன்…"
        "hi" -> "समझ रहा हूँ…"
        "te" -> "అర్థం చేసుకుంటున్నాను…"
        "kn" -> "ಅರ್ಥಮಾಡಿಕೊಳ್ಳುತ್ತಿದ್ದೇನೆ…"
        "ml" -> "മനസ്സിലാക്കുന്നു…"
        else -> "Understanding you..."
    }

    private fun generateFallbackQuestion(): String = when (langCode) {
        "ta" -> "உங்கள் குடும்பத் தொழில் அல்லது தற்போதைய வேலை பற்றி சொல்ல முடியுமா?"
        "hi" -> "आपके परिवार का पारंपरिक काम या अभी आप क्या करते हैं, बता सकते हैं?"
        "te" -> "మీ కుటుంబ వృత్తి లేదా ప్రస్తుత పని గురించి చెప్పగలరా?"
        "kn" -> "ನಿಮ್ಮ ಕುಟುಂಬದ ಸಾಂಪ್ರದಾಯಿಕ ಕೆಲಸ ಅಥವಾ ಪ್ರಸ್ತುತ ಕೆಲಸದ ಬಗ್ಗೆ ಹೇಳಬಹುದೇ?"
        "ml" -> "നിങ്ങളുടെ കുടുംബത്തിന്റെ പരമ്പരാഗത ജോലി അല്ലെങ്കിൽ ഇപ്പോഴത്തെ ജോലിയെക്കുറിച്ച് പറയാമോ?"
        else -> "Could you share about your family occupation or what you do currently?"
    }
}
