package `in`.jandwar.app.ai

import android.os.Handler
import android.os.Looper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BhashiniGateway @Inject constructor(
    private val aiConfig: AiConfig
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"

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
                val body = buildNmtBody(text, sourceLang, targetLang)
                val responseText = post(body.toString())
                val translated = parseNmtResponse(responseText) ?: text
                mainHandler.post { callback.onResult(translated) }
            } catch (e: Exception) {
                mainHandler.post { callback.onError(e.message ?: "Translate failed") }
            }
        }.start()
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
                val body = buildTtsBody(text, targetLang)
                val audio = postForAudio(body.toString())
                if (audio != null && audio.isNotEmpty()) {
                    mainHandler.post { callback.onAudio(audio) }
                } else {
                    mainHandler.post { callback.onError("No audio from Bhashini") }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError(e.message ?: "TTS failed") }
            }
        }.start()
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
    private fun post(jsonBody: String): String {
        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", aiConfig.bhashiniInferenceKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(jsonType, jsonBody))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $body")
            }
            return body
        }
    }

    private fun postForAudio(jsonBody: String): ByteArray? {
        val responseText = post(jsonBody)
        return try {
            val resp = JSONObject(responseText)
            val pipeline = resp.optJSONArray("pipelineResponse") ?: return null
            if (pipeline.length() == 0) return null
            val audioArray = pipeline.getJSONObject(0).optJSONArray("audio") ?: return null
            if (audioArray.length() == 0) return null
            val base64 = audioArray.getJSONObject(0).optString("audioContent", "")
            if (base64.isBlank()) return null
            android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseNmtResponse(response: String): String? {
        return try {
            val resp = JSONObject(response)
            resp.getJSONArray("pipelineResponse")
                .getJSONObject(0)
                .getJSONArray("output")
                .getJSONObject(0)
                .getString("target")
        } catch (e: Exception) {
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
