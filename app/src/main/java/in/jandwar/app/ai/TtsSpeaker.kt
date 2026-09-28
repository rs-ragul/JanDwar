package `in`.jandwar.app.ai

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text-to-speech for the conversational interview.
 *
 * Works fully offline once the device has the language pack installed, and
 * degrades gracefully: if a language is unavailable the caller is still
 * notified so the UI can show the text instead of silently stalling.
 */
@Singleton
class TtsSpeaker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var lang = "en"

    /** False when the chosen language has no installed voice data. */
    var languageAvailable = true
        private set

    fun init(langCode: String, onReady: (Boolean) -> Unit) {
        lang = langCode
        val existing = tts
        if (existing != null && ready) {
            applyLanguage(existing)
            onReady(languageAvailable)
            return
        }
        try {
            tts = TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) tts?.let { applyLanguage(it) } else languageAvailable = false
                onReady(ready && languageAvailable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "init failed: ${e.message}")
            ready = false
            languageAvailable = false
            onReady(false)
        }
    }

    fun setLanguage(langCode: String) {
        lang = langCode
        tts?.let { applyLanguage(it) }
    }

    private fun applyLanguage(engine: TextToSpeech) {
        val locale = localeFor(lang)
        languageAvailable = try {
            var result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = engine.setLanguage(Locale(lang))
            }
            val ok = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ok) {
                Log.w(TAG, "No voice data for $lang — falling back to en-IN")
                engine.language = Locale("en", "IN")
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "setLanguage failed: ${e.message}")
            false
        }

        selectBestVoice(engine, locale)
        // Slightly slower than default: clearer for first-time users.
        engine.setSpeechRate(if (lang == "en") 0.95f else 0.90f)
        engine.setPitch(1.02f)
    }

    private fun selectBestVoice(engine: TextToSpeech, target: Locale) {
        try {
            val candidates = engine.voices
                ?.filter { it.locale.language == target.language }
                ?.takeIf { it.isNotEmpty() } ?: return

            val best = candidates.maxByOrNull { score(it, target) } ?: return
            engine.voice = best
            Log.d(TAG, "voice=${best.name} locale=${best.locale} quality=${best.quality}")
        } catch (e: Exception) {
            Log.w(TAG, "voice selection skipped: ${e.message}")
        }
    }

    private fun score(voice: Voice, target: Locale): Int {
        var s = 0
        s += when {
            voice.quality >= 400 -> 40
            voice.quality >= 300 -> 25
            voice.quality >= 200 -> 10
            else -> 0
        }
        // Prefer voices that work without a connection — rural reality.
        if (!voice.isNetworkConnectionRequired) s += 30
        if (voice.locale.country == target.country) s += 15
        if (voice.name.contains("local", true)) s += 8
        if (voice.name.contains("legacy", true)) s -= 25
        if (voice.name.contains("compact", true)) s -= 10
        return s
    }

    /** Speaks [text]; [onDone] always fires exactly once, even on failure. */
    fun speak(text: String, onDone: () -> Unit) {
        val engine = tts
        val clean = clean(text)
        if (!ready || engine == null || clean.isBlank()) {
            onDone()
            return
        }
        val id = "jd_${System.nanoTime()}"
        var delivered = false
        val finishOnce = {
            if (!delivered) {
                delivered = true
                onDone()
            }
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) finishOnce()
            }

            @Deprecated("Superseded by onError(String, Int)")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) finishOnce()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == id) finishOnce()
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }
        try {
            val r = engine.speak(clean, TextToSpeech.QUEUE_FLUSH, params, id)
            if (r != TextToSpeech.SUCCESS) finishOnce()
        } catch (e: Exception) {
            Log.e(TAG, "speak failed: ${e.message}")
            finishOnce()
        }
    }

    /** Strips anything a TTS engine would read out awkwardly. */
    private fun clean(text: String): String = text
        .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE0F]+"), " ")
        .replace(Regex("[*#`_>|]"), "")
        .replace(Regex("^\\s*[-•]\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("!+"), ".")
        .replace(Regex("\\?{2,}"), "?")
        .replace(Regex("\\.{2,}"), ".")
        .replace(Regex("[\\r\\n]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "TtsSpeaker"

        fun localeFor(lang: String): Locale = when (lang) {
            "ta" -> Locale("ta", "IN")
            "hi" -> Locale("hi", "IN")
            "te" -> Locale("te", "IN")
            "kn" -> Locale("kn", "IN")
            "ml" -> Locale("ml", "IN")
            else -> Locale("en", "IN")
        }
    }
}
