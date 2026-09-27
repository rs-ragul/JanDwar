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

@Singleton
class GroqExtractor @Inject constructor(
    private val aiConfig: AiConfig,
    private val deterministicParser: DeterministicParser
) : NluExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val groqUrl = "https://api.groq.com/openai/v1/chat/completions"

    // Robust model chain - handles deprecations after Aug 16 2026
    // Groq deprecation notice: llama-3.1-8b-instant deprecated 08/16/26 -> replacement openai/gpt-oss-20b
    // See https://console.groq.com/docs/deprecations
    private val modelChain = listOf(
        "openai/gpt-oss-20b",                              // Fast, cheap, recommended replacement for 8b (Aug 2026)
        "openai/gpt-oss-120b",                             // Larger, recommended for versatile
        "meta-llama/llama-4-scout-17b-16e-instruct",       // Llama 4 Scout - current production
        "qwen/qwen3-32b",                                  // Qwen3 32B - multilingual strong
        "llama-3.3-70b-versatile",                         // Legacy but still may work
        "llama-3.1-8b-instant"                             // Legacy fallback - try last
    )

    fun isConfigured(): Boolean = aiConfig.groqEnabled()

    override fun extract(
        text: String,
        langCode: String,
        currentProfile: ProfileFragment?,
        isOnline: Boolean,
        callback: NluExtractor.Callback
    ) {
        if (!isConfigured()) {
            // Not configured - fallback silently to deterministic (no error shown to user)
            val fallback = deterministicParser.parse(text)
            callback.onResult(fallback)
            return
        }

        tryModel(text, langCode, currentProfile, callback, modelIndex = 0)
    }

    private fun tryModel(
        text: String,
        langCode: String,
        currentProfile: ProfileFragment?,
        callback: NluExtractor.Callback,
        modelIndex: Int
    ) {
        if (modelIndex >= modelChain.size) {
            Log.w("GroqExtractor", "All Groq models failed, falling back to deterministic")
            val fallback = deterministicParser.parse(text)
            mainHandler.post { callback.onResult(fallback) }
            return
        }

        val model = modelChain[modelIndex]
        val prompt = buildEmpatheticPrompt(text, currentProfile, langCode)

        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are JanDwar, an empathetic AI livelihood companion for SC communities under PM-AJAY. You speak warmly like a trusted village elder, not like a form. You understand low-literacy users, regional languages, and you never sound administrative. You respond ONLY with valid JSON. No markdown, no explanation.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.6)
            put("max_tokens", 600)
            if (!model.startsWith("openai/gpt-oss")) {
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }
        }

        val request = Request.Builder()
            .url(groqUrl)
            .addHeader("Authorization", "Bearer ${aiConfig.groqApiKey}")
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(jsonType, body.toString()))
            .build()

        Log.d("GroqExtractor", "Trying model: $model")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("GroqExtractor", "Network failure for $model: ${e.message}")
                mainHandler.post {
                    tryModel(text, langCode, currentProfile, callback, modelIndex + 1)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errMsg = try {
                            JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                        } catch (_: Exception) {
                            "HTTP ${response.code}: $responseBody"
                        }

                        Log.w("GroqExtractor", "Model $model failed: $errMsg")

                        val isModelError = errMsg.contains("does not exist", ignoreCase = true) ||
                                errMsg.contains("decommissioned", ignoreCase = true) ||
                                errMsg.contains("has been decommissioned", ignoreCase = true) ||
                                errMsg.contains("model_not_found", ignoreCase = true) ||
                                errMsg.contains("not found", ignoreCase = true) ||
                                response.code == 404 ||
                                (response.code == 400 && errMsg.contains("model", ignoreCase = true))

                        if (isModelError && modelIndex + 1 < modelChain.size) {
                            mainHandler.post {
                                tryModel(text, langCode, currentProfile, callback, modelIndex + 1)
                            }
                        } else if (response.code == 429 || errMsg.contains("rate", ignoreCase = true)) {
                            Log.w("GroqExtractor", "Rate limited, using offline fallback")
                            val fallback = deterministicParser.parse(text)
                            mainHandler.post { callback.onResult(fallback) }
                        } else {
                            if (modelIndex + 1 < modelChain.size) {
                                mainHandler.post {
                                    tryModel(text, langCode, currentProfile, callback, modelIndex + 1)
                                }
                            } else {
                                val fallback = deterministicParser.parse(text)
                                mainHandler.post { callback.onResult(fallback) }
                            }
                        }
                        return
                    }

                    val resp = JSONObject(responseBody)
                    var content = resp.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()

                    if (content.startsWith("```json")) content = content.substring(7).trim()
                    else if (content.startsWith("```")) content = content.substring(3).trim()
                    if (content.endsWith("```")) content = content.substring(0, content.length - 3).trim()

                    val start = content.indexOf('{')
                    val end = content.lastIndexOf('}')
                    if (start >= 0 && end > start) content = content.substring(start, end + 1)

                    val frag = ProfileFragment()
                    try {
                        val outJson = JSONObject(content)
                        if (outJson.has("edu") && !outJson.isNull("edu")) frag.edu = outJson.optString("edu").takeIf { it.isNotBlank() && it != "null" }
                        if (outJson.has("preference") && !outJson.isNull("preference")) frag.preference = outJson.optString("preference").takeIf { it.isNotBlank() && it != "null" }
                        if (outJson.has("district") && !outJson.isNull("district")) frag.district = outJson.optString("district").takeIf { it.isNotBlank() && it != "null" }
                        if (outJson.has("mobility") && !outJson.isNull("mobility")) frag.mobility = outJson.optString("mobility").takeIf { it.isNotBlank() && it != "null" }
                        if (outJson.has("familyOccupation") && !outJson.isNull("familyOccupation")) frag.familyOccupation = outJson.optString("familyOccupation").takeIf { it.isNotBlank() && it != "null" }
                        if (outJson.has("currentLivelihood") && !outJson.isNull("currentLivelihood")) frag.currentLivelihood = outJson.optString("currentLivelihood").takeIf { it.isNotBlank() && it != "null" }
                        if (outJson.has("physicalConstraints") && !outJson.isNull("physicalConstraints")) frag.physicalConstraints = outJson.optString("physicalConstraints").takeIf { it.isNotBlank() && it != "null" }
                        if (outJson.has("localOpportunity") && !outJson.isNull("localOpportunity")) frag.localOpportunity = outJson.optString("localOpportunity").takeIf { it.isNotBlank() && it != "null" }
                        if (outJson.has("interests") && !outJson.isNull("interests")) {
                            val arr = outJson.optJSONArray("interests")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val v = arr.optString(i)
                                    if (v.isNotBlank()) frag.interests.add(v)
                                }
                            }
                        }
                        if (outJson.has("skills") && !outJson.isNull("skills")) {
                            val arr = outJson.optJSONArray("skills")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val v = arr.optString(i)
                                    if (v.isNotBlank()) frag.skills.add(v)
                                }
                            }
                        }
                        if (outJson.has("next_question_native") && !outJson.isNull("next_question_native")) {
                            frag.nextQuestion = outJson.optString("next_question_native")
                        } else if (outJson.has("next_question") && !outJson.isNull("next_question")) {
                            frag.nextQuestion = outJson.optString("next_question")
                        }
                    } catch (e: Exception) {
                        Log.w("GroqExtractor", "JSON parse failed, using deterministic: ${e.message}")
                        val fallback = deterministicParser.parse(text)
                        mainHandler.post { callback.onResult(fallback) }
                        return
                    }

                    val validated = deterministicParser.validate(frag, text)
                    mainHandler.post { callback.onResult(validated) }
                } catch (e: Exception) {
                    Log.w("GroqExtractor", "Exception: ${e.message}, trying next model")
                    if (modelIndex + 1 < modelChain.size) {
                        mainHandler.post {
                            tryModel(text, langCode, currentProfile, callback, modelIndex + 1)
                        }
                    } else {
                        val fallback = deterministicParser.parse(text)
                        mainHandler.post { callback.onResult(fallback) }
                    }
                }
            }
        })
    }

    private fun buildEmpatheticPrompt(userText: String, profile: ProfileFragment?, langCode: String): String {
        val missing = mutableListOf<String>()
        if (profile == null) {
            missing.addAll(listOf("education", "family occupation", "current livelihood", "interests/skills", "preference (self vs wage)", "district", "mobility"))
        } else {
            if (!profile.hasEdu()) missing.add("education (how far studied)")
            if (!profile.hasFamilyOccupation()) missing.add("family/traditional occupation")
            if (!profile.hasCurrentLivelihood()) missing.add("current livelihood/work")
            if (!profile.hasInterests()) missing.add("skills and interests")
            if (!profile.hasPref()) missing.add("preference for self-employment vs wage job")
            if (!profile.hasDistrict()) missing.add("district in Tamil Nadu")
            if (!profile.hasMobility()) missing.add("mobility and physical constraints")
        }

        val safeText = if (userText.isBlank()) "(User just started, greet warmly and ask first question about education in a friendly, non-form way. Keep it conversational, not like a survey.)" else userText

        val currentState = profile?.let {
            "Current collected: edu=${it.edu}, family=${it.familyOccupation}, current=${it.currentLivelihood}, interests=${it.interests}, pref=${it.preference}, district=${it.district}, mobility=${it.mobility}, skills=${it.skills}"
        } ?: "No data collected yet - this is the very first turn"

        return """
You are JanDwar, a warm, empathetic livelihood companion for SC beneficiaries under PM-AJAY. You are NOT a form or survey bot. You are a trusted elder from the village who genuinely cares.

CRITICAL RULES FOR NATURAL CONVERSATION:
- ALWAYS acknowledge what user JUST said before asking next question. Show you listened!
  Example: User says "I study computer science cyber security" -> You: "Wow, Computer Science with Cyber Security - that's fantastic! So relevant today!"
  Then naturally transition to next question.
- NEVER ask for information user already gave. If they said "I am a student", don't ask "what do you do currently?" again.
- NEVER sound like reading from a list. Vary your phrasing. Be conversational, use "Oh", "Got it", "That's great", "I see"
- If user says "computer science engineering", you MUST extract: edu=graduate, currentLivelihood=Student, interests=[machine], skills=[Computer Science, Cyber Security]
- If user says "government employee" for family, extract familyOccupation=Government employee
- Keep questions SHORT (1-2 sentences) but WARM and CONTEXTUAL
- If user is a student, ask about family background to understand roots, not "what do you do currently?" again
- For interests: if user is into computers/tech, acknowledge that and ask what type of work they enjoy beyond studies

Problem Statement Context:
- User faces low digital literacy, language constraints
- Must avoid mismatch between training and aspirations
- Must feel like talking to a caring human, not a government form
- Must support Tamil, Hindi, Telugu, Kannada, Malayalam, English

Current state: $currentState
Missing fields: ${missing.joinToString(", ")}

User said (in $langCode or translated): "$safeText"

Your tasks:
1. Extract ONLY if clearly mentioned in user's CURRENT answer (use null if not mentioned in this turn, don't hallucinate):
   - edu: MUST be one of ["none","class5","class8","class10","class12","graduate"] or null. Map: B.Tech, BE, BSc, Computer Science, Engineering, Degree, College, Graduate -> "graduate". 12th/HSC/+2 -> "class12". 10th/SSLC -> "class10"
   - familyOccupation: String describing family's traditional occupation or null. Examples: "Government employee", "Farming", "Daily wage labour"
   - currentLivelihood: String describing what user currently does or null. If user says "I am a student" or "studying in computer science" -> "Student" or "Student - Computer Science"
   - interests: array from ["dairy","cattle","goat","poultry","farming","food","machine","textile","construction","tailor"] or []. Map computer, IT, software, cyber security, programming, engineering -> ["machine"]
   - skills: array of specific skills like ["Computer Science", "Cyber Security"] or []
   - preference: MUST be ["pref_self","pref_wage"] or null
   - district: Tamil Nadu district name or null
   - mobility: MUST be ["local","district","state"] or null
   - physicalConstraints: String or null
   - localOpportunity: String or null

2. Generate next_question_native:
   - Format: [Warm acknowledgment of what user JUST said] + [Natural transition to next missing field]
   - MUST be in language "$langCode" - never English if lang is ta/hi/te/kn/ml
   - Examples in English (adapt to $langCode):
     * If user said they study CSE cyber security and missing familyOccupation: "Wow, Cyber Security - that's super relevant today! What does your family traditionally do for work? Government job, farming, something else?"
     * If user said family is government employee and missing interests: "Government employee family - got it! What kind of work interests you personally? Tech, hands-on work, creative things?"
   - Sound like a caring elder, NOT "Enter family occupation"
   - If all fields collected, generate warm closing summarizing: "Thank you! So you're graduate, family in government, student in CSE... Let me find best options"
   - Never mention you are AI

Respond ONLY valid JSON:
{
  "edu": "graduate" or null,
  "familyOccupation": "Government employee" or null,
  "currentLivelihood": "Student" or null,
  "interests": ["machine"],
  "skills": ["Computer Science", "Cyber Security"],
  "preference": null,
  "district": null,
  "mobility": null,
  "physicalConstraints": null,
  "localOpportunity": null,
  "next_question_native": "warm contextual question with acknowledgment in $langCode"
}
        """.trimIndent()
    }
}
