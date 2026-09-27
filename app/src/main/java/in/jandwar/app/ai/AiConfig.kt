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
    // Bhashini - 3 possible credentials from portal
    var bhashiniUserId: String = "" // UDYAT KEY / ULCA User ID (07e29... from screenshot)
    var bhashiniInferenceKey: String = "" // INFERENCE key (n44PH... from screenshot)
    var bhashiniAppId: String = "" // App ID (d0bed4a... from screenshot)
    var bhashiniUlcaApiKey: String = "" // Sometimes same as UDYAT, kept for compatibility
    
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
                
                // Support multiple key names for Bhashini (user might use different naming)
                bhashiniUserId = obj.optString("bhashini_user_id", "")
                    .ifBlank { obj.optString("bhashini_udyat_key", "") }
                    .ifBlank { obj.optString("bhashini_ulca_user_id", "") }
                    .ifBlank { obj.optString("bhashini_ulca_api_key", "") }
                
                bhashiniInferenceKey = obj.optString("bhashini_inference_key", "")
                    .ifBlank { obj.optString("bhashini_inference_api_key", "") }
                    .ifBlank { obj.optString("inference_key", "") }
                
                bhashiniAppId = obj.optString("bhashini_app_id", "")
                    .ifBlank { obj.optString("bhashini_app", "") }
                    .ifBlank { obj.optString("app_id", "") }
                
                bhashiniUlcaApiKey = obj.optString("bhashini_ulca_api_key", "")
                    .ifBlank { obj.optString("bhashini_api_key", "") }
                    .ifBlank { bhashiniUserId } // Fallback: many portals show UDYAT as the main key

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
        // For Dhruva direct compute, only inference key is needed
        // User ID is optional for our implementation
        return bhashiniInferenceKey.isNotBlank()
    }

    fun bhashiniFullyConfigured(): Boolean {
        return bhashiniInferenceKey.isNotBlank() && bhashiniUserId.isNotBlank()
    }

    fun groqEnabled(): Boolean = groqApiKey.isNotBlank()
    fun sarvamEnabled(): Boolean = sarvamApiKey.isNotBlank()
    
    fun getConfigStatus(): String {
        return buildString {
            appendLine("Bhashini:")
            appendLine("  UDYAT/ULCA User ID (07e29...): ${if (bhashiniUserId.isNotBlank()) "✓ Present (${bhashiniUserId.take(6)}...)" else "✗ Missing"}")
            appendLine("  Inference Key (n44PH...): ${if (bhashiniInferenceKey.isNotBlank()) "✓ Present (${bhashiniInferenceKey.take(6)}...)" else "✗ Missing"}")
            appendLine("  App ID (d0bed4...): ${if (bhashiniAppId.isNotBlank()) "✓ Present (${bhashiniAppId.take(6)}...)" else "○ Optional"}")
            appendLine("Groq: ${if (groqEnabled()) "✓ Present" else "✗ Missing (will use offline)"}")
            appendLine("Sarvam: ${if (sarvamEnabled()) "✓ Present (best for Tamil TTS)" else "✗ Missing (will use Android TTS)"}")
        }
    }
}
