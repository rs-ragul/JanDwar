package `in`.jandwar.app.data.repository

import `in`.jandwar.app.data.local.AssetDataSource
import `in`.jandwar.app.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val assetDataSource: AssetDataSource
) {
    private var cachedRoles: List<JobRole>? = null
    private var cachedCentres: List<Centre>? = null
    private var cachedDistricts: DistrictsData? = null
    private var cachedI18n: I18nData? = null

    fun getJobRoles(): List<JobRole> {
        if (cachedRoles == null) cachedRoles = assetDataSource.loadJobRoles()
        return cachedRoles!!
    }

    fun getCentres(): List<Centre> {
        if (cachedCentres == null) cachedCentres = assetDataSource.loadCentres()
        return cachedCentres!!
    }

    fun getDistricts(): DistrictsData {
        if (cachedDistricts == null) cachedDistricts = assetDataSource.loadDistricts()
        return cachedDistricts!!
    }

    fun getI18n(): I18nData {
        if (cachedI18n == null) cachedI18n = assetDataSource.loadI18n()
        return cachedI18n!!
    }

    fun getCentreForDistrict(district: String): Centre? {
        if (district.isBlank()) return null
        return getCentres().find { it.district.equals(district, ignoreCase = true) }
    }

    fun getInterestChips(lang: String): List<InterestChip> {
        val keys = listOf("dairy", "cattle", "goat", "poultry", "farming", "food", "machine", "textile", "construction", "tailor")
        val i18n = getI18n()
        return keys.map { key ->
            val label = i18n.interests[lang]?.get(key)
                ?: extraInterestTranslations(lang, key)
                ?: i18n.interests["en"]?.get(key)
                ?: key
            InterestChip(key, label)
        }
    }

    fun tr(lang: String, key: String): String {
        val i18n = getI18n()
        extraTranslations(lang, key)?.let { if (it.isNotBlank()) return it }
        val localized = i18n.strings[lang]?.get(key)
        if (!localized.isNullOrBlank()) return localized
        val en = i18n.strings["en"]?.get(key)
        if (!en.isNullOrBlank()) return en
        enMap[key]?.let { return it }
        return key
    }

    fun interestLabel(lang: String, key: String): String {
        val i18n = getI18n()
        return extraInterestTranslations(lang, key)
            ?: i18n.interests[lang]?.get(key)
            ?: i18n.interests["en"]?.get(key)
            ?: key
    }

    private val enMap = mapOf(
        "name" to "JanDwar",
        "tagline" to "Gateway for Citizens",
        "continue" to "Continue",
        "start" to "Start",
        "back" to "Back",
        "home_title" to "Find the right livelihood path",
        "home_sub" to "Answer a few simple questions. JanDwar works offline and shows only verified facts.",
        "stat_roles" to "NSQF roles",
        "stat_fundable" to "fundable domains",
        "stat_districts" to "TN districts",
        "stat_centres" to "verified centres",
        "start_intake" to "Find my options",
        "search" to "Search",
        "settings" to "Settings",
        "settings_sub" to "Keep the app simple, private and ready for village use.",
        "select_district" to "Select District",
        "offline_data" to "Offline data",
        "offline_data_text" to "Job roles, districts and centre details are stored inside the app.",
        "offline_ai" to "Download Offline AI (45MB)",
        "offline_ai_installed" to "Offline AI Installed",
        "offline_ai_text" to "Get the small local AI model to use voice without internet.",
        "privacy" to "Privacy",
        "privacy_text" to "No personal details are saved or uploaded in this prototype.",
        "app_name_label" to "App",
        "honesty_note" to "JanDwar never invents a course, centre, fee or subsidy. If local centre data is missing, it tells you clearly.",
        "intake" to "Your details",
        "mic_label" to "Mic",
        "speak_button" to "Speak now",
        "details" to "View details",
        "level" to "Level",
        "short_term" to "Short-term",
        "long_term" to "Long-term",
        "asset_rule" to "Up to Rs.50,000 or 50% of asset cost with loan, whichever is lower.",
        "not_claim_text" to "Confirm final eligibility, fee, batch date and empanelment with TAHDCO or the training centre before enrolment.",
        "call" to "Call centre",
        "no_results" to "No safe match found. Try Class 10 or another interest.",
        "reason_base" to "Matched using education, interest, scheme rules and centre availability.",
        "reason_interest" to "Matches your interest in",
        "on_0" to "Use big taps or one spoken sentence. No form stress.",
        "on_1" to "Recommendations follow PM-AJAY rules and education gates.",
        "on_2" to "Centres are shown only when verified for your district.",
        "voice_intro" to "Meet your voice assistant",
        "voice_title" to "Tell JanDwar what you need",
        "voice_sub" to "Speak naturally. We will find a useful starting point.",
        "choose_path" to "Choose your path",
        "personalized_title" to "Personalized course finder",
        "personalized_sub" to "Answer three simple questions and get matched options.",
        "browse_title" to "Browse all courses",
        "browse_sub" to "Explore every course in the offline catalogue.",
        "browse_action" to "See all courses",
        "browse_note" to "516 qualification packs are bundled in this app. Tap a course for its details.",
        "offline_search" to "Offline search",
        "offline_search_sub" to "Tap the choices below. No internet or account is needed.",
        "close" to "Close",
        "voice_listening" to "Listening",
        "voice_hearing" to "I am listening to you",
        "voice_prompt" to "Speak naturally. Tap stop when you are finished.",
        "stop_listening" to "Stop listening",
        "voice_unavailable" to "Voice input is not available on this phone.",
        "found" to "found from real qualification packs and verified centres",
        "options" to "Your options",
        "course_type" to "Course type",
        "outcome" to "Target outcome",
        "fundable" to "Fundable under",
        "asset" to "Asset support",
        "why" to "Why this was suggested",
        "centre" to "Verified centre course",
        "no_centre" to "No verified centre data - confirm with TAHDCO before promising a place.",
        "not_fundable" to "Not a fundable domain",
        "fundable_badge" to "PM-AJAY fundable domain",
        "change_lang" to "Change language",
        "submit" to "Show my options",
        "skill_gap" to "Skill gap",
        "eligible" to "You are eligible",
        "needs_edu" to "Needs higher education",
        "disclaimer" to "Disclaimer",
        "about" to "About",
        "version" to "Version",
        "dev_settings" to "Developer Settings",
        "tts_engine" to "TTS Engine",
        "current_engine" to "Current Engine",
        "q_edu" to "How far did you study?",
        "q_pref" to "What do you want?",
        "q_travel" to "How far can you travel?",
        "q_dist" to "Which district?",
        "q_int" to "What interests you?",
        "edu_below8" to "Below 8th standard",
        "edu_8" to "Up to 8th standard",
        "edu_10" to "Up to 10th standard",
        "edu_12" to "Up to 12th standard",
        "edu_grad" to "Graduate or above",
        "pref_self" to "My own business",
        "pref_wage" to "A job with an employer",
        "travel_local" to "Only my village / nearby",
        "travel_district" to "Anywhere in my district",
        "travel_any" to "Anywhere in Tamil Nadu",
        "name" to "JanDwar",
        "you" to "You",
        "send" to "Send"
    )

    private fun extraTranslations(lang: String, key: String): String? {
        if (lang == "en") return enMap[key]
        val maps = mapOf(
            "ta" to mapOf(
                "name" to "JanDwar",
                "tagline" to "மக்களுக்கான நுழைவாயில்",
                "continue" to "தொடரவும்",
                "start" to "தொடங்கு",
                "back" to "பின்",
                "home_title" to "சரியான வாழ்வாதார பாதையை கண்டறியுங்கள்",
                "home_sub" to "சில எளிய கேள்விகளுக்கு பதில் அளிக்கவும்.",
                "stat_roles" to "NSQF பணிகள்",
                "stat_fundable" to "நிதியுதவி துறைகள்",
                "stat_districts" to "மாவட்டங்கள்",
                "stat_centres" to "சரிபார்க்கப்பட்ட மையங்கள்",
                "start_intake" to "என் வாய்ப்புகளை கண்டறி",
                "search" to "தேடு",
                "settings" to "அமைப்புகள்",
                "settings_sub" to "செயலியை எளிமையாக வைத்திருங்கள்.",
                "select_district" to "மாவட்டத்தை தேர்ந்தெடுக்கவும்",
                "offline_data" to "ஆஃப்லைன் தரவு",
                "offline_data_text" to "வேலை பணிகள், மாவட்டங்கள், மைய விவரங்கள் செயலியில் உள்ளன.",
                "privacy" to "தனியுரிமை",
                "privacy_text" to "தனிப்பட்ட விவரங்கள் சேமிக்கப்படவில்லை.",
                "honesty_note" to "JanDwar பாடம், மையம், கட்டணத்தை கற்பனை செய்யாது.",
                "intake" to "உங்கள் விவரங்கள்",
                "speak_button" to "இப்போது பேசுங்கள்",
                "details" to "விவரம் பார்க்க",
                "level" to "நிலை",
                "short_term" to "குறுகிய காலம்",
                "long_term" to "நீண்ட காலம்",
                "asset_rule" to "ரூ.50,000 வரை உதவி.",
                "call" to "மையத்தை அழை",
                "no_results" to "பொருத்தமான பயிற்சி இல்லை. வேறு ஆர்வத்தை முயற்சிக்கவும்.",
                "reason_base" to "கல்வி, ஆர்வம் அடிப்படையில் பொருந்தியது.",
                "reason_interest" to "உங்கள் ஆர்வத்துடன் பொருந்துகிறது",
                "voice_intro" to "உங்கள் குரல் உதவியாளர்",
                "voice_title" to "JanDwar-ிடம் சொல்லுங்கள்",
                "voice_sub" to "இயல்பாக பேசுங்கள்.",
                "choose_path" to "உங்கள் வழியை தேர்ந்தெடுக்கவும்",
                "personalized_title" to "உங்களுக்கான பயிற்சி தேடல்",
                "browse_title" to "அனைத்து பயிற்சிகளும்",
                "close" to "மூடு",
                "voice_listening" to "கேட்கிறோம்",
                "voice_hearing" to "உங்களை கேட்கிறேன்",
                "stop_listening" to "நிறுத்து",
                "skill_gap" to "திறன் இடைவெளி",
                "you" to "நீங்கள்",
                "send" to "அனுப்பு",
                "found" to "உண்மையான பாடப்பிரிவுகளிலிருந்து",
                "options" to "உங்கள் விருப்பங்கள்",
                "change_lang" to "மொழியை மாற்று",
                "submit" to "என் விருப்பங்களை காட்டு",
                "q_edu" to "எவ்வளவு படித்திருக்கிறீர்கள்?",
                "q_pref" to "உங்களுக்கு என்ன வேண்டும்?",
                "q_travel" to "எவ்வளவு தூரம் செல்ல முடியும்?",
                "q_dist" to "எந்த மாவட்டம்?",
                "q_int" to "எதில் ஆர்வம்?",
                "edu_below8" to "8-க்கு கீழ்",
                "edu_8" to "8-ம் வகுப்பு வரை",
                "edu_10" to "10-ம் வகுப்பு வரை",
                "edu_12" to "12-ம் வகுப்பு வரை",
                "edu_grad" to "பட்டப்படிப்பு அல்லது மேல்",
                "pref_self" to "சொந்த தொழில்",
                "pref_wage" to "வேலை",
                "travel_local" to "அருகில் மட்டும்",
                "travel_district" to "மாவட்டம் முழுவதும்",
                "travel_any" to "எங்கும்"
            ),
            "hi" to mapOf(
                "name" to "JanDwar",
                "tagline" to "नागरिकों का प्रवेश द्वार",
                "continue" to "जारी रखें",
                "start" to "शुरू करें",
                "back" to "वापस",
                "home_title" to "सही आजीविका रास्ता खोजें",
                "home_sub" to "कुछ सरल सवालों के जवाब दें।",
                "stat_roles" to "NSQF भूमिकाएं",
                "start_intake" to "मेरे विकल्प खोजें",
                "search" to "खोजें",
                "settings" to "सेटिंग्स",
                "select_district" to "जिला चुनें",
                "offline_data" to "ऑफ़लाइन डेटा",
                "privacy" to "गोपनीयता",
                "honesty_note" to "JanDwar कोई कोर्स, केंद्र, फीस नहीं गढ़ता।",
                "intake" to "आपका विवरण",
                "speak_button" to "अब बोलिए",
                "details" to "विवरण देखें",
                "level" to "स्तर",
                "short_term" to "अल्पकालिक",
                "long_term" to "दीर्घकालिक",
                "call" to "केंद्र को कॉल करें",
                "no_results" to "कोई उपयुक्त प्रशिक्षण नहीं मिला।",
                "reason_base" to "शिक्षा, रुचि, योजना नियमों से मिलान।",
                "voice_intro" to "अपने वॉइस सहायक से मिलें",
                "voice_title" to "JanDwar को बताएं आपको क्या चाहिए",
                "choose_path" to "अपना रास्ता चुनें",
                "personalized_title" to "व्यक्तिगत कोर्स खोज",
                "browse_title" to "सभी कोर्स देखें",
                "close" to "बंद करें",
                "voice_listening" to "सुन रहे हैं",
                "stop_listening" to "सुनना रोकें",
                "found" to "वास्तविक योग्यता पैक से",
                "options" to "आपके विकल्प",
                "change_lang" to "भाषा बदलें",
                "submit" to "मेरे विकल्प दिखाओ",
                "skill_gap" to "कौशल अंतर",
                "you" to "आप",
                "send" to "भेजें",
                "q_edu" to "आपने कितनी पढ़ाई की है?",
                "q_pref" to "आपको क्या चाहिए?",
                "q_travel" to "आप कितनी दूर जा सकते हैं?",
                "q_dist" to "कौन सा ज़िला?",
                "q_int" to "किसमें रुचि है?",
                "edu_below8" to "8वीं से कम",
                "edu_8" to "8वीं तक",
                "edu_10" to "10वीं तक",
                "edu_12" to "12वीं तक",
                "edu_grad" to "स्नातक या ऊपर",
                "pref_self" to "अपना व्यवसाय",
                "pref_wage" to "नौकरी",
                "travel_local" to "सिर्फ़ मेरा गाँव / पास",
                "travel_district" to "मेरे ज़िले में कहीं भी",
                "travel_any" to "कहीं भी"
            ),
            "te" to mapOf(
                "name" to "JanDwar",
                "tagline" to "పౌరుల కోసం ద్వారం",
                "continue" to "కొనసాగించు",
                "start" to "ప్రారంభించు",
                "back" to "వెనుకకు",
                "home_title" to "సరైన జీవనోపాధి మార్గాన్ని కనుగొనండి",
                "search" to "వెతకండి",
                "settings" to "సెట్టింగ్‌లు",
                "select_district" to "జిల్లాను ఎంచుకోండి",
                "offline_data" to "ఆఫ్‌లైన్ డేటా",
                "privacy" to "గోప్యత",
                "honesty_note" to "JanDwar కోర్సు, కేంద్రం కల్పించదు।",
                "intake" to "మీ వివరాలు",
                "speak_button" to "ఇప్పుడు మాట్లాడండి",
                "details" to "వివరాలు చూడండి",
                "level" to "స్థాయి",
                "short_term" to "స్వల్పకాలిక",
                "long_term" to "దీర్ఘకాలిక",
                "voice_intro" to "మీ వాయిస్ అసిస్టెంట్",
                "voice_title" to "JanDwar కి చెప్పండి",
                "choose_path" to "మీ మార్గాన్ని ఎంచుకోండి",
                "personalized_title" to "వ్యక్తిగత కోర్సు శోధన",
                "browse_title" to "అన్ని కోర్సులు",
                "close" to "మూసివేయండి",
                "voice_listening" to "వింటున్నాము",
                "stop_listening" to "ఆపండి",
                "found" to "వాస్తవ కోర్సుల నుండి",
                "options" to "మీ ఎంపికలు",
                "change_lang" to "భాష మార్చండి",
                "submit" to "నా ఎంపికలు చూపించు",
                "skill_gap" to "నైపుణ్య అంతరం",
                "you" to "మీరు",
                "send" to "పంపండి",
                "q_edu" to "ఎంత చదివారు?",
                "q_pref" to "మీకు ఏం కావాలి?",
                "q_travel" to "ఎంత దూరం వెళ్లగలరు?",
                "q_dist" to "ఏ జిల్లా?",
                "q_int" to "దేనిపై ఆసక్తి?",
                "edu_below8" to "8వ తరగతి కంటే తక్కువ",
                "edu_8" to "8వ తరగతి వరకు",
                "edu_10" to "10వ తరగతి వరకు",
                "edu_12" to "12వ తరగతి వరకు",
                "edu_grad" to "డిగ్రీ లేదా పైన",
                "pref_self" to "సొంత వ్యాపారం",
                "pref_wage" to "ఉద్యోగం",
                "travel_local" to "నా గ్రామం / దగ్గర",
                "travel_district" to "నా జిల్లాలో ఎక్కడైనా",
                "travel_any" to "ఎక్కడైనా"
            ),
            "kn" to mapOf(
                "name" to "JanDwar",
                "tagline" to "ನಾಗರಿಕರ ದ್ವಾರ",
                "continue" to "ಮುಂದುವರಿಸಿ",
                "start" to "ಪ್ರಾರಂಭಿಸಿ",
                "back" to "ಹಿಂದೆ",
                "home_title" to "ಸರಿಯಾದ ಜೀವನೋಪಾಯ ಮಾರ್ಗವನ್ನು ಕಂಡುಕೊಳ್ಳಿ",
                "search" to "ಹುಡುಕಿ",
                "settings" to "ಸೆಟ್ಟಿಂಗ್‌ಗಳು",
                "select_district" to "ಜಿಲ್ಲೆಯನ್ನು ಆಯ್ಕೆಮಾಡಿ",
                "offline_data" to "ಆಫ್‌ಲೈನ್ ಡೇಟಾ",
                "privacy" to "ಗೌಪ್ಯತೆ",
                "honesty_note" to "JanDwar ಕೋರ್ಸ್, ಕೇಂದ್ರ ಕಲ್ಪಿಸುವುದಿಲ್ಲ.",
                "intake" to "ನಿಮ್ಮ ವಿವರಗಳು",
                "speak_button" to "ಈಗ ಮಾತನಾಡಿ",
                "details" to "ವಿವರಗಳನ್ನು ನೋಡಿ",
                "level" to "ಮಟ್ಟ",
                "short_term" to "ಅಲ್ಪಾವಧಿ",
                "long_term" to "ದೀರ್ಘಾವಧಿ",
                "voice_intro" to "ನಿಮ್ಮ ಧ್ವನಿ ಸಹಾಯಕ",
                "voice_title" to "JanDwar ಗೆ ಹೇಳಿ",
                "choose_path" to "ನಿಮ್ಮ ಮಾರ್ಗವನ್ನು ಆಯ್ಕೆಮಾಡಿ",
                "personalized_title" to "ವೈಯಕ್ತಿಕ ಕೋರ್ಸ್ ಹುಡುಕಾಟ",
                "browse_title" to "ಎಲ್ಲಾ ಕೋರ್ಸ್‌ಗಳು",
                "close" to "ಮುಚ್ಚಿ",
                "voice_listening" to "ಆಲಿಸುತ್ತಿದ್ದೇವೆ",
                "stop_listening" to "ನಿಲ್ಲಿಸಿ",
                "found" to "ನೈಜ ಕೋರ್ಸ್‌ಗಳಿಂದ",
                "options" to "ನಿಮ್ಮ ಆಯ್ಕೆಗಳು",
                "change_lang" to "ಭಾಷೆ ಬದಲಿಸಿ",
                "submit" to "ನನ್ನ ಆಯ್ಕೆಗಳನ್ನು ತೋರಿಸಿ",
                "skill_gap" to "ಕೌಶಲ್ಯ ಅಂತರ",
                "you" to "ನೀವು",
                "send" to "ಕಳುಹಿಸಿ",
                "q_edu" to "ಎಷ್ಟು ಓದಿದ್ದೀರಿ?",
                "q_pref" to "ನಿಮಗೆ ಏನು ಬೇಕು?",
                "q_travel" to "ಎಷ್ಟು ದೂರ ಹೋಗಬಹುದು?",
                "q_dist" to "ಯಾವ ಜಿಲ್ಲೆ?",
                "q_int" to "ಏನರಲ್ಲಿ ಆಸಕ್ತಿ?",
                "edu_below8" to "8ನೇ ತರಗತಿಗಿಂತ ಕೆಳಗೆ",
                "edu_8" to "8ನೇ ತರಗತಿವರೆಗೆ",
                "edu_10" to "10ನೇ ತರಗತಿವರೆಗೆ",
                "edu_12" to "12ನೇ ತರಗತಿವರೆಗೆ",
                "edu_grad" to "ಪದವಿ ಅಥವಾ ಮೇಲೆ",
                "pref_self" to "ಸ್ವಂತ ವ್ಯವಹಾರ",
                "pref_wage" to "ಕೆಲಸ",
                "travel_local" to "ನನ್ನ ಊರು / ಹತ್ತಿರ",
                "travel_district" to "ನನ್ನ ಜಿಲ್ಲೆಯಲ್ಲಿ ಎಲ್ಲಿಯಾದರೂ",
                "travel_any" to "ಎಲ್ಲಿಯಾದರೂ"
            ),
            "ml" to mapOf(
                "name" to "JanDwar",
                "tagline" to "പൗരന്മാർക്കുള്ള കവാടം",
                "continue" to "തുടരുക",
                "start" to "ആരംഭിക്കുക",
                "back" to "പിന്നോട്ട്",
                "home_title" to "ശരിയായ ഉപജീവന പാത കണ്ടെത്തുക",
                "search" to "തിരയുക",
                "settings" to "ക്രമീകരണങ്ങൾ",
                "select_district" to "ജില്ല തിരഞ്ഞെടുക്കുക",
                "offline_data" to "ഓഫ്‌ലൈൻ ഡാറ്റ",
                "privacy" to "സ്വകാര്യത",
                "honesty_note" to "JanDwar കോഴ്സ്, കേന്ദ്രം സങ്കൽപ്പിക്കുന്നില്ല.",
                "intake" to "നിങ്ങളുടെ വിവരങ്ങൾ",
                "speak_button" to "ഇപ്പോൾ സംസാരിക്കൂ",
                "details" to "വിശദാംശങ്ങൾ കാണുക",
                "level" to "ലെവൽ",
                "short_term" to "ഹ്രസ്വകാല",
                "long_term" to "ദീർഘകാല",
                "voice_intro" to "നിങ്ങളുടെ വോയ്‌സ് അസിസ്റ്റന്റ്",
                "voice_title" to "JanDwar നോട് പറയൂ",
                "choose_path" to "നിങ്ങളുടെ പാത തിരഞ്ഞെടുക്കുക",
                "personalized_title" to "വ്യക്തിഗത കോഴ്‌സ് തിരയൽ",
                "browse_title" to "എല്ലാ കോഴ്‌സുകളും",
                "close" to "അടയ്ക്കുക",
                "voice_listening" to "കേൾക്കുന്നു",
                "stop_listening" to "നിർത്തുക",
                "found" to "യഥാർത്ഥ കോഴ്സുകളിൽ നിന്ന്",
                "options" to "നിങ്ങളുടെ ഓപ്ഷനുകൾ",
                "change_lang" to "ഭാഷ മാറ്റുക",
                "submit" to "എന്റെ ഓപ്ഷനുകൾ കാണിക്കൂ",
                "skill_gap" to "കഴിവ് വിടവ്",
                "you" to "നിങ്ങൾ",
                "send" to "അയയ്ക്കുക",
                "q_edu" to "എത്ര പഠിച്ചു?",
                "q_pref" to "നിങ്ങൾക്ക് എന്ത് വേണം?",
                "q_travel" to "എത്ര ദൂരം പോകാം?",
                "q_dist" to "ഏത് ജില്ല?",
                "q_int" to "എന്തിൽ താൽപ്പര്യം?",
                "edu_below8" to "എട്ടാം ക്ലാസിന് താഴെ",
                "edu_8" to "എട്ടാം ക്ലാസ് വരെ",
                "edu_10" to "പത്താം ക്ലാസ് വരെ",
                "edu_12" to "പ്ലസ്ടു വരെ",
                "edu_grad" to "ബിരുദം അല്ലെങ്കിൽ മുകളിൽ",
                "pref_self" to "സ്വന്തം ബിസിനസ്",
                "pref_wage" to "ജോലി",
                "travel_local" to "എന്റെ ഗ്രാമം / അടുത്ത്",
                "travel_district" to "എന്റെ ജില്ലയിൽ എവിടെയും",
                "travel_any" to "എവിടെയും"
            )
        )
        return maps[lang]?.get(key) ?: if (lang != "en") enMap[key] else null
    }

    private fun extraInterestTranslations(lang: String, key: String): String? {
        val map = mapOf(
            "ta" to mapOf(
                "dairy" to "பால் / பால்பண்ணை",
                "cattle" to "கால்நடை",
                "goat" to "ஆடு",
                "poultry" to "கோழி",
                "farming" to "விவசாயம்",
                "food" to "உணவு பதப்படுத்துதல்",
                "machine" to "கணினி / இயந்திரம்",
                "textile" to "நெசவு",
                "construction" to "கட்டுமானம்",
                "tailor" to "தையல்"
            ),
            "hi" to mapOf(
                "dairy" to "दूध / डेयरी",
                "cattle" to "पशुधन",
                "goat" to "बकरी / भेड़",
                "poultry" to "मुर्गीपालन",
                "farming" to "खेती",
                "food" to "खाद्य प्रसंस्करण",
                "machine" to "कंप्यूटर / मशीनरी",
                "textile" to "कपड़ा / हथकरघा",
                "construction" to "निर्माण",
                "tailor" to "सिलाई"
            ),
            "te" to mapOf(
                "dairy" to "పాలు / డెయిరీ",
                "cattle" to "పశువులు",
                "goat" to "మేక / గొర్రె",
                "poultry" to "కోళ్లు",
                "farming" to "వ్యవసాయం",
                "food" to "ఆహార ప్రాసెసింగ్",
                "machine" to "కంప్యూటర్ / యంత్రాలు",
                "textile" to "నేత",
                "construction" to "నిర్మాణం",
                "tailor" to "కుట్టుపని"
            ),
            "kn" to mapOf(
                "dairy" to "ಹಾಲು / ಡೈರಿ",
                "cattle" to "ಜಾನುವಾರು",
                "goat" to "ಆಡು / ಕುರಿ",
                "poultry" to "ಕೋಳಿ",
                "farming" to "ಕೃಷಿ",
                "food" to "ಆಹಾರ ಸಂಸ್ಕರಣೆ",
                "machine" to "ಕಂಪ್ಯೂಟರ್ / ಯಂತ್ರಗಳು",
                "textile" to "ನೇಯ್ಗೆ",
                "construction" to "ನಿರ್ಮಾಣ",
                "tailor" to "ಬಟ್ಟೆ ಹೊಲಿಗೆ"
            ),
            "ml" to mapOf(
                "dairy" to "പാൽ / ഡെയറി",
                "cattle" to "കന്നുകാലി",
                "goat" to "ആട്",
                "poultry" to "കോഴി",
                "farming" to "കൃഷി",
                "food" to "ഭക്ഷ്യ സംസ്കരണം",
                "machine" to "కంప్యూటർ / യന്ത്രങ്ങൾ",
                "textile" to "നെയ്ത്ത്",
                "construction" to "നിർമാണം",
                "tailor" to "തയ്യൽ"
            )
        )
        return map[lang]?.get(key)
    }

    fun matchRoles(profile: UserProfile): List<MatchedRole> {
        val roles = getJobRoles()
        val results = mutableListOf<MatchedRole>()
        // LENIENT: if edu is null, assume class8 so we still show results instead of "no match"
        val eduRank = profile.education?.rank ?: EducationLevel.CLASS_8.rank
        
        for (role in roles) {
            if (!role.isValidName()) continue
            // LENIENT: don't skip if edu slightly lower, just penalize score instead of filtering
            val required = role.requiredEduRank()
            var score = 40
            if (required > eduRank) {
                score -= (required - eduRank) * 20 // Penalize but don't skip
                // Only skip if graduate required and user is below 8th
                if (required >= EducationLevel.GRADUATE.rank && eduRank < EducationLevel.CLASS_10.rank) continue
            }
            // Don't filter long term strictly - just penalize
            if (role.isLongTerm() && eduRank < EducationLevel.CLASS_10.rank) {
                score -= 15
            }
            
            var interestHit = false
            val matchedInterests = mutableListOf<String>()
            for (interest in profile.interests) {
                if (role.matchesInterest(interest)) {
                    interestHit = true
                    matchedInterests.add(interest)
                    score += 80
                }
            }
            
            var familyFit = false
            var familyNote = ""
            if (profile.familyOccupation.isNotBlank()) {
                if (role.matchesFamilyOccupation(profile.familyOccupation)) {
                    familyFit = true
                    score += 50
                    familyNote = "Builds on your family experience in ${profile.familyOccupation}"
                }
            }
            if (profile.currentLivelihood.isNotBlank()) {
                if (role.matchesFamilyOccupation(profile.currentLivelihood)) {
                    score += 30
                    if (familyNote.isBlank()) familyNote = "Related to your current work: ${profile.currentLivelihood}"
                }
                // Student special handling - boost machine/computer roles
                if (profile.currentLivelihood.lowercase().contains("student") && role.sector == "electronics" || role.sector.contains("it") || role.job_role.lowercase().contains("computer") || role.job_role.lowercase().contains("technician")) {
                    score += 40
                    if (!interestHit) {
                        interestHit = true
                        matchedInterests.add("machine")
                    }
                }
            }
            
            // If no interest hit, still give some score if family matches or if profile has skills
            if (!interestHit && !familyFit) {
                if (profile.skills.isNotEmpty()) {
                    for (skill in profile.skills) {
                        if (role.job_role.lowercase().contains(skill.lowercase()) || role.sector.lowercase().contains(skill.lowercase())) {
                            score += 50
                            interestHit = true
                            break
                        }
                    }
                }
                if (!interestHit) score -= 10 // Reduced penalty
            }
            
            if (role.isFundable()) score += 25
            when (profile.preference) {
                Preference.SELF -> if (role.selfEmploymentFit()) score += 22
                Preference.WAGE -> if (role.wageFit()) score += 22
                else -> {}
            }
            val centre = getCentreForDistrict(profile.district)
            if (centre != null) {
                if (role.sector == "agriculture" || role.sector == "food_processing") score += 14
                score += 10 // Bonus for having centre in district
            }
            when (profile.mobility) {
                Mobility.LOCAL -> if (centre != null) score += 20 else score -= 5
                Mobility.DISTRICT -> score += 10
                Mobility.STATE -> score += 5
                else -> {}
            }
            if (profile.physicalConstraints.isNotBlank()) {
                val pc = profile.physicalConstraints.lowercase()
                if (role.sector == "construction" && (pc.contains("cannot") || pc.contains("can't") || pc.contains("heavy"))) score -= 40
            }
            score += maxOf(0, 8 - role.levelInt())
            
            val reason = when {
                interestHit && familyFit -> "${tr("en", "reason_interest")} ${matchedInterests.joinToString(", ") { interestLabel("en", it) }} and builds on family occupation."
                interestHit -> "${tr("en", "reason_interest")} ${matchedInterests.joinToString(", ") { interestLabel("en", it) }}."
                familyFit -> familyNote
                else -> tr("en", "reason_base")
            }
            val skillGap = buildSkillGapNote(role, profile)
            val regionOpp = buildRegionOpportunity(role, profile)
            results.add(MatchedRole(role, score, reason, skillGap, centre, familyNote, regionOpp))
        }
        
        // If still no results, return top 3 by score even if low
        val sorted = results.sortedByDescending { it.score }
        return if (sorted.isEmpty()) {
            // Absolute fallback: return 3 agriculture roles that are most accessible
            getJobRoles().filter { it.sector == "agriculture" && it.requiredEduRank() <= 1 }.take(3).map { role ->
                MatchedRole(role, 10, "General opportunity in ${profile.district}", "Entry: ${role.levelLabel("Level")}", getCentreForDistrict(profile.district), "", "Available in Tamil Nadu")
            }
        } else {
            sorted.take(3)
        }
    }

    private fun buildSkillGapNote(role: JobRole, profile: UserProfile): String {
        val edu = profile.education
        val required = role.requiredEduRank()
        val userRank = edu?.rank ?: EducationLevel.CLASS_8.rank
        val base = if (userRank >= required) {
            when (edu) {
                EducationLevel.BELOW_8 -> "Entry: read & write — you qualify"
                EducationLevel.CLASS_8 -> "Entry: Class 8 — you qualify"
                EducationLevel.CLASS_10 -> "Entry: Class 10 — you qualify"
                EducationLevel.CLASS_12 -> "Entry: Class 12 — you qualify"
                EducationLevel.ITI_DIPLOMA -> "Entry: ITI/Diploma — you qualify"
                EducationLevel.GRADUATE -> "Entry: Graduate — you qualify"
                null -> "Entry: Class 8+ — you qualify (based on your profile)"
            }
        } else {
            val need = when (required) {
                0 -> "read & write"
                1 -> "Class 8"
                2 -> "Class 10"
                3 -> "Class 12"
                4 -> "ITI/Diploma"
                else -> "Graduate"
            }
            "Needs $need; you have ${edu?.name ?: "Class 8"} - still applicable, check with centre"
        }
        return if (profile.currentLivelihood.isNotBlank() && profile.interests.isNotEmpty()) "$base | Builds on: ${profile.currentLivelihood} → ${role.job_role}" else base
    }

    private fun buildRegionOpportunity(role: JobRole, profile: UserProfile): String {
        val district = profile.district
        val centre = getCentreForDistrict(district)
        return when {
            centre != null -> "Available at ${centre.name}, ${centre.district}. Local demand for ${role.sector} high in $district."
            profile.localOpportunity.isNotBlank() -> "Based on your note '${profile.localOpportunity}', ${role.sector} has local demand."
            district.isNotBlank() && getDistricts().without_centre.contains(district) -> "No verified centre in $district yet — confirm with TAHDCO. ${role.sector} demand in nearby districts."
            else -> "Region: ${role.sector} opportunities growing in Tamil Nadu per PM-AJAY"
        }
    }

    fun searchRoles(query: String, lang: String): List<JobRole> {
        if (query.isBlank()) return getJobRoles().take(50)
        val q = query.lowercase()
        return getJobRoles().filter {
            it.job_role.lowercase().contains(q) ||
                    it.qp_code.lowercase().contains(q) ||
                    it.sector.lowercase().contains(q) ||
                    it.ssc.lowercase().contains(q)
        }.take(50)
    }
}
