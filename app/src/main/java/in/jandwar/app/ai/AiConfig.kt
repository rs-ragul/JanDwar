package `in`.jandwar.app.ai

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads optional cloud credentials from `assets/config.json`.
 *
 * The app is fully functional with an **empty** config: the on-device NLU,
 * Android SpeechRecognizer and Android TextToSpeech cover the whole journey
 * offline. Supplying keys upgrades the experience:
 *
 *  - `groq_api_key`      → free-flowing LLM conversation (most natural)
 *  - `sarvam_api_key`    → high quality Indic neural TTS
 *  - `bhashini_*`        → Govt. of India ASR / TTS / translation
 *
 * See `assets/config.json` for the template.
 */
@Singleton
class AiConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {
    var groqApiKey: String = ""
        private set
    var groqModel: String = DEFAULT_GROQ_MODEL
        private set
    var sarvamApiKey: String = ""
        private set
    var bhashiniInferenceKey: String = ""
        private set
    var bhashiniUserId: String = ""
        private set
    var bhashiniAppId: String = ""
        private set

    @Volatile
    private var loaded = false

    init {
        // Load eagerly so any collaborator can ask groqEnabled() safely.
        load()
    }

    @Synchronized
    fun load() {
        if (loaded) return
        loaded = true
        try {
            val text = context.assets.open(CONFIG_FILE).bufferedReader().use { it.readText() }
            val obj = JSONObject(text)

            groqApiKey = obj.readKey("groq_api_key")
            groqModel = obj.optString("groq_model", "").ifBlank { DEFAULT_GROQ_MODEL }
            sarvamApiKey = obj.readKey("sarvam_api_key")
            bhashiniInferenceKey = obj.readKey("bhashini_inference_key", "bhashini_inference_api_key")
            bhashiniUserId = obj.readKey("bhashini_user_id", "bhashini_udyat_key", "bhashini_ulca_user_id")
            bhashiniAppId = obj.readKey("bhashini_app_id", "app_id")

            Log.i(TAG, "Config loaded · groq=${groqEnabled()} sarvam=${sarvamEnabled()} bhashini=${bhashiniEnabled()}")
        } catch (e: Exception) {
            // No config.json (or malformed) — this is a supported, normal state.
            Log.i(TAG, "No usable config.json; running fully on-device (${e.javaClass.simpleName})")
        }
    }

    /**
     * Reads the first non-blank value among [names], ignoring the placeholder
     * text shipped in the template so an unedited config behaves as "absent".
     */
    private fun JSONObject.readKey(vararg names: String): String {
        for (n in names) {
            val v = optString(n, "").trim()
            if (v.isNotBlank() && !isPlaceholder(v)) return v
        }
        return ""
    }

    private fun isPlaceholder(v: String): Boolean {
        val l = v.lowercase()
        return l.startsWith("paste_") || l.startsWith("your_") || l.startsWith("<") ||
                l == "null" || l == "none" || l.contains("here")
    }

    fun groqEnabled(): Boolean = groqApiKey.isNotBlank()
    fun sarvamEnabled(): Boolean = sarvamApiKey.isNotBlank()
    fun bhashiniEnabled(): Boolean = bhashiniInferenceKey.isNotBlank()

    /** True when any cloud voice engine can be used. */
    fun cloudVoiceAvailable(): Boolean = sarvamEnabled() || bhashiniEnabled()

    companion object {
        private const val TAG = "AiConfig"
        const val CONFIG_FILE = "config.json"
        const val DEFAULT_GROQ_MODEL = "llama-3.3-70b-versatile"
    }
}
