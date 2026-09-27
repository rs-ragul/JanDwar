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
                ?: i18n.interests["en"]?.get(key)
                ?: key
            InterestChip(key, label)
        }
    }

    fun tr(lang: String, key: String): String {
        val i18n = getI18n()
        // Check custom extra translations first
        extraTranslations(lang, key)?.let { return it }

        val localized = i18n.strings[lang]?.get(key)
        if (!localized.isNullOrBlank()) return localized
        val en = i18n.strings["en"]?.get(key)
        if (!en.isNullOrBlank()) return en
        return key
    }

    fun interestLabel(lang: String, key: String): String {
        val i18n = getI18n()
        return i18n.interests[lang]?.get(key)
            ?: i18n.interests["en"]?.get(key)
            ?: key
    }

    // Comprehensive extra translations that were hardcoded in old MainActivity
    private fun extraTranslations(lang: String, key: String): String? {
        val enMap = mapOf(
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
            "current_engine" to "Current Engine"
        )

        if (lang == "en") return enMap[key]

        // Tamil, Hindi, Telugu, Kannada, Malayalam overrides for critical keys
        val localizedOverrides = when (lang) {
            "ta" -> mapOf(
                "tagline" to "மக்களுக்கான நுழைவாயில்",
                "continue" to "தொடரவும்",
                "start" to "தொடங்கு",
                "back" to "பின்",
                "home_title" to "சரியான வாழ்வாதார பாதையை கண்டறியுங்கள்",
                "home_sub" to "சில எளிய கேள்விகளுக்கு பதில் அளிக்கவும். JanDwar ஆஃப்லைனில் வேலை செய்கிறது.",
                "stat_roles" to "NSQF பணிகள்",
                "stat_fundable" to "நிதியுதவி துறைகள்",
                "stat_districts" to "தமிழ்நாடு மாவட்டங்கள்",
                "stat_centres" to "சரிபார்க்கப்பட்ட மையங்கள்",
                "start_intake" to "என் வாய்ப்புகளை கண்டறி",
                "search" to "தேடு",
                "settings" to "அமைப்புகள்",
                "offline_data" to "ஆஃப்லைன் தரவு",
                "privacy" to "தனியுரிமை",
                "intake" to "உங்கள் விவரங்கள்",
                "speak_button" to "இப்போது பேசுங்கள்",
                "details" to "விவரம் பார்க்க",
                "level" to "நிலை",
                "short_term" to "குறுகிய காலம்",
                "long_term" to "நீண்ட காலம்",
                "asset_rule" to "கடனுடன் சொத்து செலவின் 50% அல்லது ரூ.50,000 வரை, எது குறைவோ அது.",
                "call" to "மையத்தை அழை",
                "voice_intro" to "உங்கள் குரல் உதவியாளரை சந்திக்கவும்",
                "voice_title" to "JanDwar-ிடம் உங்கள் தேவையை சொல்லுங்கள்",
                "voice_sub" to "இயல்பாக பேசுங்கள். தொடங்க ஒரு நல்ல வழியை காண்போம்.",
                "choose_path" to "உங்கள் வழியை தேர்ந்தெடுக்கவும்",
                "personalized_title" to "உங்களுக்கான பயிற்சி தேடல்",
                "browse_title" to "அனைத்து பயிற்சிகளையும் பார்க்கவும்",
                "close" to "மூடு",
                "voice_listening" to "கேட்கிறோம்",
                "stop_listening" to "கேட்பதை நிறுத்து",
                "options" to "உங்கள் விருப்பங்கள்",
                "honesty_note" to "JanDwar பாடம், மையம், கட்டணம் அல்லது உதவித்தொகையை கற்பனை செய்து காட்டாது."
            )
            "hi" -> mapOf(
                "tagline" to "नागरिकों का प्रवेश द्वार",
                "continue" to "जारी रखें",
                "start" to "शुरू करें",
                "back" to "वापस",
                "home_title" to "सही आजीविका रास्ता खोजें",
                "stat_roles" to "NSQF भूमिकाएं",
                "start_intake" to "मेरे विकल्प खोजें",
                "settings" to "सेटिंग्स",
                "speak_button" to "अब बोलिए",
                "voice_intro" to "अपने वॉइस सहायक से मिलें",
                "choose_path" to "अपना रास्ता चुनें",
                "close" to "बंद करें",
                "voice_listening" to "सुन रहे हैं",
                "stop_listening" to "सुनना रोकें"
            )
            else -> emptyMap()
        }

        return localizedOverrides[key] ?: if (lang != "en") enMap[key] else null
    }

    fun matchRoles(profile: UserProfile): List<MatchedRole> {
        val roles = getJobRoles()
        val results = mutableListOf<MatchedRole>()
        val eduRank = profile.education?.rank ?: 0

        for (role in roles) {
            if (!role.isValidName()) continue

            // Education gate
            if (role.requiredEduRank() > eduRank) continue

            // Long-term needs Class 10 per PM-AJAY rule
            if (role.isLongTerm() && eduRank < EducationLevel.CLASS_10.rank) continue

            // Physical constraints filter: if user says cannot do hard labour, avoid construction
            if (profile.physicalConstraints.isNotBlank()) {
                val pc = profile.physicalConstraints.lowercase()
                if ((pc.contains("cannot") || pc.contains("can't") || pc.contains("disability") || pc.contains("hard")) && role.sector == "construction") {
                    // Reduce score heavily but don't fully block unless explicit
                    // Continue with penalty
                }
            }

            var score = 40
            var interestHit = false
            var matchedInterests = mutableListOf<String>()

            for (interest in profile.interests) {
                if (role.matchesInterest(interest)) {
                    interestHit = true
                    matchedInterests.add(interest)
                    score += 80
                }
            }

            // Family occupation affinity (per problem statement: existing or traditional family occupations)
            var familyFit = false
            var familyNote = ""
            if (profile.familyOccupation.isNotBlank()) {
                if (role.matchesFamilyOccupation(profile.familyOccupation)) {
                    familyFit = true
                    score += 50
                    familyNote = "Builds on your family experience in ${profile.familyOccupation}"
                }
            }

            // Current livelihood affinity
            if (profile.currentLivelihood.isNotBlank()) {
                if (role.matchesFamilyOccupation(profile.currentLivelihood)) {
                    score += 30
                    if (familyNote.isBlank()) familyNote = "Related to your current work: ${profile.currentLivelihood}"
                }
            }

            if (!interestHit && !familyFit) score -= 25

            if (role.isFundable()) score += 25

            when (profile.preference) {
                Preference.SELF -> if (role.selfEmploymentFit()) score += 22
                Preference.WAGE -> if (role.wageFit()) score += 22
                else -> {}
            }

            val centre = getCentreForDistrict(profile.district)
            if (centre != null && role.sector == "agriculture") score += 14

            // Mobility handling
            when (profile.mobility) {
                Mobility.LOCAL -> if (centre != null) score += 20 else score -= 10
                Mobility.DISTRICT -> score += 10
                Mobility.STATE -> score += 5
                else -> {}
            }

            // Physical constraints penalty
            if (profile.physicalConstraints.isNotBlank()) {
                val pc = profile.physicalConstraints.lowercase()
                if (role.sector == "construction" && (pc.contains("cannot") || pc.contains("can't"))) {
                    score -= 40
                }
            }

            // Lower NSQF level for lower-literacy users gets slight boost
            score += maxOf(0, 8 - role.levelInt())

            val reason = when {
                interestHit && familyFit -> "${tr("en", "reason_interest")} ${matchedInterests.joinToString(", ") { interestLabel("en", it) }} and builds on family occupation."
                interestHit -> "${tr("en", "reason_interest")} ${matchedInterests.joinToString(", ") { interestLabel("en", it) }}."
                familyFit -> familyNote
                else -> tr("en", "reason_base")
            }

            // Skill-gap line per recommendation
            val skillGap = buildSkillGapNote(role, profile)

            // Region-specific opportunity
            val regionOpp = buildRegionOpportunity(role, profile)

            results.add(MatchedRole(role, score, reason, skillGap, centre, familyNote, regionOpp))
        }

        return results.sortedByDescending { it.score }.take(3)
    }

    private fun buildSkillGapNote(role: JobRole, profile: UserProfile): String {
        val edu = profile.education
        val required = role.requiredEduRank()
        val userRank = edu?.rank ?: 0

        val base = if (userRank >= required) {
            when (edu) {
                EducationLevel.BELOW_8 -> "Entry: read & write — you qualify"
                EducationLevel.CLASS_8 -> "Entry: Class 8 — you qualify"
                EducationLevel.CLASS_10 -> "Entry: Class 10 — you qualify"
                EducationLevel.CLASS_12 -> "Entry: Class 12 — you qualify"
                EducationLevel.ITI_DIPLOMA -> "Entry: ITI/Diploma — you qualify"
                EducationLevel.GRADUATE -> "Entry: Graduate — you qualify"
                null -> "Entry: ${role.levelLabel("Level")} — check eligibility"
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
            "Needs $need for this course; see short-term options instead."
        }

        // Add skill gap from problem statement: compare current livelihood vs role
        return if (profile.currentLivelihood.isNotBlank() && profile.interests.isNotEmpty()) {
            "$base | Builds on: ${profile.currentLivelihood} → ${role.job_role}"
        } else base
    }

    private fun buildRegionOpportunity(role: JobRole, profile: UserProfile): String {
        val district = profile.district
        val centre = getCentreForDistrict(district)
        return when {
            centre != null -> "Available at ${centre.name}, ${centre.district}. Local market demand for ${role.sector} is high in $district."
            profile.localOpportunity.isNotBlank() -> "Based on your note '${profile.localOpportunity}', this ${role.sector} role has local demand."
            district.isNotBlank() && getDistricts().without_centre.contains(district) -> "No verified centre in $district yet — confirm with TAHDCO. This ${role.sector} trade has demand in nearby districts."
            else -> "Region-specific: ${role.sector} opportunities growing in Tamil Nadu per PM-AJAY GIA."
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
