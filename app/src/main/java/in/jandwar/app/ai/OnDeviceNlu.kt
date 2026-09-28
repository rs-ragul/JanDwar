package `in`.jandwar.app.ai

import `in`.jandwar.app.data.local.AssetDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fully offline natural-language understanding for the six supported
 * languages.
 *
 * This replaces the old `DeterministicParser`, which only handled English and
 * mis-detected education for almost every non-English utterance. It performs
 * **multi-slot** extraction: a single sentence such as
 * *"I studied up to 10th, my family does dairy farming in Erode"* fills three
 * slots at once, so the interview finishes in far fewer turns.
 */
@Singleton
class OnDeviceNlu @Inject constructor(
    private val assetDataSource: AssetDataSource
) {

    private val districtIndex: List<Pair<String, List<String>>> by lazy { buildDistrictIndex() }

    /** Extracts everything it can from [raw]. Never throws. */
    fun extract(raw: String, lang: String): ProfileFragment {
        val frag = ProfileFragment()
        val text = raw.trim()
        if (text.isEmpty()) return frag
        val s = normalise(text)

        frag.district = detectDistrict(s, text)
        frag.edu = detectEducation(s)
        frag.preference = detectPreference(s)
        frag.mobility = detectMobility(s)
        detectInterests(s).forEach { if (!frag.interests.contains(it)) frag.interests.add(it) }
        frag.physicalConstraints = detectConstraints(s, text)

        val occupation = detectOccupation(s)
        if (occupation != null) {
            if (mentionsFamily(s)) frag.familyOccupation = occupation
            else frag.currentLivelihood = occupation
        }
        if (frag.currentLivelihood == null) detectStudentStatus(s, text)?.let { frag.currentLivelihood = it }

        return frag
    }

    /**
     * Extracts an answer that we know relates to a specific slot (the question
     * we just asked), so short replies like "ten" or "my own shop" land in the
     * right place even when they carry no other signal.
     */
    fun extractForSlot(raw: String, lang: String, slot: ProfileFragment.Slot): ProfileFragment {
        val frag = extract(raw, lang)
        val text = raw.trim()
        if (text.isEmpty()) return frag
        val s = normalise(text)

        when (slot) {
            ProfileFragment.Slot.EDUCATION ->
                if (!frag.hasEdu()) frag.edu = detectEducationLoose(s)

            ProfileFragment.Slot.FAMILY_OCCUPATION ->
                if (!frag.hasFamilyOccupation()) {
                    frag.familyOccupation = frag.currentLivelihood ?: freeText(text)
                    if (frag.currentLivelihood != null && mentionsFamily(s)) frag.currentLivelihood = null
                }

            ProfileFragment.Slot.CURRENT_LIVELIHOOD ->
                if (!frag.hasCurrentLivelihood()) frag.currentLivelihood = freeText(text)

            ProfileFragment.Slot.INTERESTS ->
                if (!frag.hasInterests()) {
                    // Treat the reply as a free-text skill so matching can still use it.
                    freeText(text)?.let { frag.skills.add(it) }
                }

            ProfileFragment.Slot.PREFERENCE ->
                if (!frag.hasPref()) frag.preference = detectPreferenceLoose(s)

            ProfileFragment.Slot.MOBILITY ->
                if (!frag.hasMobility()) frag.mobility = detectMobilityLoose(s)

            ProfileFragment.Slot.DISTRICT -> Unit // district needs an exact hit
        }
        return frag
    }

    // ── Normalisation ───────────────────────────────────────────────────────

    private fun normalise(t: String): String =
        t.lowercase()
            .replace(Regex("[\\p{Punct}&&[^+]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.hasAny(vararg words: String): Boolean =
        words.any { this.contains(it) }

    private fun String.hasAny(words: List<String>): Boolean =
        words.any { this.contains(it) }

    // ── Education ───────────────────────────────────────────────────────────

    private val GRAD_WORDS = listOf(
        "graduate", "degree", "bachelor", "master", "b tech", "btech", "b e ", "be ",
        "bsc", "b sc", "bcom", "b com", "bca", "mca", "mba", "bba", "ba ", "ma ",
        "msc", "m sc", "engineering", "college", "university", "phd", "nursing",
        "pharmacy", "பட்டம்", "பட்டப்படிப்பு", "கல்லூரி", "डिग्री", "स्नातक", "कॉलेज",
        "డిగ్రీ", "కళాశాల", "ಪದವಿ", "ಕಾಲೇಜು", "ബിരുദം", "കോളേജ്"
    )
    private val ITI_WORDS = listOf(
        "iti", "i t i", "diploma", "polytechnic", "டிப்ளோமா", "பாலிடெக்னிக்",
        "डिप्लोमा", "पॉलिटेक्निक", "డిప్లొమా", "ಡಿಪ್ಲೊಮಾ", "ഡിപ്ലോമ"
    )
    private val C12_WORDS = listOf(
        "12th", "12 th", "twelfth", "hsc", "plus two", "+2", "higher secondary", "class 12",
        "பன்னிரண்டு", "12 ம்", "மேல்நிலை", "बारहवीं", "12वीं", "పన్నెండు", "ಹನ್ನೆರಡು", "പന്ത്രണ്ട്"
    )
    private val C10_WORDS = listOf(
        "10th", "10 th", "tenth", "sslc", "matric", "class 10", "பத்தாம்", "பத்து",
        "दसवीं", "10वीं", "పదవ", "ಹತ್ತನೇ", "പത്താം"
    )
    private val C8_WORDS = listOf(
        "8th", "8 th", "eighth", "class 8", "எட்டாம்", "எட்டு", "आठवीं", "8वीं",
        "ఎనిమిదవ", "ಎಂಟನೇ", "എട്ടാം"
    )
    private val BELOW8_WORDS = listOf(
        "below 8", "5th", "fifth", "did not study", "didnt study", "no school",
        "never went to school", "illiterate", "read and write", "just read",
        "படிக்கவில்லை", "பள்ளி செல்லவில்லை", "ஐந்தாம்", "नहीं पढ़ा", "पांचवीं",
        "చదవలేదు", "ಓದಿಲ್ಲ", "പഠിച്ചിട്ടില്ല"
    )

    private fun detectEducation(s: String): String? = when {
        s.hasAny(BELOW8_WORDS) -> "class5"
        s.hasAny(ITI_WORDS) -> "iti_diploma"
        s.hasAny(C12_WORDS) -> "class12"
        s.hasAny(C10_WORDS) -> "class10"
        s.hasAny(C8_WORDS) -> "class8"
        s.hasAny(GRAD_WORDS) -> "graduate"
        else -> null
    }

    /** Looser pass used only when we explicitly asked about education. */
    private fun detectEducationLoose(s: String): String? {
        detectEducation(s)?.let { return it }
        // Bare numbers: "ten", "12", "8"
        val num = Regex("\\b(\\d{1,2})\\b").find(s)?.groupValues?.get(1)?.toIntOrNull()
        if (num != null) return when {
            num >= 11 -> "class12"
            num >= 9 -> "class10"
            num >= 7 -> "class8"
            num in 1..6 -> "class5"
            else -> null
        }
        val words = mapOf(
            "five" to "class5", "eight" to "class8", "ten" to "class10", "twelve" to "class12",
            "ஐந்து" to "class5", "எட்டு" to "class8", "பத்து" to "class10", "பன்னிரண்டு" to "class12",
            "पाँच" to "class5", "आठ" to "class8", "दस" to "class10", "बारह" to "class12"
        )
        words.forEach { (k, v) -> if (s.contains(k)) return v }
        return null
    }

    // ── Preference ──────────────────────────────────────────────────────────

    private val SELF_WORDS = listOf(
        "own business", "my own", "self employ", "self-employ", "entrepreneur", "start a shop",
        "own shop", "own work", "business", "startup", "enterprise", "சொந்த", "தொழில்",
        "சொந்தமா", "अपना काम", "खुद का", "व्यवसाय", "स्वरोजगार", "సొంత", "వ్యాపారం",
        "ಸ್ವಂತ", "ಉದ್ಯಮ", "സ്വന്തം", "ബിസിനസ്"
    )
    private val WAGE_WORDS = listOf(
        "job", "wage", "employer", "salary", "company", "placement", "work for",
        "வேலை", "சம்பளம்", "நிறுவனம்", "नौकरी", "वेतन", "कंपनी", "ఉద్యోగం", "జీతం",
        "ಕೆಲಸ", "ಸಂಬಳ", "ജോലി", "ശമ്പളം"
    )

    private fun detectPreference(s: String): String? = when {
        s.hasAny(SELF_WORDS) -> "pref_self"
        s.hasAny(WAGE_WORDS) -> "pref_wage"
        else -> null
    }

    private fun detectPreferenceLoose(s: String): String? {
        detectPreference(s)?.let { return it }
        if (s.hasAny("first", "one", "1", "முதல்", "पहला")) return "pref_self"
        if (s.hasAny("second", "two", "2", "இரண்டு", "दूसरा")) return "pref_wage"
        return null
    }

    // ── Mobility ────────────────────────────────────────────────────────────

    private val LOCAL_WORDS = listOf(
        "village", "nearby", "near by", "close", "local", "cannot travel", "cant travel",
        "can not travel", "only here", "ஊரில்", "அருகில்", "வெளியே போக முடியாது",
        "गाँव", "पास", "नहीं जा", "ఊరు", "దగ్గర", "ಊರು", "ಹತ್ತಿರ", "ഗ്രാമം", "അടുത്ത്"
    )
    private val DISTRICT_WORDS = listOf(
        "district", "மாவட்ட", "जिला", "जिले", "జిల్లా", "ಜಿಲ್ಲೆ", "ജില്ല"
    )
    private val STATE_WORDS = listOf(
        "anywhere", "any place", "state", "tamil nadu", "tamilnadu", "far", "outside",
        "எங்கும்", "எங்கு வேண்டுமானாலும்", "தமிழ்நாடு", "कहीं भी", "राज्य", "तमिलनाडु",
        "ఎక్కడైనా", "రాష్ట్రం", "ಎಲ್ಲಿಯಾದರೂ", "ರಾಜ್ಯ", "എവിടെയും", "സംസ്ഥാനം"
    )

    private fun detectMobility(s: String): String? = when {
        s.hasAny(LOCAL_WORDS) -> "local"
        s.hasAny(STATE_WORDS) -> "state"
        s.hasAny(DISTRICT_WORDS) -> "district"
        else -> null
    }

    private fun detectMobilityLoose(s: String): String? {
        detectMobility(s)?.let { return it }
        if (s.hasAny("yes", "can", "ok", "சரி", "हाँ", "అవును", "ಹೌದು", "അതെ")) return "district"
        if (s.hasAny("no", "illai", "இல்லை", "नहीं", "కాదు", "ಇಲ್ಲ", "ഇല്ല")) return "local"
        return null
    }

    // ── Interests ───────────────────────────────────────────────────────────

    private val INTEREST_WORDS: Map<String, List<String>> = mapOf(
        "dairy" to listOf("dairy", "milk", "பால்", "பால் பண்ணை", "डेयरी", "दूध", "పాలు", "ಹಾಲು", "പാൽ"),
        "cattle" to listOf("cattle", "cow", "livestock", "buffalo", "மாடு", "கால்நடை", "गाय", "पशु",
            "ఆవు", "పశు", "ಹಸು", "ಜಾನುವಾರು", "പശു", "കന്നുകാലി"),
        "goat" to listOf("goat", "sheep", "ஆடு", "செம்மறி", "बकरी", "भेड़", "మేక", "గొర్రె",
            "ಮೇಕೆ", "ಕುರಿ", "ആട്"),
        "poultry" to listOf("poultry", "chicken", "hen", "கோழி", "मुर्गी", "కోడి", "ಕೋಳಿ", "കോഴി"),
        "farming" to listOf("farm", "agri", "crop", "cultivat", "விவசாய", "பயிர்", "खेती", "फसल",
            "కృషి", "వ్యవసాయ", "పంట", "ಕೃಷಿ", "ಬೆಳೆ", "കൃഷി"),
        "food" to listOf("food", "bakery", "baker", "cooking", "snack", "உணவு", "பேக்கரி",
            "खाद्य", "बेकरी", "खाना", "ఆహార", "ಆಹಾರ", "ഭക്ഷ്യ"),
        "machine" to listOf("machine", "mechanic", "repair", "electric", "electronic", "motor",
            "technician", "welding", "இயந்திர", "மெக்கானிக்", "பழுது", "மின்", "मशीन", "मरम्मत",
            "बिजली", "యంత్ర", "ಯಂತ್ರ", "യന്ത്ര"),
        "textile" to listOf("textile", "weav", "loom", "handloom", "நெசவு", "தறி", "ஜவுளி",
            "बुनाई", "करघा", "कपड़ा", "నేత", "ನೇಯ್ಗೆ", "നെയ്ത്ത്"),
        "construction" to listOf("construction", "mason", "building", "plumb", "carpent", "paint",
            "கட்டுமான", "கொத்தனார்", "निर्माण", "मिस्त्री", "నిర్మాణ", "ನಿರ್ಮಾಣ", "നിർമാണ"),
        "tailor" to listOf("tailor", "stitch", "sewing", "garment", "தையல்", "சிலாய்", "सिलाई",
            "दर्जी", "కుట్టు", "ಹೊಲಿಗೆ", "തയ്യൽ")
    )

    private fun detectInterests(s: String): List<String> =
        INTEREST_WORDS.filter { (_, words) -> s.hasAny(words) }.keys.toList()

    // ── Occupation ──────────────────────────────────────────────────────────

    private val FAMILY_MARKERS = listOf(
        "family", "father", "mother", "parents", "traditional", "appa", "amma",
        "குடும்ப", "அப்பா", "அம்மா", "பெற்றோர்", "பாரம்பரிய",
        "परिवार", "पिता", "माता", "माँ", "पारंपरिक",
        "కుటుంబ", "నాన్న", "అమ్మ", "ಕುಟುಂಬ", "ಅಪ್ಪ", "ಅಮ್ಮ", "കുടുംബ", "അച്ഛൻ", "അമ്മ"
    )

    private fun mentionsFamily(s: String): Boolean = s.hasAny(FAMILY_MARKERS)

    private val OCCUPATION_MAP: List<Pair<List<String>, String>> = listOf(
        listOf("dairy", "milk", "பால்", "डेयरी", "పాలు", "ಹಾಲು", "പാൽ") to "Dairy farming",
        listOf("cattle", "cow", "மாடு", "கால்நடை", "गाय", "ఆవు", "ಹಸು", "പശു") to "Cattle rearing",
        listOf("goat", "sheep", "ஆடு", "बकरी", "మేక", "ಮೇಕೆ", "ആട്") to "Goat rearing",
        listOf("poultry", "chicken", "கோழி", "मुर्गी", "కోడి", "ಕೋಳಿ", "കോഴി") to "Poultry",
        listOf("farm", "agri", "விவசாய", "खेती", "కృషి", "వ్యవసాయ", "ಕೃಷಿ", "കൃഷി") to "Farming",
        listOf("tailor", "stitch", "தையல்", "सिलाई", "కుట్టు", "ಹೊಲಿಗೆ", "തയ്യൽ") to "Tailoring",
        listOf("weav", "loom", "நெசவு", "बुनाई", "నేత", "ನೇಯ್ಗೆ", "നെയ്ത്ത്") to "Weaving",
        listOf("construction", "mason", "கட்டுமான", "निर्माण", "నిర్మాణ", "ನಿರ್ಮಾಣ", "നിർമാണ") to "Construction work",
        listOf("coolie", "daily wage", "கூலி", "दिहाड़ी", "మజూరి", "ಕೂಲಿ", "കൂലി") to "Daily wage labour",
        listOf("driver", "auto", "ஓட்டுநர்", "ड्राइवर", "డ్రైవర్", "ಚಾಲಕ", "ഡ്രൈവർ") to "Driving",
        listOf("shop", "petty", "கடை", "दुकान", "దుకాణం", "ಅಂಗಡಿ", "കട") to "Small shop",
        listOf("cook", "hotel", "catering", "சமையல்", "खाना", "వంట", "ಅಡುಗೆ", "പാചകം") to "Cooking / catering",
        listOf("fish", "மீன்", "मछली", "చేప", "ಮೀನು", "മീൻ") to "Fishing",
        listOf("government", "govt", "அரசு", "सरकारी", "ప్రభుత్వ", "ಸರ್ಕಾರಿ", "സർക്കാർ") to "Government service"
    )

    private fun detectOccupation(s: String): String? =
        OCCUPATION_MAP.firstOrNull { (keys, _) -> s.hasAny(keys) }?.second

    private val STUDENT_WORDS = listOf(
        "student", "studying", "pursuing", "college", "school", "படிக்கிறேன்", "மாணவ",
        "पढ़ रहा", "पढ़ाई", "छात्र", "చదువుతున్న", "విద్యార్థి", "ಓದುತ್ತಿದ್ದೇನೆ", "ವಿದ್ಯಾರ್ಥಿ",
        "പഠിക്കുന്നു", "വിദ്യാർത്ഥി"
    )
    private val UNEMPLOYED_WORDS = listOf(
        "no work", "unemployed", "looking for", "jobless", "nothing", "idle",
        "வேலை இல்லை", "வேலை தேடு", "काम नहीं", "बेरोजगार", "పని లేదు", "ಕೆಲಸ ಇಲ್ಲ", "ജോലി ഇല്ല"
    )

    private fun detectStudentStatus(s: String, original: String): String? = when {
        s.hasAny(STUDENT_WORDS) -> "Student"
        s.hasAny(UNEMPLOYED_WORDS) -> "Looking for work"
        else -> null
    }

    // ── Physical constraints ────────────────────────────────────────────────

    private val CONSTRAINT_WORDS = listOf(
        "cannot lift", "cant lift", "back pain", "disabil", "disabled", "handicap",
        "injury", "injured", "weak", "surgery", "cannot stand", "cannot walk",
        "தூக்க முடியாது", "வலி", "ஊனம்", "नहीं उठा", "दर्द", "विकलांग",
        "ఎత్తలేను", "నొప్పి", "ಎತ್ತಲಾಗದು", "ನೋವು", "ഉയർത്താൻ കഴിയില്ല", "വേദന"
    )

    private fun detectConstraints(s: String, original: String): String? =
        if (s.hasAny(CONSTRAINT_WORDS)) original.trim().take(120) else null

    // ── District ────────────────────────────────────────────────────────────

    /** English name → alternate spellings / native forms. */
    private val DISTRICT_ALIASES: Map<String, List<String>> = mapOf(
        "Ariyalur" to listOf("அரியலூர்", "अरियलूर"),
        "Chengalpattu" to listOf("செங்கல்பட்டு", "chengalpet", "चेंगलपट्टू"),
        "Chennai" to listOf("சென்னை", "चेन्नई", "madras", "చెన్నై", "ಚೆನ್ನೈ", "ചെന്നൈ"),
        "Coimbatore" to listOf("கோயம்புத்தூர்", "कोयंबटूर", "kovai", "கோவை", "కోయంబత్తూరు", "ಕೊಯಮತ್ತೂರು"),
        "Cuddalore" to listOf("கடலூர்", "कुड्डालोर"),
        "Dharmapuri" to listOf("தர்மபுரி", "धर्मपुरी"),
        "Dindigul" to listOf("திண்டுக்கல்", "डिंडीगुल"),
        "Erode" to listOf("ஈரோடு", "इरोड", "ఈరోడ్", "ಈರೋಡ್"),
        "Kallakurichi" to listOf("கள்ளக்குறிச்சி", "कल्लाकुरिची"),
        "Kancheepuram" to listOf("காஞ்சிபுரம்", "कांचीपुरम", "kanchipuram"),
        "Kanniyakumari" to listOf("கன்னியாகுமரி", "कन्याकुमारी", "kanyakumari", "nagercoil"),
        "Karur" to listOf("கரூர்", "करूर"),
        "Krishnagiri" to listOf("கிருஷ்ணகிரி", "कृष्णागिरी"),
        "Madurai" to listOf("மதுரை", "मदुरै", "మదురై", "ಮಧುರೈ", "മധുര"),
        "Mayiladuthurai" to listOf("மயிலாடுதுறை", "मयिलादुथुरै"),
        "Nagapattinam" to listOf("நாகப்பட்டினம்", "नागपट्टिनम"),
        "Namakkal" to listOf("நாமக்கல்", "नामक्कल"),
        "Nilgiris" to listOf("நீலகிரி", "नीलगिरी", "ooty", "உதகமண்டலம்"),
        "Perambalur" to listOf("பெரம்பலூர்", "पेरम्बलूर"),
        "Pudukkottai" to listOf("புதுக்கோட்டை", "पुदुक्कोट्टई"),
        "Ramanathapuram" to listOf("இராமநாதபுரம்", "रामनाथपुरम", "ramnad"),
        "Ranipet" to listOf("இராணிப்பேட்டை", "रानीपेट"),
        "Salem" to listOf("சேலம்", "सेलम", "సేలం", "ಸೇಲಂ"),
        "Sivaganga" to listOf("சிவகங்கை", "शिवगंगा", "sivagangai"),
        "Tenkasi" to listOf("தென்காசி", "तेनकासी"),
        "Thanjavur" to listOf("தஞ்சாவூர்", "तंजावुर", "tanjore"),
        "Theni" to listOf("தேனி", "थेनी"),
        "Thoothukudi" to listOf("தூத்துக்குடி", "तूतुकुडी", "tuticorin"),
        "Tiruchirappalli" to listOf("திருச்சிராப்பள்ளி", "तिरुचिरापल्ली", "trichy", "திருச்சி"),
        "Tirunelveli" to listOf("திருநெல்வேலி", "तिरुनेलवेली", "nellai"),
        "Tirupattur" to listOf("திருப்பத்தூர்", "तिरुपत्तूर"),
        "Tiruppur" to listOf("திருப்பூர்", "तिरुपुर", "tirupur"),
        "Tiruvallur" to listOf("திருவள்ளூர்", "तिरुवल्लूर"),
        "Tiruvannamalai" to listOf("திருவண்ணாமலை", "तिरुवन्नामलई"),
        "Tiruvarur" to listOf("திருவாரூர்", "तिरुवारूर"),
        "Vellore" to listOf("வேலூர்", "वेल्लोर"),
        "Villupuram" to listOf("விழுப்புரம்", "विल्लुपुरम", "viluppuram"),
        "Virudhunagar" to listOf("விருதுநகர்", "विरुधुनगर")
    )

    private fun buildDistrictIndex(): List<Pair<String, List<String>>> {
        val all = try {
            assetDataSource.loadDistricts().all
        } catch (e: Exception) {
            emptyList()
        }
        return all.map { d ->
            val forms = mutableListOf(d.lowercase())
            DISTRICT_ALIASES[d]?.forEach { forms.add(it.lowercase()) }
            d to forms
        }
    }

    private fun detectDistrict(s: String, original: String): String? {
        val lowerOriginal = original.lowercase()
        // Longest form first so "Tiruvannamalai" wins over "Tiruvallur"-like prefixes.
        return districtIndex
            .flatMap { (name, forms) -> forms.map { name to it } }
            .sortedByDescending { it.second.length }
            .firstOrNull { (_, form) -> s.contains(form) || lowerOriginal.contains(form) }
            ?.first
    }

    // ── Free text ───────────────────────────────────────────────────────────

    private val NON_ANSWERS = listOf(
        "yes", "no", "ok", "okay", "hmm", "nothing", "dont know", "don't know",
        "சரி", "இல்லை", "தெரியாது", "हाँ", "नहीं", "पता नहीं"
    )

    /** Keeps a short user phrase as-is when we cannot classify it. */
    private fun freeText(original: String): String? {
        val t = original.trim()
        if (t.length < 2 || t.length > 140) return null
        if (NON_ANSWERS.any { t.lowercase() == it }) return null
        return t.replaceFirstChar { it.uppercase() }
    }
}
