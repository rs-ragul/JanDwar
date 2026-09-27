package `in`.jandwar.app.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {
    var bhashiniUserId: String = ""
    var bhashiniInferenceKey: String = ""
    var bhashiniAppId: String = ""
    var groqApiKey: String = ""
    var sarvamApiKey: String = ""
        private set

    @Volatile
    private var loaded = false

    fun load() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                val input = context.assets.open("config.json")
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                input.close()
                val obj = JSONObject(output.toString("UTF-8"))
                bhashiniUserId = obj.optString("bhashini_user_id", "")
                bhashiniInferenceKey = obj.optString("bhashini_inference_key", "")
                bhashiniAppId = obj.optString("bhashini_app_id", "")
                groqApiKey = obj.optString("groq_api_key", "")
                sarvamApiKey = obj.optString("sarvam_api_key", "")
                loaded = true
            } catch (e: Exception) {
                // Keys stay empty; app degrades gracefully offline
                loaded = true
            }
        }
    }

    fun bhashiniEnabled(): Boolean {
        return bhashiniInferenceKey.isNotBlank()
    }

    fun groqEnabled(): Boolean = groqApiKey.isNotBlank()
    fun sarvamEnabled(): Boolean = sarvamApiKey.isNotBlank()
}
