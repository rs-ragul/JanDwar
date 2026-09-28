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
    private val aiConfig: AiConfig
) : NluExtractor {

    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val groqUrl = "https://api.groq.com/openai/v1/chat/completions"
    private val modelChain = listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b")

    fun isConfigured(): Boolean = aiConfig.groqEnabled()

    override fun extract(text: String, langCode: String, currentProfile: ProfileFragment?, isOnline: Boolean, callback: NluExtractor.Callback) {
        if (!isConfigured()) {
            mainHandler.post { callback.onError("Groq API key missing. Add groq_api_key to config.json. No deterministic fallback - you want real Groq only.") }
            return
        }
        if (!isOnline) {
            mainHandler.post { callback.onError("Offline - Groq needs internet. No deterministic fallback as per your request.") }
            return
        }
        tryModel(text, langCode, currentProfile, callback, 0)
    }

    private fun tryModel(text: String, langCode: String, currentProfile: ProfileFragment?, callback: NluExtractor.Callback, modelIndex: Int) {
        if (modelIndex >= modelChain.size) {
            mainHandler.post { callback.onError("All Groq models failed (gpt-oss-120b, gpt-oss-20b). Check API key, internet, rate limit 1000/day.") }
            return
        }
        val model = modelChain[modelIndex]
        val userPrompt = buildPureGroqPrompt(text, currentProfile, langCode)
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", "You are JanDwar, a warm, empathetic livelihood companion for SC communities under PM-AJAY. You are a real conversational AI, NOT a keyword matcher. You speak like a trusted village elder, naturally, in user's language. You understand ANY course, ANY degree, ANY job, ANY language with real intelligence. ONE-TO-ONE natural conversation. Respond ONLY with valid JSON. DO NOT USE ANY TOOLS AT ALL.") })
                put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
            })
            put("temperature", 0.8)
            put("top_p", 0.95)
            put("max_tokens", 1000)
            put("reasoning_effort", "low")
            put("reasoning_format", "hidden")
            put("response_format", JSONObject().apply {
                put("type", "json_schema")
                put("json_schema", JSONObject().apply {
                    put("name", "jandwar_conversation")
                    put("strict", true)
                    put("schema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("edu", JSONObject().apply { put("type", JSONArray().apply { put("string"); put("null") }); put("enum", JSONArray().apply { put("none"); put("class5"); put("class8"); put("class10"); put("class12"); put("graduate"); put(JSONObject.NULL) }) })
                            put("familyOccupation", JSONObject().apply { put("type", JSONArray().apply { put("string"); put("null") }) })
                            put("currentLivelihood", JSONObject().apply { put("type", JSONArray().apply { put("string"); put("null") }) })
                            put("interests", JSONObject().apply { put("type", "array"); put("items", JSONObject().apply { put("type", "string") }) })
                            put("skills", JSONObject().apply { put("type", "array"); put("items", JSONObject().apply { put("type", "string") }) })
                            put("preference", JSONObject().apply { put("type", JSONArray().apply { put("string"); put("null") }); put("enum", JSONArray().apply { put("pref_self"); put("pref_wage"); put(JSONObject.NULL) }) })
                            put("district", JSONObject().apply { put("type", JSONArray().apply { put("string"); put("null") }) })
                            put("mobility", JSONObject().apply { put("type", JSONArray().apply { put("string"); put("null") }); put("enum", JSONArray().apply { put("local"); put("district"); put("state"); put(JSONObject.NULL) }) })
                            put("physicalConstraints", JSONObject().apply { put("type", JSONArray().apply { put("string"); put("null") }) })
                            put("localOpportunity", JSONObject().apply { put("type", JSONArray().apply { put("string"); put("null") }) })
                            put("next_question_native", JSONObject().apply { put("type", "string") })
                            put("is_complete", JSONObject().apply { put("type", "boolean") })
                            put("final_summary", JSONObject().apply { put("type", JSONArray().apply { put("string"); put("null") }) })
                        })
                        put("required", JSONArray().apply { put("edu"); put("familyOccupation"); put("currentLivelihood"); put("interests"); put("skills"); put("preference"); put("district"); put("mobility"); put("physicalConstraints"); put("localOpportunity"); put("next_question_native"); put("is_complete"); put("final_summary") })
                        put("additionalProperties", false)
                    })
                })
            })
        }
        val request = Request.Builder().url(groqUrl).addHeader("Authorization", "Bearer ${aiConfig.groqApiKey}").addHeader("Content-Type", "application/json").post(RequestBody.create(jsonType, body.toString())).build()
        Log.d("GroqExtractor", "PURE GROQ try $model | text: $text")
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { mainHandler.post { tryModel(text, langCode, currentProfile, callback, modelIndex + 1) } }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.w("GroqExtractor", "Model $model HTTP ${response.code}: $responseBody")
                        if (modelIndex + 1 < modelChain.size) mainHandler.post { tryModel(text, langCode, currentProfile, callback, modelIndex + 1) }
                        else mainHandler.post { callback.onError("Groq HTTP ${response.code}: $responseBody") }
                        return
                    }
                    val resp = JSONObject(responseBody)
                    var content = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                    if (content.startsWith("```json")) content = content.substring(7).trim()
                    else if (content.startsWith("```")) content = content.substring(3).trim()
                    if (content.endsWith("```")) content = content.substring(0, content.length - 3).trim()
                    val start = content.indexOf('{'); val end = content.lastIndexOf('}')
                    if (start >= 0 && end > start) content = content.substring(start, end + 1)
                    Log.d("GroqExtractor", "PURE GROQ success $model: $content")
                    val frag = ProfileFragment()
                    try {
                        val outJson = JSONObject(content)
                        if (outJson.has("edu") && !outJson.isNull("edu")) { val v = outJson.optString("edu"); if (v.isNotBlank() && v != "null") frag.edu = v }
                        if (outJson.has("preference") && !outJson.isNull("preference")) { val v = outJson.optString("preference"); if (v.isNotBlank() && v != "null") frag.preference = v }
                        if (outJson.has("district") && !outJson.isNull("district")) { val v = outJson.optString("district"); if (v.isNotBlank() && v != "null") frag.district = v }
                        if (outJson.has("mobility") && !outJson.isNull("mobility")) { val v = outJson.optString("mobility"); if (v.isNotBlank() && v != "null") frag.mobility = v }
                        if (outJson.has("familyOccupation") && !outJson.isNull("familyOccupation")) { val v = outJson.optString("familyOccupation"); if (v.isNotBlank() && v != "null") frag.familyOccupation = v }
                        if (outJson.has("currentLivelihood") && !outJson.isNull("currentLivelihood")) { val v = outJson.optString("currentLivelihood"); if (v.isNotBlank() && v != "null") frag.currentLivelihood = v }
                        if (outJson.has("physicalConstraints") && !outJson.isNull("physicalConstraints")) { val v = outJson.optString("physicalConstraints"); if (v.isNotBlank() && v != "null") frag.physicalConstraints = v }
                        if (outJson.has("localOpportunity") && !outJson.isNull("localOpportunity")) { val v = outJson.optString("localOpportunity"); if (v.isNotBlank() && v != "null") frag.localOpportunity = v }
                        if (outJson.has("interests") && !outJson.isNull("interests")) { val arr = outJson.optJSONArray("interests"); if (arr != null) for (i in 0 until arr.length()) { val v = arr.optString(i); if (v.isNotBlank() && v != "null") frag.interests.add(v) } }
                        if (outJson.has("skills") && !outJson.isNull("skills")) { val arr = outJson.optJSONArray("skills"); if (arr != null) for (i in 0 until arr.length()) { val v = arr.optString(i); if (v.isNotBlank() && v != "null") frag.skills.add(v) } }
                        if (outJson.has("next_question_native") && !outJson.isNull("next_question_native")) frag.nextQuestion = outJson.optString("next_question_native")
                        if (outJson.has("final_summary") && !outJson.isNull("final_summary")) { val summary = outJson.optString("final_summary"); if (summary.isNotBlank() && summary != "null" && outJson.optBoolean("is_complete", false)) { frag.nextQuestion = summary } }
                    } catch (e: Exception) {
                        Log.e("GroqExtractor", "JSON parse fail: ${e.message} content: $content")
                        mainHandler.post { callback.onError("Groq JSON parse failed: ${e.message}. Raw: $content") }
                        return
                    }
                    mainHandler.post { callback.onResult(frag) }
                } catch (e: Exception) {
                    Log.e("GroqExtractor", "Exception: ${e.message}")
                    if (modelIndex + 1 < modelChain.size) mainHandler.post { tryModel(text, langCode, currentProfile, callback, modelIndex + 1) }
                    else mainHandler.post { callback.onError("Groq exception: ${e.message}") }
                }
            }
        })
    }

    fun explainResultsWithGroq(profile: ProfileFragment, topResults: List<String>, langCode: String, callback: (String) -> Unit) {
        if (!isConfigured()) { callback("Groq API key missing - cannot explain results with real AI"); return }
        val resultsText = topResults.joinToString("\n")
        val prompt = """
You are JanDwar, warm village elder. User profile: edu=${profile.edu}, family=${profile.familyOccupation}, current=${profile.currentLivelihood}, interests=${profile.interests}, district=${profile.district}.

We matched top 3 livelihood options from 516 QPs (ordinary code filtering, Excel-like, no AI):
$resultsText

Task: Explain these top 3 recommendations warmly in language $langCode, like talking to a friend. For each, say why it fits their background, what they will learn, where centre is. Keep it natural, not form-like. 1 paragraph per option, warm, encouraging. MUST be in $langCode. This will be spoken by TTS, so no markdown, no emojis, just natural speech.

Respond ONLY with the spoken explanation in $langCode, no JSON.
        """.trimIndent()
        val body = JSONObject().apply {
            put("model", modelChain[0])
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", "You are JanDwar, warm village elder explaining livelihood options naturally in user's language. No markdown, no JSON, just natural speech for TTS. DO NOT USE TOOLS.") })
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
            put("temperature", 0.8)
            put("max_tokens", 1200)
            put("reasoning_effort", "low")
            put("reasoning_format", "hidden")
        }
        val request = Request.Builder().url(groqUrl).addHeader("Authorization", "Bearer ${aiConfig.groqApiKey}").addHeader("Content-Type", "application/json").post(RequestBody.create(jsonType, body.toString())).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { mainHandler.post { callback("I found ${topResults.size} great options for you based on your profile. Let me show you details on next page.") } }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) { mainHandler.post { callback("I found ${topResults.size} excellent options that fit your background. Details are on next page.") }; return }
                    val resp = JSONObject(responseBody)
                    var content = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                    Log.d("GroqExtractor", "Explain results success: $content")
                    mainHandler.post { callback(content) }
                } catch (e: Exception) { mainHandler.post { callback("Wonderful! I found ${topResults.size} options perfect for you. Let me explain on next page.") } }
            }
        })
    }

    private fun buildPureGroqPrompt(userText: String, profile: ProfileFragment?, langCode: String): String {
        val currentState = profile?.let { "Already collected: edu=${it.edu ?: "none"}, family=${it.familyOccupation ?: "none"}, current=${it.currentLivelihood ?: "none"}, interests=${it.interests}, pref=${it.preference ?: "none"}, district=${it.district ?: "none"}, mobility=${it.mobility ?: "none"}, skills=${it.skills}" } ?: "No data yet - first conversation turn"
        val safeText = if (userText.isBlank()) "FIRST TURN: User just opened app. Greet warmly like village elder in $langCode, no forms, no pressure. Ask naturally: how far did you study? Talk like friend. 1 sentence only." else userText
        return """
You are JanDwar - you have ONE-TO-ONE real conversation with user, NOT a form.

YOUR ROLE:
- Warm village elder, trusted companion for SC communities under PM-AJAY
- You speak naturally in language $langCode, like talking to a friend
- You understand ANY course, ANY degree, ANY job, ANY language with REAL intelligence - ECE, CSE, MBA, BCA, BBA, B.Com, Nursing, Pharmacy, Fashion Design, Hotel Management, Data Science, Cyber Security, ANYTHING - not keyword matching
- You ask what you need naturally, not from fixed list. You need: education, family traditional occupation, current livelihood, interests, preference (own business vs job), district, travel limit/mobility. But ask in your own natural way, one at a time, warmly.
- NEVER ask for info already in Already collected
- Acknowledge what user JUST said warmly before asking next

Current state: $currentState
User language code: $langCode -> next_question_native MUST be in $langCode, warm, natural, 1 sentence, like village elder
User said: "$safeText"

TASK:
1. Extract from THIS turn only what user said now (null if not mentioned now)
2. Generate next_question_native: warm acknowledgment of THIS turn + natural question for ONE missing field in $langCode. If all info collected, set is_complete=true and final_summary = warm closing thank you in $langCode
3. is_complete = true only if you have education, family, current, interests, preference, district, mobility

Mapping (use intelligence, not fixed list):
- Any college degree, engineering, bachelor's, master's, diploma above 12th, professional course, ANY course name/abbreviation -> edu=graduate
- 12th/HSC/+2/Plus Two -> class12
- 10th/SSLC -> class10
- 8th -> class8
- Below 8th/read/write -> class5
- Any "studying X", "pursuing X", "doing X", "X student" -> currentLivelihood=Student - X, skills=[X]
- Interests: infer intelligently: tech/engineering/computer/IT/electronics/AI/data/cyber/software -> machine; farming/dairy/cattle/goat/poultry/agri -> farming/dairy/etc; fashion/tailoring/textile/design -> tailor/textile; food/cooking/hotel/catering -> food; construction/civil/mason/electrical/plumbing -> construction
- Preference: own/self/business/entrepreneur/shop -> pref_self; job/wage/salary/company/placement -> pref_wage
- Mobility: local/village/nearby/cannot travel -> local; district -> district; anywhere/state/far -> state

Return ONLY JSON:
{
  "edu": "graduate"/"class12"/"class10"/"class8"/"class5"/null,
  "familyOccupation": string or null,
  "currentLivelihood": string or null,
  "interests": ["machine"/"farming"/etc],
  "skills": ["exact course name"],
  "preference": "pref_self"/"pref_wage" or null,
  "district": string or null,
  "mobility": "local"/"district"/"state" or null,
  "physicalConstraints": string or null,
  "localOpportunity": string or null,
  "next_question_native": "warm natural 1-sentence question in $langCode",
  "is_complete": false,
  "final_summary": null or "warm closing in $langCode if complete"
}
        """.trimIndent()
    }
}
