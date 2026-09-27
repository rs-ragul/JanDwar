package `in`.jandwar.app.ai

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bhashini Gateway - Supports both old ULCA and new Udyat portal
 * 
 * Official flow (from https://bhashini.gitbook.io/bhashini-apis/):
 * 1. Config Call: POST https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline
 *    Headers: userID, ulcaApiKey (both from My Profile)
 *    Response: pipelineInferenceAPIEndPoint.callbackUrl + inferenceApiKey
 * 2. Compute Call: POST callbackUrl
 *    Header: Authorization = inferenceApiKey.value
 * 
 * New Udyat portal (your screenshot):
 * - UDYAT KEY (07e29...) = ulcaApiKey / userID
 * - INFERENCE (n44PH...) = inferenceApiKey (direct for compute)
 * - App ID (d0b...) = identifier, not used in API
 * 
 * Our implementation: Tries direct compute first (using inference key only),
 * fallback to config call if userID available. This supports both portals.
 */
@Singleton
class BhashiniGateway @Inject constructor(
    private val aiConfig: AiConfig
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val directComputeUrl = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
    private val configUrl = "https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline"

    interface TranslateCallback {
        fun onResult(translated: String)
        fun onError(reason: String)
    }

    interface TtsCallback {
        fun onAudio(audioBytes: ByteArray)
        fun onError(reason: String)
    }

    fun isAvailable(): Boolean = aiConfig.bhashiniEnabled()

    fun translate(text: String, sourceLang: String, targetLang: String = "en", callback: TranslateCallback) {
        if (!isAvailable() || sourceLang == targetLang || text.isBlank()) {
            callback.onResult(text)
            return
        }

        Thread {
            try {
                // Try direct compute first (works with just inference key - new portal)
                val body = buildNmtBody(text, sourceLang, targetLang)
                Log.d("Bhashini", "Translate direct compute: $sourceLang -> $targetLang, key ${aiConfig.bhashiniInferenceKey.take(6)}...")
                val responseText = postDirect(body.toString())
                val translated = parseNmtResponse(responseText) ?: text
                Log.d("Bhashini", "Translate success: ${translated.take(80)}")
                mainHandler.post { callback.onResult(translated) }
            } catch (e: Exception) {
                Log.w("Bhashini", "Direct translate failed: ${e.message}, trying config flow if userID available")
                // Fallback to config call flow if userID available
                if (aiConfig.bhashiniUserId.isNotBlank()) {
                    tryConfigThenTranslate(text, sourceLang, targetLang, callback)
                } else {
                    mainHandler.post { callback.onError(e.message ?: "Translate failed") }
                }
            }
        }.start()
    }

    private fun tryConfigThenTranslate(text: String, sourceLang: String, targetLang: String, callback: TranslateCallback) {
        try {
            val configBody = buildConfigBody(sourceLang, targetLang)
            val configResponse = postConfig(configBody.toString())
            val (callbackUrl, inferenceKey) = parseConfigResponse(configResponse)
            if (callbackUrl != null && inferenceKey != null) {
                val computeBody = buildNmtBody(text, sourceLang, targetLang)
                val responseText = postToUrl(callbackUrl, inferenceKey, computeBody.toString())
                val translated = parseNmtResponse(responseText) ?: text
                mainHandler.post { callback.onResult(translated) }
            } else {
                throw IOException("Config response missing callbackUrl or inferenceKey")
            }
        } catch (e: Exception) {
            Log.w("Bhashini", "Config flow also failed: ${e.message}")
            mainHandler.post { callback.onError(e.message ?: "Translate failed") }
        }
    }

    fun tts(text: String, targetLang: String, callback: TtsCallback) {
        if (!isAvailable()) {
            callback.onError("Bhashini not configured")
            return
        }
        if (text.isBlank()) {
            callback.onError("Empty text")
            return
        }

        Thread {
            try {
                var cleanText = text.replace(Regex("[!]{2,}"), ". ")
                    .replace(Regex("[?]{2,}"), ". ")
                    .replace("*", "").replace("#", "")
                    .replace(Regex("\\s+"), " ").trim()
                if (cleanText.length > 400) cleanText = cleanText.take(400)
                
                val body = buildTtsBody(cleanText, targetLang)
                Log.d("Bhashini", "TTS direct compute lang: $targetLang, key ${aiConfig.bhashiniInferenceKey.take(6)}...")
                val audio = postForAudioDirect(body.toString())
                if (audio != null && audio.isNotEmpty()) {
                    Log.d("Bhashini", "TTS success, audio size: ${audio.size}")
                    mainHandler.post { callback.onAudio(audio) }
                } else {
                    mainHandler.post { callback.onError("No audio from Bhashini") }
                }
            } catch (e: Exception) {
                Log.w("Bhashini", "TTS direct failed: ${e.message}")
                mainHandler.post { callback.onError(e.message ?: "TTS failed") }
            }
        }.start()
    }

    private fun buildConfigBody(sourceLang: String, targetLang: String): JSONObject {
        return JSONObject().apply {
            put("pipelineTasks", JSONArray().apply {
                put(JSONObject().apply {
                    put("taskType", "translation")
                    put("config", JSONObject().apply {
                        put("language", JSONObject().apply {
                            put("sourceLanguage", mapToBhashiniLang(sourceLang))
                            put("targetLanguage", mapToBhashiniLang(targetLang))
                        })
                    })
                })
            })
            put("pipelineRequestConfig", JSONObject().apply {
                put("pipelineId", "64392f96daac500b55c543cd") // Default MeitY pipeline
            })
        }
    }

    private fun buildNmtBody(text: String, sourceLang: String, targetLang: String): JSONObject {
        return JSONObject().apply {
            put("pipelineTasks", JSONArray().apply {
                put(JSONObject().apply {
                    put("taskType", "translation")
                    put("config", JSONObject().apply {
                        put("language", JSONObject().apply {
                            put("sourceLanguage", mapToBhashiniLang(sourceLang))
                            put("targetLanguage", mapToBhashiniLang(targetLang))
                        })
                    })
                })
            })
            put("inputData", JSONObject().apply {
                put("input", JSONArray().apply {
                    put(JSONObject().apply { put("source", text) })
                })
            })
        }
    }

    private fun buildTtsBody(text: String, lang: String): JSONObject {
        return JSONObject().apply {
            put("pipelineTasks", JSONArray().apply {
                put(JSONObject().apply {
                    put("taskType", "tts")
                    put("config", JSONObject().apply {
                        put("language", JSONObject().apply {
                            put("sourceLanguage", mapToBhashiniLang(lang))
                        })
                        put("gender", "female")
                        put("samplingRate", 22050)
                    })
                })
            })
            put("inputData", JSONObject().apply {
                put("input", JSONArray().apply {
                    put(JSONObject().apply { put("source", text) })
                })
            })
        }
    }

    @Throws(IOException::class)
    private fun postDirect(jsonBody: String): String {
        val request = Request.Builder()
            .url(directComputeUrl)
            .addHeader("Authorization", aiConfig.bhashiniInferenceKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(jsonType, jsonBody))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.w("Bhashini", "Direct compute HTTP ${response.code}: ${body.take(500)}")
                throw IOException("HTTP ${response.code}: ${body.take(300)}")
            }
            return body
        }
    }

    @Throws(IOException::class)
    private fun postConfig(jsonBody: String): String {
        // Config call needs userID + ulcaApiKey (both from My Profile)
        // For new portal, UDYAT KEY serves as both
        val userId = aiConfig.bhashiniUserId.ifBlank { aiConfig.bhashiniUlcaApiKey }
        val apiKey = aiConfig.bhashiniUlcaApiKey.ifBlank { aiConfig.bhashiniUserId }.ifBlank { aiConfig.bhashiniInferenceKey }
        
        val request = Request.Builder()
            .url(configUrl)
            .addHeader("userID", userId)
            .addHeader("ulcaApiKey", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(jsonType, jsonBody))
            .build()
        Log.d("Bhashini", "Config call with userID ${userId.take(6)}... and apiKey ${apiKey.take(6)}...")
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Config HTTP ${response.code}: ${body.take(300)}")
            }
            return body
        }
    }

    @Throws(IOException::class)
    private fun postToUrl(url: String, authKey: String, jsonBody: String): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", authKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(jsonType, jsonBody))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Compute HTTP ${response.code}: ${body.take(300)}")
            }
            return body
        }
    }

    private fun parseConfigResponse(response: String): Pair<String?, String?> {
        return try {
            val resp = JSONObject(response)
            val endpoint = resp.optJSONObject("pipelineInferenceAPIEndPoint") ?: return Pair(null, null)
            val callbackUrl = endpoint.optString("callbackUrl", directComputeUrl)
            val inferenceKeyObj = endpoint.optJSONObject("inferenceApiKey")
            val inferenceKey = inferenceKeyObj?.optString("value") ?: aiConfig.bhashiniInferenceKey
            Pair(callbackUrl, inferenceKey)
        } catch (e: Exception) {
            Log.w("Bhashini", "Config parse failed: ${e.message}")
            Pair(null, null)
        }
    }

    private fun postForAudioDirect(jsonBody: String): ByteArray? {
        val responseText = postDirect(jsonBody)
        return parseAudioResponse(responseText)
    }

    private fun parseAudioResponse(responseText: String): ByteArray? {
        return try {
            val resp = JSONObject(responseText)
            val pipeline = resp.optJSONArray("pipelineResponse") ?: return null
            if (pipeline.length() == 0) return null
            val first = pipeline.getJSONObject(0)
            var audioArray = first.optJSONArray("audio")
            if (audioArray == null) audioArray = first.optJSONArray("output")
            if (audioArray == null || audioArray.length() == 0) return null
            val obj = audioArray.getJSONObject(0)
            var base64 = obj.optString("audioContent", "")
            if (base64.isBlank()) base64 = obj.optString("audio", "")
            if (base64.isBlank()) return null
            android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            Log.w("Bhashini", "Audio parse failed: ${e.message}")
            null
        }
    }

    private fun parseNmtResponse(response: String): String? {
        return try {
            val resp = JSONObject(response)
            val pipelineResponse = resp.optJSONArray("pipelineResponse") ?: return null
            if (pipelineResponse.length() == 0) return null
            val output = pipelineResponse.getJSONObject(0).optJSONArray("output") ?: return null
            if (output.length() == 0) return null
            output.getJSONObject(0).optString("target") ?: output.getJSONObject(0).optString("source")
        } catch (e: Exception) {
            Log.w("Bhashini", "Parse NMT failed: ${e.message}")
            null
        }
    }

    private fun mapToBhashiniLang(code: String): String {
        return when (code.lowercase()) {
            "ta" -> "ta"
            "hi" -> "hi"
            "te" -> "te"
            "kn" -> "kn"
            "ml" -> "ml"
            "en" -> "en"
            "ta-in" -> "ta"
            "hi-in" -> "hi"
            "te-in" -> "te"
            "kn-in" -> "kn"
            "ml-in" -> "ml"
            "en-in" -> "en"
            else -> code.lowercase().take(2)
        }
    }
}
