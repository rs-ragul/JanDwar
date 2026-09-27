package `in`.jandwar.app.ai

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.min

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
        fun onResultExplanation(text: String)
        fun onListening(isListening: Boolean)
    }

    private var listener: Listener? = null
    private var langCode: String = "en"
    private val profile = ProfileFragment()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stopped = false
    private var listening = false
    private var mediaPlayer: MediaPlayer? = null
    private var lastQuestion: String = ""
    private var lastSpokenText: String = ""
    private var speechRetryCount = 0
    private val maxSpeechRetries = 2
    private var isSpeakingResult = false
    private var isSpeaking = false
    private var lastSpeechResultTime = 0L

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
        isSpeaking = false
        lastQuestion = ""
        lastSpokenText = ""
        speechRetryCount = 0
        isSpeakingResult = false
        lastSpeechResultTime = 0L
        speechGateway.setLanguage(speechTag())
        Log.d("ConversationController", "Initialized with lang: $lang")
    }

    fun start() {
        stopped = false
        speechRetryCount = 0
        isSpeakingResult = false
        androidTts.init(langCode) {
            if (!stopped) {
                processAnswer("", isInitial = true)
            }
        }
    }

    fun stop() {
        stopped = true
        listening = false
        isSpeaking = false
        isSpeakingResult = false
        try { speechGateway.stop() } catch (_: Exception) {}
        try { androidTts.stop() } catch (_: Exception) {}
        stopAudio()
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun resumeListening() {
        if (!stopped && !isSpeaking && !isSpeakingResult) {
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

    fun speakOnly(text: String, onComplete: () -> Unit) {
        if (stopped) {
            onComplete()
            return
        }
        isSpeakingResult = true
        listening = false
        isSpeaking = true
        try { speechGateway.stop() } catch (_: Exception) {}
        listener?.onListening(false)
        listener?.onSpeaking(true)
        speakInternal(text, listenAfter = false, onDone = {
            isSpeakingResult = false
            isSpeaking = false
            onComplete()
        })
    }

    fun speakResultsSummary(resultsSummary: String, onComplete: () -> Unit) {
        if (stopped) {
            onComplete()
            return
        }
        isSpeakingResult = true
        listening = false
        isSpeaking = true
        try { speechGateway.stop() } catch (_: Exception) {}
        listener?.onListening(false)
        listener?.onSpeaking(true)
        listener?.onResultExplanation(resultsSummary)
        listener?.onQuestion(resultsSummary)
        speakInternal(resultsSummary, listenAfter = false, onDone = {
            isSpeakingResult = false
            isSpeaking = false
            mainHandler.postDelayed({ onComplete() }, 1200)
        })
    }

    fun processAnswer(answer: String, isInitial: Boolean = false) {
        if (stopped) return
        
        // Ignore empty answers except initial
        if (answer.isBlank() && !isInitial && profile.hasEdu()) {
            Log.d("ConversationController", "Ignoring blank answer after profile started")
            startListening()
            return
        }

        // Prevent double processing same answer quickly
        val now = System.currentTimeMillis()
        if (answer.isNotBlank() && now - lastSpeechResultTime < 1200) {
            Log.d("ConversationController", "Ignoring duplicate answer too quick: $answer")
            return
        }
        if (answer.isNotBlank()) lastSpeechResultTime = now

        // Check if AI is listening to itself
        if (answer.isNotBlank() && isSelfEcho(answer, lastSpokenText)) {
            Log.w("ConversationController", "Detected self-echo, ignoring: $answer vs $lastSpokenText")
            mainHandler.postDelayed({ if (!stopped) startListening() }, 1000)
            return
        }

        speechRetryCount = 0
        listener?.onStatus(thinkingText())
        listener?.onSpeaking(false)
        listener?.onListening(false)
        isSpeaking = false

        val isOnline = isNetworkAvailable()

        nluExtractor.extract(answer, langCode, profile, isOnline, object : NluExtractor.Callback {
            override fun onResult(fragment: ProfileFragment) {
                if (stopped) return
                profile.merge(fragment)
                listener?.onFieldExtracted(profile)

                if (profile.isComplete()) {
                    val closing = fragment.nextQuestion ?: generateClosingWithSummary()
                    lastQuestion = closing
                    lastSpokenText = closing
                    listener?.onQuestion(closing)
                    isSpeakingResult = true
                    isSpeaking = true
                    speakInternal(closing, listenAfter = false, onDone = {
                        isSpeakingResult = false
                        isSpeaking = false
                        if (!stopped) {
                            listener?.onDone(profile)
                        }
                    })
                } else {
                    val nextQ = fragment.nextQuestion ?: generateFallbackQuestion()
                    // Prevent asking same question again immediately
                    if (nextQ == lastQuestion && !isInitial) {
                        Log.d("ConversationController", "Same question as before, waiting for user")
                        startListening()
                        return
                    }
                    lastQuestion = nextQ
                    lastSpokenText = nextQ
                    listener?.onQuestion(nextQ)
                    speakThenListen(nextQ, true)
                }
            }

            override fun onError(reason: String) {
                if (stopped) return
                Log.w("ConversationController", "NLU error hidden: $reason")
                listener?.onStatus(thinkingText())
                val apology = when (langCode) {
                    "ta" -> "மன்னிக்கவும், சிறு தாமதம். மீண்டும் கேட்கிறேன்."
                    "hi" -> "थोड़ी देर, फिर से सुन रहा हूँ।"
                    "te" -> "కొంచెం ఆలస్యం, మళ్లీ వింటున్నాను."
                    "kn" -> "ಸ್ವಲ್ಪ ವಿಳಂಬ, ಮತ್ತೆ ಆಲಿಸುತ್ತಿದ್ದೇನೆ."
                    "ml" -> "ചെറിയ താമസം, വീണ്ടും കേൾക്കുന്നു."
                    else -> "Just a moment, let me listen again."
                }
                speakThenListen(apology, true)
            }
        })
    }

    private fun isSelfEcho(heard: String, spoken: String): Boolean {
        if (spoken.isBlank() || heard.isBlank()) return false
        if (heard.length < 10) return false
        // If heard text is very similar to what we just spoke, it's self-echo
        val spokenClean = spoken.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim()
        val heardClean = heard.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim()
        if (spokenClean.isBlank() || heardClean.isBlank()) return false
        
        // Check if heard contains large chunk of spoken
        val spokenWords = spokenClean.split(Regex("\\s+")).filter { it.length > 3 }
        if (spokenWords.size < 3) return false
        
        var matchCount = 0
        for (word in spokenWords) {
            if (heardClean.contains(word)) matchCount++
        }
        val similarity = matchCount.toFloat() / spokenWords.size
        return similarity > 0.6f
    }

    private fun generateClosingWithSummary(): String {
        val edu = profile.edu?.let { mapEduToReadable(it) } ?: "your education"
        val family = profile.familyOccupation ?: "your family background"
        val current = profile.currentLivelihood ?: "your current situation"
        val interests = if (profile.interests.isNotEmpty()) profile.interests.joinToString(", ") else "your interests"
        val district = profile.district?.ifBlank { "your area" } ?: "your area"

        return when (langCode) {
            "ta" -> "மிக்க நன்றி. $edu படித்திருக்கிறீர்கள், குடும்பம் $family, இப்போது $current, ஆர்வம் $interests, மாவட்டம் $district என்று புரிந்து கொண்டேன். உங்களுக்கான சிறந்த வாய்ப்புகளை தேடி, விளக்குகிறேன்."
            "hi" -> "बहुत धन्यवाद. आप $edu हैं, परिवार $family में है, अभी $current, रुचि $interests, जिला $district. अब आपके लिए सबसे अच्छे विकल्प ढूंढकर समझाता हूँ।"
            "te" -> "చాలా ధన్యవాదాలు. మీరు $edu, కుటుంబం $family, ప్రస్తుతం $current, ఆసక్తి $interests. ఇప్పుడు మీకు తగిన అవకాశాలు వెతికి వివరిస్తాను."
            "kn" -> "ತುಂಬಾ ಧನ್ಯವಾದಗಳು. ನೀವು $edu, ಕುಟುಂಬ $family, ಪ್ರಸ್ತುತ $current, ಆಸಕ್ತಿ $interests. ಈಗ ನಿಮಗಾಗಿ ಉತ್ತಮ ಅವಕಾಶಗಳನ್ನು ಹುಡುಕಿ ವಿವರಿಸುತ್ತೇನೆ."
            "ml" -> "വളരെ നന്ദി. നിങ്ങൾ $edu, കുടുംബം $family, ഇപ്പോൾ $current, താൽപ്പര്യം $interests. ഇപ്പോൾ നിങ്ങൾക്കான മികച്ച അവസരങ്ങൾ തിരഞ്ഞ് വിശദീകരിക്കാം."
            else -> "Thank you so much. I've got a clear picture now - you're $edu, family background is $family, currently $current, interested in $interests, from $district. Now let me find and explain the best options that truly fit you."
        }
    }

    private fun mapEduToReadable(edu: String): String {
        return when (edu) {
            "class5" -> when (langCode) { "ta" -> "5-ம் வகுப்பு வரை"; "hi" -> "5वीं तक"; else -> "up to 5th" }
            "class8" -> "8th"
            "class10" -> "10th"
            "class12" -> "12th"
            "graduate" -> when (langCode) { "ta" -> "பட்டதாரி"; "hi" -> "स्नातक"; else -> "graduate" }
            else -> edu
        }
    }

    private fun speakThenListen(text: String, listenAfter: Boolean) {
        // Always stop mic before speaking to prevent self-echo
        try { speechGateway.stop() } catch (_: Exception) {}
        listening = false
        listener?.onListening(false)
        speakInternal(text, listenAfter, onDone = {})
    }

    private fun speakInternal(text: String, listenAfter: Boolean, onDone: () -> Unit) {
        if (stopped) return
        if (text.isBlank()) {
            if (listenAfter) {
                mainHandler.postDelayed({ if (!stopped) startListening() }, 1000)
            } else {
                onDone()
            }
            return
        }

        // Ensure mic is off while speaking
        try { speechGateway.stop() } catch (_: Exception) {}
        listening = false
        isSpeaking = true
        listener?.onSpeaking(true)
        listener?.onListening(false)
        
        val clean = formatForTts(text)
        if (clean.isBlank()) {
            mainHandler.post {
                listener?.onSpeaking(false)
                isSpeaking = false
                if (listenAfter) {
                    mainHandler.postDelayed({ if (!stopped) startListening() }, 1000)
                } else {
                    onDone()
                }
            }
            return
        }

        lastSpokenText = clean
        val ttsEngine = prefs.getString("tts_engine", "auto") ?: "auto"

        val internalOnDone: () -> Unit = {
            mainHandler.post {
                listener?.onSpeaking(false)
                isSpeaking = false
                stopAudio()
                if (!stopped) {
                    if (listenAfter) {
                        // Longer delay to ensure TTS fully stopped and avoid self-echo
                        mainHandler.postDelayed({ if (!stopped) startListening() }, 1300)
                    } else {
                        onDone()
                    }
                } else {
                    onDone()
                }
            }
            Unit
        }

        when {
            ttsEngine == "sarvam" && sarvamGateway.isConfigured() -> {
                sarvamGateway.synthesize(clean, langCode, object : SarvamGateway.Callback {
                    override fun onSuccess(audioData: ByteArray) { playAudio(audioData, internalOnDone) }
                    override fun onError(error: String) {
                        Log.w("ConversationController", "Sarvam failed: $error, fallback to Android TTS")
                        androidTts.speak(clean, internalOnDone)
                    }
                })
            }
            ttsEngine == "bhashini" && bhashiniGateway.isAvailable() -> {
                bhashiniGateway.tts(clean, langCode, object : BhashiniGateway.TtsCallback {
                    override fun onAudio(audioBytes: ByteArray) { playAudio(audioBytes, internalOnDone) }
                    override fun onError(reason: String) {
                        Log.w("ConversationController", "Bhashini failed: $reason, fallback to Android")
                        androidTts.speak(clean, internalOnDone)
                    }
                })
            }
            ttsEngine == "auto" && langCode != "en" -> {
                if (sarvamGateway.isConfigured()) {
                    sarvamGateway.synthesize(clean, langCode, object : SarvamGateway.Callback {
                        override fun onSuccess(audioData: ByteArray) { playAudio(audioData, internalOnDone) }
                        override fun onError(error: String) {
                            Log.w("ConversationController", "Sarvam auto failed: $error, using Android TTS")
                            androidTts.speak(clean, internalOnDone)
                        }
                    })
                } else {
                    androidTts.speak(clean, internalOnDone)
                }
            }
            else -> {
                androidTts.speak(clean, internalOnDone)
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
                    try { tempFile.delete() } catch (_: Exception) {}
                }
                setOnErrorListener { _, _, _ ->
                    stopAudio()
                    onDone()
                    try { tempFile.delete() } catch (_: Exception) {}
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w("ConversationController", "Audio play failed: ${e.message}")
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
        if (stopped || isSpeaking || isSpeakingResult) {
            Log.d("ConversationController", "Not starting listening - stopped=$stopped, speaking=$isSpeaking, speakingResult=$isSpeakingResult")
            return
        }
        if (listening) {
            Log.d("ConversationController", "Already listening, skip")
            return
        }
        listening = true
        listener?.onListening(true)
        listener?.onStatus(listeningText())
        Log.d("ConversationController", "Starting listening for $langCode")

        speechGateway.setListener(object : SpeechGateway.Listener {
            override fun onPartial(text: String) {
                mainHandler.post {
                    if (!stopped && !isSpeaking) {
                        listener?.onPartialTranscript(text)
                    }
                }
            }
            override fun onResult(text: String) {
                listening = false
                speechRetryCount = 0
                Log.d("ConversationController", "Speech result: $text")
                mainHandler.post {
                    if (!stopped) {
                        listener?.onListening(false)
                        listener?.onTranscriptResult(text)
                        processAnswer(text)
                    }
                }
            }
            override fun onError(error: String) {
                listening = false
                Log.w("ConversationController", "Speech error: $error retry=$speechRetryCount")
                mainHandler.post { if (!stopped) listener?.onListening(false) }

                val lower = error.lowercase()
                val isRecoverable = lower.contains("no match") || lower.contains("no speech") || lower.contains("empty") || lower.contains("speech timeout")

                if (isRecoverable && speechRetryCount < maxSpeechRetries && !stopped && !isSpeakingResult && !isSpeaking) {
                    speechRetryCount++
                    val retryMsg = when (langCode) {
                        "ta" -> "கேட்கவில்லை, மீண்டும் கேட்கிறேன். பேசுங்கள்."
                        "hi" -> "सुनाई नहीं दिया, फिर से सुन रहा हूँ। बोलिए।"
                        "te" -> "వినబడలేదు, మళ్లీ వింటున్నాను. మాట్లాడండి."
                        "kn" -> "ಕೇಳಲಿಲ್ಲ, ಮತ್ತೆ ಆಲಿಸುತ್ತಿದ್ದೇನೆ. ಮಾತನಾಡಿ."
                        "ml" -> "കേൾക്കാനായില്ല, വീണ്ടും കേൾക്കുന്നു. സംസാരിക്കൂ."
                        else -> "Didn't catch that, listening again. Please speak."
                    }
                    mainHandler.post {
                        if (!stopped) listener?.onStatus(retryMsg)
                    }
                    mainHandler.postDelayed({
                        if (!stopped && !isSpeakingResult && !isSpeaking) startListening()
                    }, 1500)
                } else {
                    speechRetryCount = 0
                    val finalMsg = when (langCode) {
                        "ta" -> "பேசுங்கள், கேட்கிறேன். அல்லது தட்டச்சு செய்யுங்கள்."
                        "hi" -> "बोलिए, मैं सुन रहा हूँ। या टाइप करें।"
                        "te" -> "మాట్లాడండి, వింటున్నాను. లేదా టైప్ చేయండి."
                        "kn" -> "ಮಾತನಾಡಿ, ಆಲಿಸುತ್ತಿದ್ದೇನೆ. ಅಥವಾ ಟೈಪ್ ಮಾಡಿ."
                        "ml" -> "സംസാരിക്കൂ, കേൾക്കുന്നു. അല്ലെങ്കിൽ ടൈപ്പ് ചെയ്യൂ."
                        else -> "I'm listening. Speak naturally or type below."
                    }
                    mainHandler.post {
                        if (!stopped) listener?.onStatus(finalMsg)
                    }
                    mainHandler.postDelayed({
                        if (!stopped && !isSpeakingResult && !isSpeaking) startListening()
                    }, 4000)
                }
            }
        })
        try {
            speechGateway.start()
        } catch (e: Exception) {
            Log.e("ConversationController", "Failed to start speech: ${e.message}")
            listening = false
            mainHandler.post {
                listener?.onListening(false)
                listener?.onStatus(
                    when (langCode) {
                        "ta" -> "மைக் தொடங்க முடியவில்லை. தட்டச்சு செய்யுங்கள்."
                        "hi" -> "माइक शुरू नहीं हो पाया। टाइप करें।"
                        else -> "Mic couldn't start. Please type below."
                    }
                )
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun formatForTts(text: String): String {
        var t = text
        t = t.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF]+"), " ")
        t = t.replace("*", " ").replace("#", " ").replace("`", " ").replace("_", " ")
        t = t.replace(Regex("!+"), ". ")
        t = t.replace(Regex("\\?+"), ". ")
        t = t.replace(Regex("\\.{2,}"), ". ")
        t = t.replace("(", " ").replace(")", " ").replace("[", " ").replace("]", " ")
        t = t.replace("\n", " ").replace("\r", " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        if (t.length > 500) {
            t = t.take(500).trim()
            val lastDot = t.lastIndexOf('.')
            if (lastDot > 300) t = t.substring(0, lastDot + 1)
        }
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
        "ta" -> "கேட்கிறேன். இயல்பாக பேசுங்கள்"
        "hi" -> "सुन रहा हूँ. दिल खोलकर बताइए"
        "te" -> "వింటున్నాను. సహజంగా మాట్లాడండి"
        "kn" -> "ಆಲಿಸುತ್ತಿದ್ದೇನೆ. ಮುಕ್ತವಾಗಿ ಹೇಳಿ"
        "ml" -> "കേൾക്കുന്നു. മനസ്സ് തുറന്ന് പറയൂ"
        else -> "Listening. Speak naturally, I'm here"
    }

    private fun thinkingText(): String = when (langCode) {
        "ta" -> "புரிந்து கொள்கிறேன்"
        "hi" -> "समझ रहा हूँ"
        "te" -> "అర్థం చేసుకుంటున్నాను"
        "kn" -> "ಅರ್ಥಮಾಡಿಕೊಳ್ಳುತ್ತಿದ್ದೇನೆ"
        "ml" -> "മനസ്സിലാക്കുന്നു"
        else -> "Understanding you"
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
