package `in`.jandwar.app.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class TieredNluExtractor @Inject constructor(
    private val groqExtractor: GroqExtractor,
    private val deterministicParser: DeterministicParser,
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
                frag.nextQuestion = generateEmpatheticQuestion(
                    currentProfile, langCode, lastUserText = "", lastFragment = null, isFirst = true
                )
                callback.onResult(frag)
            }
            return
        }

        if (isOnline && groqExtractor.isConfigured()) {
            if (langCode != "en" && bhashiniGateway.isAvailable()) {
                bhashiniGateway.translate(text, langCode, "en", object : BhashiniGateway.TranslateCallback {
                    override fun onResult(translated: String) {
                        groqExtractor.extract(translated, langCode, currentProfile, true, object : NluExtractor.Callback {
                            override fun onResult(fragment: ProfileFragment) {
                                val validated = deterministicParser.validate(fragment, text)
                                // Ensure natural question if Groq didn't provide one or it's repetitive
                                if (validated.nextQuestion.isNullOrBlank() || isRepetitiveQuestion(validated.nextQuestion!!, currentProfile)) {
                                    val merged = ProfileFragment()
                                    currentProfile?.let { merged.merge(it) }
                                    merged.merge(validated)
                                    validated.nextQuestion = generateEmpatheticQuestion(
                                        merged, langCode, lastUserText = text, lastFragment = validated, isFirst = false
                                    )
                                }
                                callback.onResult(validated)
                            }
                            override fun onError(reason: String) {
                                handleOfflineFallback(text, currentProfile, langCode, callback)
                            }
                        })
                    }
                    override fun onError(reason: String) {
                        groqExtractor.extract(text, langCode, currentProfile, true, object : NluExtractor.Callback {
                            override fun onResult(fragment: ProfileFragment) {
                                val validated = deterministicParser.validate(fragment, text)
                                if (validated.nextQuestion.isNullOrBlank()) {
                                    val merged = ProfileFragment()
                                    currentProfile?.let { merged.merge(it) }
                                    merged.merge(validated)
                                    validated.nextQuestion = generateEmpatheticQuestion(
                                        merged, langCode, lastUserText = text, lastFragment = validated
                                    )
                                }
                                callback.onResult(validated)
                            }
                            override fun onError(reason: String) {
                                handleOfflineFallback(text, currentProfile, langCode, callback)
                            }
                        })
                    }
                })
            } else {
                groqExtractor.extract(text, langCode, currentProfile, true, object : NluExtractor.Callback {
                    override fun onResult(fragment: ProfileFragment) {
                        val validated = deterministicParser.validate(fragment, text)
                        if (validated.nextQuestion.isNullOrBlank() || isRepetitiveQuestion(validated.nextQuestion!!, currentProfile)) {
                            val merged = ProfileFragment()
                            currentProfile?.let { merged.merge(it) }
                            merged.merge(validated)
                            validated.nextQuestion = generateEmpatheticQuestion(
                                merged, langCode, lastUserText = text, lastFragment = validated
                            )
                        }
                        callback.onResult(validated)
                    }
                    override fun onError(reason: String) {
                        handleOfflineFallback(text, currentProfile, langCode, callback)
                    }
                })
            }
        } else {
            handleOfflineFallback(text, currentProfile, langCode, callback)
        }
    }

    private fun handleOfflineFallback(
        text: String,
        currentProfile: ProfileFragment?,
        langCode: String,
        callback: NluExtractor.Callback
    ) {
        val result = deterministicParser.parse(text)
        val merged = ProfileFragment()
        currentProfile?.let { merged.merge(it) }
        merged.merge(result)

        if (!merged.isComplete()) {
            result.nextQuestion = generateEmpatheticQuestion(merged, langCode, lastUserText = text, lastFragment = result)
        } else {
            result.nextQuestion = generateClosingMessage(langCode, merged)
        }
        callback.onResult(result)
    }

    private fun isRepetitiveQuestion(question: String, profile: ProfileFragment?): Boolean {
        // Simple check: if question asks for field already filled, it's repetitive
        val qLower = question.lowercase()
        if (profile == null) return false
        if (profile.hasFamilyOccupation() && qLower.contains("family occupation")) return true
        if (profile.hasCurrentLivelihood() && qLower.contains("what do you do currently")) return true
        if (profile.hasEdu() && qLower.contains("how far did you study")) return true
        return false
    }

    private fun generateEmpatheticQuestion(
        profile: ProfileFragment?,
        lang: String,
        lastUserText: String = "",
        lastFragment: ProfileFragment? = null,
        isFirst: Boolean = false
    ): String {
        if (isFirst) {
            return when (lang) {
                "ta" -> "வணக்கம்! நான் JanDwar, உங்கள் தொழில் துணை. இயல்பாக பேசுங்கள் - எவ்வளவு படித்திருக்கிறீர்கள் என்று சொல்லுங்கள்?"
                "hi" -> "नमस्ते! मैं JanDwar हूँ, आपका साथी। आराम से बताइए - आपने कितनी पढ़ाई की है?"
                "te" -> "నమస్కారం! నేను JanDwar. మీరు ఎంత చదివారో చెప్పండి, సహజంగా మాట్లాడండి."
                "kn" -> "ನಮಸ್ಕಾರ! ನಾನು JanDwar. ನೀವು ಎಷ್ಟು ಓದಿದ್ದೀರಿ ಎಂದು ಸಹಜವಾಗಿ ಹೇಳಿ."
                "ml" -> "നമസ്കാരം! ഞാൻ JanDwar. നിങ്ങൾ എത്ര പഠിച്ചുവെന്ന് പറയൂ."
                else -> "Hey there! I'm JanDwar, your livelihood companion. No forms, no pressure - just tell me, how far did you study? Speak like you're talking to a friend."
            }
        }

        val missing = profile?.missingFields() ?: listOf("education")
        val lastTextLower = lastUserText.lowercase()

        // Generate acknowledgment based on what user just said
        val acknowledgment = generateAcknowledgment(lastFragment, lastUserText, lang)

        // Determine next field to ask, avoiding repetition
        val next = when {
            missing.contains("education") -> "education"
            missing.contains("familyOccupation") -> "familyOccupation"
            missing.contains("currentLivelihood") -> "currentLivelihood"
            missing.contains("interests") -> "interests"
            missing.contains("preference") -> "preference"
            missing.contains("mobility") -> "mobility"
            missing.contains("district") -> "district"
            else -> "closing"
        }

        // If user is a student studying CSE, we already know education and current livelihood
        // Don't ask current livelihood again if we know they are student
        val effectiveNext = if (next == "currentLivelihood" && profile?.hasCurrentLivelihood() == true) {
            // Skip to interests if we already know they are student
            if (missing.contains("interests")) "interests" else if (missing.contains("familyOccupation")) "familyOccupation" else "preference"
        } else next

        val questionPart = when (lang) {
            "ta" -> when (effectiveNext) {
                "education" -> listOf(
                    "உங்கள் படிப்பு பற்றி சொல்லுங்கள் - பள்ளி, கல்லூரி, ITI?",
                    "எவ்வளவு படித்திருக்கிறீர்கள்?"
                ).random()
                "familyOccupation" -> listOf(
                    "உங்கள் குடும்பத்தில் பரம்பரையாக என்ன தொழில்? விவசாயம், அரசு வேலை, தையல்?",
                    "உங்கள் அப்பா, அம்மா என்ன வேலை செய்கிறார்கள்?"
                ).random()
                "currentLivelihood" -> listOf(
                    "இப்போது நீங்கள் என்ன செய்கிறீர்கள்? படிக்கிறீர்களா, வேலை செய்கிறீர்களா?",
                    "தற்போதைய வேலை என்ன?"
                ).random()
                "interests" -> listOf(
                    "உங்களுக்கு எந்த மாதிரி வேலை பிடிக்கும்? கணினி, பால் பண்ணை, தையல், கட்டுமானம்?",
                    "எதில் ஆர்வம் அதிகம்?"
                ).random()
                "preference" -> "சொந்தமாக தொழில் தொடங்க ஆசையா, இல்லை நல்ல வேலைக்கு போக ஆசையா?"
                "mobility" -> "வேலைக்காக எவ்வளவு தூரம் போக முடியும்? உங்கள் ஊரிலேயா, மாவட்டத்திலா? உடல் நிலை தடையா ஏதும்?"
                "district" -> "நீங்கள் தமிழ்நாட்டில் எந்த மாவட்டம்? உங்கள் ஊரில் என்ன வேலை கிடைக்கும்?"
                else -> "உங்களைப் பற்றி இன்னும் சொல்லுங்கள்."
            }
            "hi" -> when (effectiveNext) {
                "education" -> "आपने कितनी पढ़ाई की है?"
                "familyOccupation" -> "आपके परिवार में पारंपरिक रूप से क्या काम होता है? सरकारी नौकरी, खेती, कुछ और?"
                "currentLivelihood" -> "अभी आप क्या करते हैं - पढ़ाई, नौकरी, या कुछ और?"
                "interests" -> "आपको किस तरह के काम में मज़ा आता है? कंप्यूटर, डेयरी, सिलाई, कुछ और?"
                "preference" -> "आप खुद का बिज़नेस शुरू करना चाहते हैं या किसी कंपनी में नौकरी करना चाहते हैं?"
                "mobility" -> "काम के लिए कितनी दूर जा सकते हैं? कोई शारीरिक परेशानी तो नहीं?"
                "district" -> "आप तमिलनाडु में किस जिले से हैं?"
                else -> "और बताइए अपने बारे में?"
            }
            else -> when (effectiveNext) {
                "education" -> listOf(
                    "How far did you study? School, college, ITI?",
                    "What's your education background?"
                ).random()
                "familyOccupation" -> {
                    // Contextual based on last answer
                    if (lastFragment?.currentLivelihood?.contains("student", ignoreCase = true) == true ||
                        lastTextLower.contains("student") || lastTextLower.contains("studying")) {
                        listOf(
                            "Got it, you're studying - that's awesome! What does your family traditionally do for work? Government job, farming, business?",
                            "Since you're a student, I'm curious - what work does your family do? Helps me understand your background.",
                            "Nice, student life! What about your family - what's their main occupation?"
                        ).random()
                    } else {
                        listOf(
                            "What does your family traditionally do? Farming, government job, tailoring, something else?",
                            "Tell me about your family's work - what do your parents do?",
                            "What's your family's traditional occupation? Helps me find something that fits your roots."
                        ).random()
                    }
                }
                "currentLivelihood" -> listOf(
                    "What do you do currently for a living? Student, farming, daily wage, or looking for work?",
                    "Are you currently studying, working, or searching for opportunities?"
                ).random()
                "interests" -> {
                    if (lastTextLower.contains("computer") || lastTextLower.contains("cyber") || lastTextLower.contains("software") || lastTextLower.contains("engineering")) {
                        listOf(
                            "Computer Science and Cyber Security - that's fantastic! Super relevant today. What kind of work excites you most? Building things, teaching, tech, dairy, tailoring?",
                            "Wow, Cyber Security specialization! What type of activities do you enjoy? Tech, hands-on work, creative things?",
                            "CSE with Cyber Security - impressive! Beyond studies, what work or activities do you find interesting?"
                        ).random()
                    } else if (lastTextLower.contains("government")) {
                        listOf(
                            "Government employee family - got it! What kind of work interests you personally? Tech, farming, business?",
                            "So your family is in government service. What about you - what work do you enjoy?"
                        ).random()
                    } else {
                        listOf(
                            "What kind of work or activities do you enjoy? Tech, dairy, goats, poultry, farming, food processing, machines, tailoring, construction?",
                            "What excites you? Working with computers, animals, machines, making things?",
                            "Tell me, what work feels interesting to you? No wrong answers."
                        ).random()
                    }
                }
                "preference" -> listOf(
                    "Would you prefer to start your own small business someday, or get a steady job with a good employer? Both are great paths.",
                    "Do you dream of having your own enterprise, or would you rather have a stable job? What feels right?",
                    "Self-employment or wage employment - which appeals more to you?"
                ).random()
                "mobility" -> listOf(
                    "How far can you travel for work? Just your village, within your district, or anywhere in Tamil Nadu? Any physical constraints I should know?",
                    "For work, are you looking to stay nearby or open to travel? Any health constraints?",
                    "Mobility-wise, can you travel far for training or work, or prefer staying local?"
                ).random()
                "district" -> listOf(
                    "Which district in Tamil Nadu are you from? And what's the job scene like in your area?",
                    "What district are you in? Helps me find nearby centres and local opportunities.",
                    "Where in Tamil Nadu are you located? Village/town name also helps."
                ).random()
                "closing" -> generateClosingMessage(lang, profile)
                else -> "Tell me a bit more about yourself."
            }
        }

        return if (acknowledgment.isNotBlank()) {
            "$acknowledgment $questionPart"
        } else {
            questionPart
        }
    }

    private fun generateAcknowledgment(lastFragment: ProfileFragment?, lastText: String, lang: String): String {
        if (lastFragment == null && lastText.isBlank()) return ""
        val lower = lastText.lowercase()

        return when (lang) {
            "en" -> {
                when {
                    lastFragment?.edu == "graduate" || lower.contains("graduate") || lower.contains("engineering") || lower.contains("computer science") -> {
                        listOf(
                            "Graduate in Computer Science - impressive!",
                            "Engineering background - great!",
                            "Computer Science - that's awesome!"
                        ).random()
                    }
                    lastFragment?.hasCurrentLivelihood() == true && lastFragment.currentLivelihood!!.contains("student", ignoreCase = true) -> {
                        listOf(
                            "Got it, you're a student.",
                            "Student - nice!",
                            "Studying currently - understood."
                        ).random()
                    }
                    lower.contains("cyber security") || lower.contains("cybersecurity") -> {
                        listOf(
                            "Cyber Security specialization - super relevant today!",
                            "Cyber Security - fantastic choice!",
                            "Cyber Security - that's the future!"
                        ).random()
                    }
                    lastFragment?.familyOccupation?.contains("government", ignoreCase = true) == true || lower.contains("government employee") -> {
                        listOf(
                            "Government employee family - understood.",
                            "Family in government service - got it.",
                            ""
                        ).random()
                    }
                    lower.contains("dairy") || lower.contains("milk") -> "Dairy - wonderful!"
                    lower.contains("farming") || lower.contains("agriculture") -> "Farming background - great!"
                    else -> {
                        // Don't always acknowledge, sometimes just ask next
                        if (Random.nextBoolean()) "" else listOf("Got it.", "Thanks for sharing.", "I see.").random()
                    }
                }
            }
            else -> {
                // For non-English, keep acknowledgments short
                when {
                    lower.contains("computer") || lower.contains("engineering") -> when (lang) {
                        "ta" -> "கணினி படிப்பு - அருமை!"
                        "hi" -> "कंप्यूटर साइंस - बहुत बढ़िया!"
                        else -> "Great!"
                    }
                    lower.contains("student") -> when (lang) {
                        "ta" -> "மாணவர் - புரிகிறது."
                        "hi" -> "छात्र - समझ गया।"
                        else -> "Student - got it."
                    }
                    else -> ""
                }
            }
        }
    }

    private fun generateClosingMessage(lang: String, profile: ProfileFragment? = null): String {
        val hasEdu = profile?.hasEdu() == true
        val hasFamily = profile?.hasFamilyOccupation() == true
        val hasInterest = profile?.hasInterests() == true

        return when (lang) {
            "ta" -> if (hasEdu && hasFamily) "மிக்க நன்றி! உங்கள் பின்னணியை புரிந்து கொண்டேன். உங்களுக்கான சிறந்த பயிற்சி வாய்ப்புகளை தேடுகிறேன்."
            else "மிக்க நன்றி! உங்களுக்கான சிறந்த பயிற்சி வாய்ப்புகளை தேடுகிறேன்."
            "hi" -> "बहुत धन्यवाद! आपकी बातें सुनकर अच्छा लगा। आपके लिए सबसे अच्छे विकल्प ढूंढता हूँ।"
            "te" -> "ధన్యవాదాలు! మీకు తగిన అవకాశాలు వెతుకుతున్నాను."
            "kn" -> "ಧನ್ಯವಾದಗಳು! ನಿಮಗಾಗಿ ಉತ್ತಮ ಅವಕಾಶಗಳನ್ನು ಹುಡುಕುತ್ತೇನೆ."
            "ml" -> "നന്ദി! നിങ്ങൾക്കான അവസരങ്ങൾ തിരയുന്നു."
            else -> if (hasEdu && hasFamily && hasInterest) {
                "Thank you so much for sharing! I now have a good picture - ${profile?.edu} background, family in ${profile?.familyOccupation}, interested in ${profile?.interests?.joinToString()}. Let me find the best training options that truly fit you."
            } else {
                "Thank you so much for sharing! I really appreciate you opening up. Let me find the best training and livelihood options that truly fit your aspirations."
            }
        }
    }
}
