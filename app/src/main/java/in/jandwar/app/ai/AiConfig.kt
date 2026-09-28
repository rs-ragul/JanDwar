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
 * offline. One optional key upgrades the experience:
 *
 *  - `groq_api_key` → free-flowing LLM conversation (the most natural wording)
 *
 * Nothing else is read. Keys for services the app does not actually call were
 * removed rather than left as dead configuration.
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

            Log.i(TAG, "Config loaded · groq=${groqEnabled()}")
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

    companion object {
        private const val TAG = "AiConfig"
        const val CONFIG_FILE = "config.json"
        const val DEFAULT_GROQ_MODEL = "llama-3.3-70b-versatile"
    }
}
