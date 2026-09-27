package `in`.jandwar.app.ai

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SarvamGateway @Inject constructor(
    private val aiConfig: AiConfig
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val url = "https://api.sarvam.ai/text-to-speech"

    interface Callback {
        fun onSuccess(audioData: ByteArray)
        fun onError(error: String)
    }

    fun isConfigured(): Boolean = aiConfig.sarvamEnabled()

    fun synthesize(text: String, langCode: String, callback: Callback) {
        if (!aiConfig.sarvamEnabled()) {
            callback.onError("Sarvam not configured")
            return
        }
        val sarvamLang = mapLangCode(langCode)
        val json = JSONObject().apply {
            put("inputs", JSONArray().apply { put(JSONObject().apply { put("text", text) }) })
            put("target_language_code", sarvamLang)
            put("speaker", "meera")
            put("enable_preprocessing", true)
            put("model", "bulbul:v1")
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("api-subscription-key", aiConfig.sarvamApiKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(jsonType, json.toString()))
            .build()
        Log.d("SarvamGateway", "TTS request lang: $sarvamLang, text: ${text.take(50)}")
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("SarvamGateway", "Network failure: ${e.message}")
                mainHandler.post { callback.onError("Network: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful && response.body != null) {
                        val respStr = response.body!!.string()
                        val respJson = JSONObject(respStr)
                        val base64Audio = respJson.getJSONArray("audios").getString(0)
                        val audioData = Base64.decode(base64Audio, Base64.DEFAULT)
                        Log.d("SarvamGateway", "TTS success, audio size: ${audioData.size}")
                        mainHandler.post { callback.onSuccess(audioData) }
                    } else {
                        val errBody = response.body?.string() ?: ""
                        Log.w("SarvamGateway", "API error ${response.code}: $errBody")
                        mainHandler.post { callback.onError("API ${response.code}") }
                    }
                } catch (e: Exception) {
                    Log.e("SarvamGateway", "Parse error: ${e.message}", e)
                    mainHandler.post { callback.onError("Parse: ${e.message}") }
                }
            }
        })
    }

    private fun mapLangCode(lang: String): String {
        return when (lang.lowercase()) {
            "ta" -> "ta-IN"
            "hi" -> "hi-IN"
            "te" -> "te-IN"
            "kn" -> "kn-IN"
            "ml" -> "ml-IN"
            "en" -> "en-IN"
            else -> "en-IN"
        }
    }
}
