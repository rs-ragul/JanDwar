package `in`.jandwar.app.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.jandwar.app.ai.ConversationController
import `in`.jandwar.app.ai.GroqExtractor
import `in`.jandwar.app.ai.ProfileFragment
import `in`.jandwar.app.data.model.ConversationMessage
import `in`.jandwar.app.data.model.MatchedRole
import `in`.jandwar.app.data.repository.AppRepository
import javax.inject.Inject

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val conversationController: ConversationController,
    private val groqExtractor: GroqExtractor,
    private val repository: AppRepository
) : ViewModel() {

    var currentQuestion by mutableStateOf("")
        private set
    var transcript by mutableStateOf("")
        private set
    var status by mutableStateOf("Listening")
        private set
    var isSpeaking by mutableStateOf(false)
        private set
    var isListening by mutableStateOf(false)
        private set
    var profile by mutableStateOf(ProfileFragment())
        private set
    var isDone by mutableStateOf(false)
        private set
    var finalProfile by mutableStateOf<ProfileFragment?>(null)
        private set
    var conversationHistory by mutableStateOf(listOf<ConversationMessage>())
        private set
    var resultExplanation by mutableStateOf("")
        private set
    var isExplainingResults by mutableStateOf(false)
        private set
    var matchedResults by mutableStateOf<List<MatchedRole>>(emptyList())
        private set

    private var currentLangCode: String = "en"
    private var onFieldExtractedCallback: ((ProfileFragment) -> Unit)? = null
    private var onDoneCallback: ((ProfileFragment) -> Unit)? = null

    fun start(lang: String, onFieldExtracted: (ProfileFragment) -> Unit, onDone: (ProfileFragment) -> Unit) {
        currentLangCode = lang
        onFieldExtractedCallback = onFieldExtracted
        onDoneCallback = onDone
        isDone = false
        transcript = ""
        status = when(lang) {
            "ta" -> "கேட்கிறேன்"
            "hi" -> "सुन रहा हूँ"
            "te" -> "వింటున్నాను"
            "kn" -> "ಆಲಿಸುತ್ತಿದ್ದೇನೆ"
            "ml" -> "കേൾക്കുന്നു"
            else -> "Listening"
        }
        currentQuestion = "..."
        conversationHistory = emptyList()
        resultExplanation = ""
        isExplainingResults = false
        matchedResults = emptyList()
        isListening = false
        isSpeaking = false

        conversationController.init(lang, object : ConversationController.Listener {
            override fun onQuestion(questionText: String) {
                currentQuestion = questionText
                transcript = ""
                if (!isExplainingResults) {
                    conversationHistory = conversationHistory + ConversationMessage(role = "assistant", text = questionText)
                }
            }
            override fun onPartialTranscript(partial: String) { transcript = partial }
            override fun onTranscriptResult(full: String) {
                transcript = full
                if (full.isNotBlank()) {
                    conversationHistory = conversationHistory + ConversationMessage(role = "user", text = full)
                }
            }
            override fun onFieldExtracted(updated: ProfileFragment) {
                profile = updated
                onFieldExtracted(updated)
            }
            override fun onDone(finalProfile: ProfileFragment) {
                this@VoiceViewModel.finalProfile = finalProfile
                explainResultsThenDone(finalProfile)
            }
            override fun onStatus(statusText: String) { status = statusText }
            override fun onSpeaking(isSpeaking: Boolean) {
                this@VoiceViewModel.isSpeaking = isSpeaking
                if (isSpeaking) isListening = false
            }
            override fun onResultExplanation(text: String) {
                resultExplanation = text
                isExplainingResults = true
                conversationHistory = conversationHistory + ConversationMessage(role = "assistant", text = text)
            }
            override fun onListening(isListening: Boolean) {
                this@VoiceViewModel.isListening = isListening
            }
        })
        conversationController.start()
    }

    // STEP 3 from user spec: ordinary programming filtering (Excel-like, no AI) for top 3
    // STEP 4: Then ask Groq to explain results warmly in user's language, TTS speaks it
    private fun explainResultsThenDone(finalProfile: ProfileFragment) {
        try {
            val education = finalProfile.edu?.let { eduStr -> try { `in`.jandwar.app.data.model.EducationLevel.fromAiString(eduStr) } catch (_: Exception) { null } }
            val preference = finalProfile.preference?.let { prefStr -> try { `in`.jandwar.app.data.model.Preference.fromAiString(prefStr) } catch (_: Exception) { null } }
            val mobility = finalProfile.mobility?.let { mobStr -> try { `in`.jandwar.app.data.model.Mobility.fromAiString(mobStr) } catch (_: Exception) { null } }
            val profileForMatching = `in`.jandwar.app.data.model.UserProfile(
                education = education,
                preference = preference,
                mobility = mobility,
                district = finalProfile.district ?: "",
                interests = finalProfile.interests.toSet(),
                familyOccupation = finalProfile.familyOccupation ?: "",
                currentLivelihood = finalProfile.currentLivelihood ?: "",
                physicalConstraints = finalProfile.physicalConstraints ?: "",
                localOpportunity = finalProfile.localOpportunity ?: "",
                skills = finalProfile.skills.toSet()
            )
            // Ordinary code filtering - no AI, Excel-like
            val results = repository.matchRoles(profileForMatching)
            matchedResults = results
            onFieldExtractedCallback?.invoke(finalProfile)

            val top = results.take(3)
            if (top.isEmpty()) {
                val fallback = buildNaturalResultsSummary(results, profileForMatching, finalProfile, currentLangCode)
                conversationController.speakResultsSummary(fallback) {
                    isDone = true
                    onDoneCallback?.invoke(finalProfile)
                }
                return
            }

            // Prepare top 3 as strings for Groq (Step 3 -> Step 4)
            val topResultsForGroq = top.mapIndexed { idx, matched ->
                "${idx+1}. ${matched.role.job_role} (${matched.role.qp_code}) - SSC: ${matched.role.ssc}, Sector: ${matched.role.sector}, NSQF: ${matched.role.nsqf_level}, Duration: ${matched.role.duration_hours}h, " +
                "Centre: ${matched.centre?.name ?: "N/A"} in ${matched.centre?.district ?: finalProfile.district ?: "N/A"}, " +
                "Why fits: ${matched.familyFitNote.ifBlank { "interest in ${finalProfile.interests.joinToString()}" }}, Skill gap: ${matched.skillGapNote}"
            }

            // PURE GROQ explains results - real AI conversation, not deterministic
            if (groqExtractor.isConfigured()) {
                isExplainingResults = true
                status = when(currentLangCode) {
                    "ta" -> "Groq AI உங்கள் வாய்ப்புகளை விளக்குகிறது..."
                    "hi" -> "Groq AI आपके विकल्प समझा रहा है..."
                    else -> "Groq AI explaining your best matches..."
                }
                groqExtractor.explainResultsWithGroq(finalProfile, topResultsForGroq, currentLangCode) { explanation ->
                    // This callback is on main thread, spoken by TTS
                    resultExplanation = explanation
                    conversationHistory = conversationHistory + ConversationMessage(role = "assistant", text = explanation)
                    // Speak with TTS (natural native voice)
                    conversationController.speakResultsSummary(explanation) {
                        isDone = true
                        onDoneCallback?.invoke(finalProfile)
                    }
                }
            } else {
                // Fallback if no Groq key (should not happen in production, but keep for offline)
                val fallback = buildNaturalResultsSummary(results, profileForMatching, finalProfile, currentLangCode)
                conversationController.speakResultsSummary(fallback) {
                    isDone = true
                    onDoneCallback?.invoke(finalProfile)
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceViewModel", "Error explaining results: ${e.message}", e)
            isDone = true
            onDoneCallback?.invoke(finalProfile)
        }
    }

    private fun buildNaturalResultsSummary(results: List<MatchedRole>, profile: `in`.jandwar.app.data.model.UserProfile, frag: ProfileFragment, lang: String): String {
        if (results.isEmpty()) {
            return when (lang) {
                "ta" -> "மன்னிக்கவும், உங்கள் விவரங்களுக்கு ஏற்ற பயிற்சி இப்போது கிடைக்கவில்லை. உங்கள் கல்வி ${frag.edu ?: ""}, ஆர்வம் ${frag.interests.joinToString()} ஆகியவற்றை வைத்து தேடினேன். வேறு மாவட்டம் அல்லது ஆர்வத்தை முயற்சிக்கலாம். உங்கள் விவரம் சேமிக்கப்பட்டது, 516 பயிற்சிகளையும் பார்க்கலாம்."
                "hi" -> "क्षमा करें, आपके विवरण के लिए अभी कोई उपयुक्त प्रशिक्षण नहीं मिला। आपकी शिक्षा ${frag.edu ?: ""} और रुचि ${frag.interests.joinToString()} के आधार पर खोजा। दूसरा जिला या रुचि देखें। आपका प्रोफाइल सेव है।"
                "te" -> "క్షమించండి, మీ వివరాలకు తగిన శిక్షణ ఇప్పుడు లేదు. వేరే జిల్లా లేదా ఆసక్తిని ప్రయత్నించండి."
                "kn" -> "ಕ್ಷಮಿಸಿ, ನಿಮ್ಮ ವಿವರಗಳಿಗೆ ಸೂಕ್ತ ತರಬೇತಿ ಈಗ ಸಿಗುತ್ತಿಲ್ಲ. ಬೇರೆ ಜಿಲ್ಲೆ ಅಥವಾ ಆಸಕ್ತಿ ಪ್ರಯತ್ನಿಸಿ."
                "ml" -> "ക്ഷമിക്കണം, നിങ്ങളുടെ വിവരങ്ങൾക്ക് അനുയോജ്യമായ പരിശീലനം ഇപ്പോൾ ലഭ്യമല്ല."
                else -> "I searched based on your education ${frag.edu ?: ""} and interests ${frag.interests.joinToString()}, but couldn't find a perfect match right now. Your profile is saved, you can browse all 516 courses or try another district or interest. Sometimes local centres for ${frag.district ?: "your district"} are still being verified - check with TAHDCO."
            }
        }
        val top = results.take(3)
        val family = frag.familyOccupation?.ifBlank { null } ?: "your family"
        val current = frag.currentLivelihood?.ifBlank { null } ?: "your current work"
        val interestsStr = frag.interests.joinToString(", ").ifBlank { "your interests" }
        val district = frag.district?.ifBlank { "your area" } ?: "your area"
        
        return when (lang) {
            "ta" -> buildString {
                append("அருமை. உங்களைப் பற்றி நன்றாக புரிந்து கொண்டேன். ")
                append("நீங்கள் ${frag.edu ?: ""} படித்திருக்கிறீர்கள், குடும்பம் $family, தற்போது $current, ஆர்வம் $interestsStr. ")
                append("இதை வைத்து உங்களுக்கு ஏற்ற ${top.size} சிறந்த வாய்ப்புகளை கண்டறிந்துள்ளேன். ")
                top.forEachIndexed { idx, matched ->
                    append("${idx+1}. ${repository.interestLabel("ta", matched.role.sector)} துறையில் ${matched.role.job_role}. ")
                    if (matched.familyFitNote.isNotBlank()) append("இது உங்கள் குடும்ப அனுபவத்தை அடிப்படையாகக் கொண்டது. ")
                    append("${matched.skillGapNote}. ")
                    matched.centre?.let { append("${it.district} மாவட்டத்தில் ${it.name} மையத்தில் கிடைக்கிறது. ") }
                }
                append("இந்த விவரங்கள் அனைத்தும் அடுத்த பக்கத்தில் உள்ளன. எந்த பயிற்சி பற்றி மேலும் தெரிந்து கொள்ள விரும்புகிறீர்கள்.")
            }
            "hi" -> buildString {
                append("बहुत बढ़िया. मैंने आपको अच्छे से समझ लिया है. ")
                append("आप ${frag.edu ?: ""} हैं, परिवार $family में है, अभी $current, रुचि $interestsStr, जिला $district. ")
                append("इसी आधार पर आपके लिए ${top.size} सबसे अच्छे विकल्प मिले हैं. ")
                top.forEachIndexed { idx, matched ->
                    append("${idx+1}. ${matched.role.job_role}, जो ${matched.role.qp_code} है. ")
                    if (matched.familyFitNote.isNotBlank()) append("यह आपके पारिवारिक अनुभव पर आधारित है. ")
                    append("${matched.skillGapNote}. ")
                    matched.centre?.let { append("${it.district} में ${it.name} केंद्र पर उपलब्ध है. ") }
                }
                append("सभी विवरण अगले पेज पर हैं। किस प्रशिक्षण के बारे में और जानना चाहते हैं.")
            }
            else -> buildString {
                append("Wonderful, I've really understood you now. ")
                append("You're ${frag.edu ?: ""}, family background is $family, currently $current, interested in $interestsStr, from $district. ")
                append("Based on all this, I found ${top.size} excellent options that truly fit your journey. ")
                top.forEachIndexed { idx, matched ->
                    append("${idx+1}. ${matched.role.job_role}, that's ${matched.role.qp_code} from ${matched.role.ssc}. ")
                    if (matched.familyFitNote.isNotBlank()) append("${matched.familyFitNote}. ")
                    else append("This builds on your interest in ${interestsStr}. ")
                    append("${matched.skillGapNote}. ")
                    matched.centre?.let { append("It's available at ${it.name} in ${it.district}, which is perfect for your mobility in $district. ") }
                    if (matched.regionOpportunity.isNotBlank()) append("${matched.regionOpportunity} ")
                }
                append("I've put all the details - fees, duration, centre contact - on the next page. You can tap any course to see full details, or ask me more. Which one feels most exciting to you.")
            }
        }
    }

    fun stop() { conversationController.stop() }
    fun resumeListening() { conversationController.resumeListening() }
    fun repeatQuestion() { conversationController.repeatCurrentQuestion() }
    fun processTypedAnswer(text: String) {
        if (text.isBlank()) return
        transcript = text
        conversationHistory = conversationHistory + ConversationMessage(role = "user", text = text)
        if (isExplainingResults) {
            // After Groq explained top 3, user can ask questions - allow follow-up via Groq explanation or navigate
            val followUp = when (currentLangCode) {
                "ta" -> "நல்ல கேள்வி. விவரங்கள் அடுத்த பக்கத்தில் உள்ளன. எந்த பயிற்சி பிடித்திருக்கிறது."
                "hi" -> "अच्छा सवाल. विवरण अगले पेज पर है। कौन सा प्रशिक्षण अच्छा लगा."
                else -> "Great question. Details are on next page. Tap any course for centre, fees, contact. Which one interests you most."
            }
            conversationController.speakOnly(followUp) {
                isDone = true
                finalProfile?.let { onDoneCallback?.invoke(it) }
            }
            return
        }
        conversationController.processAnswer(text)
    }

    override fun onCleared() { super.onCleared(); conversationController.stop() }
}
