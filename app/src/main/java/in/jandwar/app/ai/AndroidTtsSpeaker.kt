package `in`.jandwar.app.ai

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTtsSpeaker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPreferences
) : Speaker {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var currentLang = "en"

    fun init(langCode: String, onReady: () -> Unit) {
        currentLang = langCode
        if (tts != null && ready) {
            setLanguage()
            onReady()
            return
        }
        try {
            tts = TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    setLanguage()
                    Log.d("AndroidTts", "TTS ready for $langCode, voices: ${tts?.voices?.size}, default engine: ${tts?.defaultEngine}")
                    try {
                        val engines = tts?.engines
                        Log.d("AndroidTts", "Engines: ${engines?.joinToString { it.name }}")
                    } catch (_: Exception) {}
                } else {
                    Log.w("AndroidTts", "TTS init failed: $status")
                }
                onReady()
            }
        } catch (e: Exception) {
            Log.e("AndroidTts", "TTS init exception: ${e.message}")
            ready = false
            onReady()
        }
    }

    private fun setLanguage() {
        val locale = when (currentLang) {
            "ta" -> Locale("ta", "IN")
            "hi" -> Locale("hi", "IN")
            "te" -> Locale("te", "IN")
            "kn" -> Locale("kn", "IN")
            "ml" -> Locale("ml", "IN")
            else -> Locale("en", "IN")
        }
        try {
            var result = tts?.setLanguage(locale)
            Log.d("AndroidTts", "Set language $locale result: $result")
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("AndroidTts", "Language $locale not supported, trying ${Locale(currentLang)}")
                val fallbackLocale = Locale(currentLang)
                result = tts?.setLanguage(fallbackLocale)
                Log.d("AndroidTts", "Fallback $fallbackLocale result: $result")
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w("AndroidTts", "Fallback also not supported, trying en-IN")
                    tts?.language = Locale("en", "IN")
                }
            }
            selectBestVoice(locale)
            // More natural speech rates
            tts?.setSpeechRate(
                when (currentLang) {
                    "ta" -> 0.90f
                    "te" -> 0.90f
                    "kn" -> 0.90f
                    "ml" -> 0.90f
                    "hi" -> 0.92f
                    else -> 0.95f
                }
            )
            tts?.setPitch(1.05f)
        } catch (e: Exception) {
            Log.e("AndroidTts", "setLanguage failed: ${e.message}")
        }
    }

    private fun selectBestVoice(targetLocale: Locale) {
        try {
            val voices = tts?.voices ?: return
            val enginePref = prefs.getString("tts_engine", "auto") ?: "auto"
            val forceOffline = enginePref == "android_offline"
            Log.d("AndroidTts", "Available voices total: ${voices.size}, looking for ${targetLocale.language}, forceOffline=$forceOffline")
            
            val matchingVoices = voices.filter { it.locale.language == targetLocale.language }
            Log.d("AndroidTts", "Matching voices for ${targetLocale.language}: ${matchingVoices.size}")
            
            if (matchingVoices.isEmpty()) {
                Log.w("AndroidTts", "No voices for ${targetLocale.language}, listing all: ${voices.map { "${it.name}:${it.locale}" }.take(20)}")
                return
            }

            var bestVoice: android.speech.tts.Voice? = null
            var bestScore = -1
            for (voice in matchingVoices) {
                var score = 0
                // Prefer high quality
                if (voice.quality >= 400) score += 40
                else if (voice.quality >= 300) score += 25
                else if (voice.quality >= 200) score += 10
                
                // Prefer offline if requested, otherwise prefer network for better quality in Indian langs
                if (forceOffline) {
                    if (!voice.isNetworkConnectionRequired) score += 60 else continue
                } else {
                    // For Indian languages, network voices are often better
                    if (voice.isNetworkConnectionRequired) score += 15 else score += 10
                }
                
                // Prefer female voices for naturalness
                if (voice.name.contains("female", ignoreCase = true)) score += 20
                if (voice.name.contains("meera", ignoreCase = true) || voice.name.contains("heera", ignoreCase = true) || voice.name.contains("kavya", ignoreCase = true)) {
                    score += 25
                }
                // Avoid legacy / low quality
                if (voice.name.contains("legacy", ignoreCase = true)) score -= 20
                if (voice.name.contains("compact", ignoreCase = true)) score -= 10
                
                // Prefer exact country match
                if (voice.locale.country == targetLocale.country) score += 15
                // Prefer Google engine
                if (voice.name.contains("google", ignoreCase = true)) score += 10
                
                Log.d("AndroidTts", "Voice candidate: ${voice.name}, locale: ${voice.locale}, quality: ${voice.quality}, network: ${voice.isNetworkConnectionRequired}, score: $score")
                if (score > bestScore) {
                    bestScore = score
                    bestVoice = voice
                }
            }
            bestVoice?.let {
                try {
                    tts?.voice = it
                    Log.d("AndroidTts", "✓ Selected best voice: ${it.name}, locale: ${it.locale}, quality: ${it.quality}, score: $bestScore")
                } catch (e: Exception) {
                    Log.e("AndroidTts", "Failed to set voice ${it.name}: ${e.message}")
                }
            } ?: run {
                Log.w("AndroidTts", "No suitable voice found for ${targetLocale.language}")
            }
        } catch (e: Exception) {
            Log.e("AndroidTts", "Voice selection failed: ${e.message}", e)
        }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (!ready || tts == null) {
            Log.w("AndroidTts", "TTS not ready")
            onDone()
            return
        }
        // Clean text for TTS - remove problematic characters
        val cleanText = cleanForTts(text)
        if (cleanText.isBlank()) {
            Log.w("AndroidTts", "Clean text blank, skipping")
            onDone()
            return
        }
        val utteranceId = "utt_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { Log.d("AndroidTts", "Speaking started: $utteranceId") }
            override fun onDone(utterId: String?) {
                Log.d("AndroidTts", "Speaking done: $utterId")
                if (utterId == utteranceId) onDone()
            }
            override fun onError(utterId: String?) {
                Log.w("AndroidTts", "Speaking error: $utterId")
                if (utterId == utteranceId) onDone()
            }
            override fun onError(utterId: String?, errorCode: Int) {
                Log.w("AndroidTts", "Speaking error $errorCode: $utterId")
                if (utterId == utteranceId) onDone()
            }
        })
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
        try {
            val result = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            Log.d("AndroidTts", "Speak called, result: $result, lang: $currentLang, text: ${cleanText.take(80)}")
            if (result != TextToSpeech.SUCCESS) {
                Log.w("AndroidTts", "Speak failed with $result")
                onDone()
            }
        } catch (e: Exception) {
            Log.e("AndroidTts", "Speak exception: ${e.message}", e)
            onDone()
        }
    }

    private fun cleanForTts(text: String): String {
        var t = text
        // Remove emojis and special symbols that TTS might read literally
        t = t.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF]+"), " ")
        // Remove markdown
        t = t.replace("*", "").replace("#", "").replace("`", "")
        // Replace multiple punctuation that TTS reads as "exclamation" etc
        t = t.replace(Regex("!+"), ". ")
        t = t.replace(Regex("\\?{2,}"), "? ")
        t = t.replace(Regex("\\.{2,}"), ". ")
        // Remove isolated punctuation that gets read literally
        t = t.replace(" ! ", " ")
        t = t.replace(" ?", " ")
        // Clean newlines and multiple spaces
        t = t.replace("\n", " ").replace("\r", " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        // Don't let TTS read "!" at end as exclamation mark word
        if (t.endsWith("!")) t = t.dropLast(1) + "."
        return t
    }

    override fun stop() {
        try { tts?.stop() } catch (_: Exception) {}
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        ready = false
    }
}
