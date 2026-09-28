package `in`.jandwar.app.ai

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TieredNluExtractor @Inject constructor(
    private val groqExtractor: GroqExtractor,
    private val bhashiniGateway: BhashiniGateway
) : NluExtractor {

    override fun extract(
        text: String,
        langCode: String,
        currentProfile: ProfileFragment?,
        isOnline: Boolean,
        callback: NluExtractor.Callback
    ) {
        if (text.isBlank()) {
            if (isOnline && groqExtractor.isConfigured()) {
                groqExtractor.extract(text, langCode, currentProfile, true, callback)
            } else {
                val frag = ProfileFragment()
                frag.nextQuestion = when (langCode) {
                    "ta" -> "வணக்கம்! நான் JanDwar, Groq AI (gpt-oss-120b) மூலம் இயங்குகிறேன். உண்மையான AI உடன் பேசுகிறீர்கள், deterministic அல்ல. எவ்வளவு படிச்சிருக்கீங்க? ECE, CSE, MBA, BCA, எதுவாக இருந்தாலும் சொல்லுங்க!"
                    "hi" -> "नमस्ते! मैं JanDwar, Groq AI (gpt-oss-120b) से चल रहा हूँ। आप real AI से बात कर रहे हैं। कितना पढ़े हो?"
                    else -> "Hey! I'm JanDwar, powered by REAL Groq AI (gpt-oss-120b) - no deterministic, no keyword matching. I understand ANY course naturally - ECE, CSE, MBA, BCA, Nursing, anything! How far did you study?"
                }
                callback.onResult(frag)
            }
            return
        }

        if (isOnline && groqExtractor.isConfigured()) {
            if (langCode != "en" && bhashiniGateway.isAvailable()) {
                bhashiniGateway.translate(text, langCode, "en", object : BhashiniGateway.TranslateCallback {
                    override fun onResult(translated: String) {
                        groqExtractor.extract(translated, langCode, currentProfile, true, object : NluExtractor.Callback {
                            override fun onResult(fragment: ProfileFragment) { callback.onResult(fragment) }
                            override fun onError(reason: String) {
                                Log.e("TieredNlu", "PURE GROQ failed: $reason")
                                val errorFrag = ProfileFragment()
                                errorFrag.nextQuestion = "⚠️ PURE GROQ FAILED: $reason | You ARE talking to real Groq AI (gpt-oss-120b), not deterministic. Check API key and internet."
                                callback.onResult(errorFrag)
                            }
                        })
                    }
                    override fun onError(reason: String) {
                        groqExtractor.extract(text, langCode, currentProfile, true, object : NluExtractor.Callback {
                            override fun onResult(fragment: ProfileFragment) { callback.onResult(fragment) }
                            override fun onError(reason2: String) {
                                val errorFrag = ProfileFragment()
                                errorFrag.nextQuestion = "⚠️ GROQ FAILED: $reason2 | Real Groq error, no deterministic fallback."
                                callback.onResult(errorFrag)
                            }
                        })
                    }
                })
            } else {
                groqExtractor.extract(text, langCode, currentProfile, true, object : NluExtractor.Callback {
                    override fun onResult(fragment: ProfileFragment) { callback.onResult(fragment) }
                    override fun onError(reason: String) {
                        Log.e("TieredNlu", "PURE GROQ failed: $reason")
                        val errorFrag = ProfileFragment()
                        errorFrag.nextQuestion = "⚠️ GROQ ERROR: $reason | This IS real Groq (gpt-oss-120b) failing, NOT deterministic. Check API key, internet, rate limit. NO deterministic fallback as requested."
                        callback.onResult(errorFrag)
                    }
                })
            }
        } else {
            val errorFrag = ProfileFragment()
            errorFrag.nextQuestion = when {
                !isOnline -> "⚠️ OFFLINE: Groq AI needs internet. No deterministic fallback as you requested - you want REAL Groq only. Please go online."
                !groqExtractor.isConfigured() -> "⚠️ Groq API key missing in config.json. You requested NO deterministic, only real Groq. Add groq_api_key to talk to real AI."
                else -> "⚠️ Groq not available. No deterministic fallback as requested."
            }
            callback.onResult(errorFrag)
        }
    }
}
