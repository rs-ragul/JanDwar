package `in`.jandwar.app.ai

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin Groq chat-completions client.
 *
 * Two jobs:
 *  1. [understand] — drive the empathetic interview and extract profile slots.
 *  2. [explainResults] — narrate the final recommendations warmly.
 *
 * Both always answer on the main thread and never throw: failures are reported
 * as `null` so [HybridNlu] can silently fall back to the on-device engine.
 */
@Singleton
class GroqClient @Inject constructor(
    private val aiConfig: AiConfig
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    private val main = Handler(Looper.getMainLooper())
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean = aiConfig.groqEnabled()

    private fun models(): List<String> =
        listOf(aiConfig.groqModel, FALLBACK_MODEL).distinct()

    // ── Interview ───────────────────────────────────────────────────────────

    fun understand(
        utterance: String,
        lang: String,
        current: ProfileFragment,
        askedSlot: ProfileFragment.Slot?,
        onDone: (ProfileFragment?) -> Unit
    ) {
        if (!isConfigured()) {
            main.post { onDone(null) }
            return
        }
        val system = buildSystemPrompt(lang)
        val user = buildUserPrompt(utterance, current, askedSlot, lang)
        chat(system, user, 0) { content ->
            val frag = content?.let { parseFragment(it) }
            main.post { onDone(frag) }
        }
    }

    private fun buildSystemPrompt(lang: String): String {
        val langName = LANG_NAMES[lang] ?: "English"
        return """
            You are JanDwar, a warm and patient livelihood counsellor for Scheduled Caste
            beneficiaries under the Government of India's PM-AJAY scheme (Grant-in-Aid component).
            You cover five states: Tamil Nadu, Kerala, Karnataka, Andhra Pradesh and
            Uttar Pradesh.

            Your job is to hold a gentle spoken conversation and quietly build the person's
            profile. Many users have low literacy and little digital experience.

            RULES
            - Reply ONLY with a single JSON object. No markdown, no commentary.
            - "next_question" MUST be written in $langName, in simple spoken words a villager
              understands. Never use jargon, never read out field names.
            - Ask about ONE thing at a time. Keep it under 30 words.
            - Briefly acknowledge what the person just said before asking the next thing.
              Be encouraging and never judgemental about low education or poverty.
            - Extract every field you can from the whole conversation so far, even if the
              person mentioned it in passing.
            - NEVER assume which state the person lives in, not from their language and not
              from a district name that sounds familiar. Ask for the state, then ask which
              district of that state. Naming the state back to them ("which district of
              Kerala?") is good practice.
            - Set "is_complete" to true only once education, state, district and either
              interests or family occupation are known, and you have asked about work
              preference and travel.

            FIELD VALUES (use exactly these tokens)
            - edu: none | class5 | class8 | class10 | class12 | iti_diploma | graduate
            - preference: pref_self | pref_wage
            - mobility: local | district | state
            - interests: any of dairy, cattle, goat, poultry, farming, food, machine,
              textile, construction, tailor
            - state: exactly one of Tamil Nadu | Kerala | Karnataka | Andhra Pradesh |
              Uttar Pradesh. Null until the person says it.
            - district: an English district name belonging to that state, e.g. Erode
              (Tamil Nadu), Ernakulam (Kerala), Ballari (Karnataka), Guntur (Andhra
              Pradesh), Varanasi (Uttar Pradesh)
            - familyOccupation / currentLivelihood / physicalConstraints: short English phrases
            - Use null for anything not yet known. Never invent values.

            JSON SHAPE
            {"edu":null,"preference":null,"mobility":null,"state":null,"district":null,
             "familyOccupation":null,"currentLivelihood":null,"physicalConstraints":null,
             "interests":[],"skills":[],"next_question":"...","is_complete":false}
        """.trimIndent()
    }

    private fun buildUserPrompt(
        utterance: String,
        current: ProfileFragment,
        askedSlot: ProfileFragment.Slot?,
        lang: String
    ): String {
        val known = JSONObject().apply {
            put("edu", current.edu ?: JSONObject.NULL)
            put("preference", current.preference ?: JSONObject.NULL)
            put("mobility", current.mobility ?: JSONObject.NULL)
            put("state", current.state ?: JSONObject.NULL)
            put("district", current.district ?: JSONObject.NULL)
            put("familyOccupation", current.familyOccupation ?: JSONObject.NULL)
            put("currentLivelihood", current.currentLivelihood ?: JSONObject.NULL)
            put("physicalConstraints", current.physicalConstraints ?: JSONObject.NULL)
            put("interests", JSONArray(current.interests))
            put("skills", JSONArray(current.skills))
        }
        val still = current.missingSlots().joinToString(", ") { it.name.lowercase() }
        return buildString {
            append("Known so far: ").append(known.toString()).append('\n')
            append("Still missing: ").append(if (still.isBlank()) "nothing" else still).append('\n')
            if (askedSlot != null) {
                append("The question you just asked was about: ")
                    .append(askedSlot.name.lowercase()).append('\n')
            }
            if (utterance.isBlank()) {
                append("The conversation is starting. Greet the person warmly in ")
                    .append(LANG_NAMES[lang] ?: "English")
                    .append(" and ask the first question.")
            } else {
                append("The person just said: \"").append(utterance).append("\"")
            }
        }
    }

    private fun parseFragment(content: String): ProfileFragment? = try {
        val json = JSONObject(extractJsonObject(content))
        ProfileFragment().apply {
            edu = json.optNullableString("edu")
            preference = json.optNullableString("preference")
            mobility = json.optNullableString("mobility")
            state = json.optNullableString("state")?.let { canonicalState(it) }
            district = json.optNullableString("district")
            familyOccupation = json.optNullableString("familyOccupation")
            currentLivelihood = json.optNullableString("currentLivelihood")
            physicalConstraints = json.optNullableString("physicalConstraints")
            json.optJSONArray("interests")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() && it != "null" }
                        ?.let { interests.add(it.lowercase()) }
                }
            }
            json.optJSONArray("skills")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() && it != "null" }?.let { skills.add(it) }
                }
            }
            nextQuestion = json.optNullableString("next_question")
            isComplete = json.optBoolean("is_complete", false)
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not parse Groq payload: ${e.message}")
        null
    }

    // ── Result narration ────────────────────────────────────────────────────

    fun explainResults(
        lang: String,
        profileSummary: String,
        topMatches: List<String>,
        onDone: (String?) -> Unit
    ) {
        if (!isConfigured() || topMatches.isEmpty()) {
            main.post { onDone(null) }
            return
        }
        val langName = LANG_NAMES[lang] ?: "English"
        val system = """
            You are JanDwar, a warm livelihood counsellor speaking aloud to a PM-AJAY
            beneficiary. Write a short spoken summary in $langName.

            RULES
            - Plain text only. No markdown, no bullets, no emoji, no numbers like "1)".
            - 60 to 90 words. It will be read aloud by a text-to-speech voice.
            - Warmly name each recommended course and say in one clause why it suits them.
            - Mention the training centre only if one is given.
            - Never invent a fee, subsidy, date or centre that is not listed.
            - End by inviting them to look at the details on the next screen.
        """.trimIndent()
        val user = buildString {
            append("The person: ").append(profileSummary).append('\n')
            append("Recommended courses:\n")
            topMatches.forEachIndexed { i, m -> append(i + 1).append(". ").append(m).append('\n') }
        }
        chat(system, user, 0, jsonMode = false) { content ->
            main.post { onDone(content?.trim()?.takeIf { it.isNotBlank() }) }
        }
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private fun chat(
        system: String,
        user: String,
        modelIndex: Int,
        jsonMode: Boolean = true,
        onContent: (String?) -> Unit
    ) {
        val list = models()
        if (modelIndex >= list.size) {
            onContent(null)
            return
        }
        val model = list[modelIndex]

        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", system) })
                put(JSONObject().apply { put("role", "user"); put("content", user) })
            })
            put("temperature", if (jsonMode) 0.4 else 0.7)
            put("max_tokens", 700)
            if (jsonMode) {
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer ${aiConfig.groqApiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "$model network failure: ${e.message}")
                chat(system, user, modelIndex + 1, jsonMode, onContent)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    val raw = try {
                        r.body?.string().orEmpty()
                    } catch (e: Exception) {
                        ""
                    }
                    if (!r.isSuccessful) {
                        Log.w(TAG, "$model HTTP ${r.code}: ${raw.take(200)}")
                        chat(system, user, modelIndex + 1, jsonMode, onContent)
                        return
                    }
                    val content = try {
                        JSONObject(raw)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .optString("content")
                    } catch (e: Exception) {
                        null
                    }
                    if (content.isNullOrBlank()) {
                        chat(system, user, modelIndex + 1, jsonMode, onContent)
                    } else {
                        onContent(content)
                    }
                }
            }
        })
    }

    private fun extractJsonObject(raw: String): String {
        var c = raw.trim()
        if (c.startsWith("```")) {
            c = c.removePrefix("```json").removePrefix("```").trim()
            if (c.endsWith("```")) c = c.removeSuffix("```").trim()
        }
        val start = c.indexOf('{')
        val end = c.lastIndexOf('}')
        return if (start >= 0 && end > start) c.substring(start, end + 1) else c
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key).trim()
        return v.takeIf { it.isNotBlank() && !it.equals("null", true) }
    }

    /**
     * Snaps whatever the model wrote onto one of the five catalogue states.
     *
     * An LLM will happily return "TN", "tamilnadu" or "Andhra". Those are all
     * correct answers that would not have matched a district list keyed on
     * "Tamil Nadu", so they are normalised here rather than discarded.
     */
    private fun canonicalState(raw: String): String? {
        val v = raw.trim().lowercase().replace(Regex("[^a-z]"), "")
        if (v.isEmpty()) return null
        return when {
            v.startsWith("tamil") || v == "tn" -> "Tamil Nadu"
            v.startsWith("keral") || v == "kl" -> "Kerala"
            v.startsWith("karnat") || v == "ka" -> "Karnataka"
            v.startsWith("andhra") || v == "ap" -> "Andhra Pradesh"
            v.startsWith("uttarp") || v == "up" -> "Uttar Pradesh"
            else -> null
        }
    }

    companion object {
        private const val TAG = "GroqClient"
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        private const val FALLBACK_MODEL = "llama-3.1-8b-instant"

        private val LANG_NAMES = mapOf(
            "en" to "English",
            "ta" to "Tamil",
            "hi" to "Hindi",
            "te" to "Telugu",
            "kn" to "Kannada",
            "ml" to "Malayalam"
        )
    }
}
