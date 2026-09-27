package `in`.jandwar.app.ai

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                setLanguage()
            }
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
            else -> Locale.ENGLISH
        }

        tts?.language = locale

        val enginePref = prefs.getString("tts_engine", "auto") ?: "auto"
        val forceOffline = enginePref == "android_offline"

        try {
            val voices = tts?.voices ?: return
            var best: android.speech.tts.Voice? = null
            for (v in voices) {
                if (v.locale.language == locale.language) {
                    if (forceOffline) {
                        if (!v.isNetworkConnectionRequired && !v.name.contains("network", ignoreCase = true)) {
                            best = v
                            break
                        }
                    } else {
                        if (v.isNetworkConnectionRequired || v.name.contains("network", ignoreCase = true)) {
                            best = v
                            break
                        }
                    }
                }
            }
            best?.let { tts?.voice = it }
        } catch (_: Exception) {}
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (!ready || tts == null) {
            onDone()
            return
        }

        val utteranceId = "utt_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utterId: String?) {
                if (utterId == utteranceId) onDone()
            }

            override fun onError(utterId: String?) {
                if (utterId == utteranceId) onDone()
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    override fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
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
