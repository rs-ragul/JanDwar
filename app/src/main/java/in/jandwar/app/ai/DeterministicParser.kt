package `in`.jandwar.app.ai

import `in`.jandwar.app.data.local.AssetDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeterministicParser @Inject constructor(
    private val assetDataSource: AssetDataSource
) : NluExtractor {

    override fun extract(
        text: String,
        langCode: String,
        currentProfile: ProfileFragment?,
        isOnline: Boolean,
        callback: NluExtractor.Callback
    ) {
        try {
            val result = parse(text, langCode)
            callback.onResult(result)
        } catch (e: Exception) {
            callback.onError(e.message ?: "Parse failed")
        }
    }

    fun parse(originalSentence: String, langCode: String = "en"): ProfileFragment {
        val sentence = originalSentence.lowercase().trim()
        val s = sentence
        val frag = ProfileFragment()

        try {
            val districts = assetDataSource.loadDistricts()
            for (d in districts.all) {
                if (s.contains(d.lowercase())) {
                    frag.district = d
                    break
                }
            }
        } catch (_: Exception) {}

        // Education - English + Tamil + Hindi + Telugu + Kannada + Malayalam keywords
        when {
            s.contains("phd") || s.contains("doctorate") || s.contains("m.tech") || s.contains("mtech") || s.contains("master") || s.contains("post graduate") || s.contains("postgraduate") -> frag.edu = "graduate"
            s.contains("b.tech") || s.contains("btech") || s.contains("b.e") || s.contains("be ") || s.contains("bachelor") || s.contains("engineering") || s.contains("computer science") || s.contains("bsc") || s.contains("b.sc") || s.contains("bcom") || s.contains("ba ") || s.contains("degree") || s.contains("college") || s.contains("graduate") || s.contains("graduation") -> frag.edu = "graduate"
            s.contains("பட்டதாரி") || s.contains("பட்டம்") || s.contains("கல்லூரி") || s.contains("பொறியியல்") || s.contains("இளங்கலை") -> frag.edu = "graduate"
            s.contains("स्नातक") || s.contains("इंजीनियरिंग") || s.contains("डिग्री") || s.contains("कॉलेज") -> frag.edu = "graduate"
            s.contains("డిగ్రీ") || s.contains("ఇంజనీరింగ్") || s.contains("కాలేజ్") -> frag.edu = "graduate"
            s.contains("ಪದವಿ") || s.contains("ಇಂಜಿನಿಯರಿಂಗ್") || s.contains("ಕಾಲೇಜು") -> frag.edu = "graduate"
            s.contains("ബിരുദം") || s.contains("എഞ്ചിനീയറിംഗ്") || s.contains("കോളേജ്") -> frag.edu = "graduate"
            s.contains("12th") || s.contains("class 12") || s.contains("twelfth") || s.contains("hsc") || s.contains("12 வது") || s.contains("बारहवीं") || s.contains("intermediate") || s.contains("plus two") || s.contains("+2") || s.contains("12ம் வகுப்பு") -> frag.edu = "class12"
            s.contains("பன்னிரண்டாம்") || s.contains("மேல்நிலை") -> frag.edu = "class12"
            s.contains("10th") || s.contains("class 10") || s.contains("tenth") || s.contains("sslc") || s.contains("10 வது") || s.contains("दसवीं") || s.contains("matric") || s.contains("10ம் வகுப்பு") -> frag.edu = "class10"
            s.contains("பத்தாம்") -> frag.edu = "class10"
            s.contains("8th") || s.contains("class 8") || s.contains("eighth") || s.contains("8 வது") || s.contains("8th pass") || s.contains("8ம் வகுப்பு") -> frag.edu = "class8"
            s.contains("எட்டாம்") -> frag.edu = "class8"
            s.contains("below") || s.contains("read") || s.contains("write") || s.contains("5th") || s.contains("class 5") || s.contains("illiterate") || s.contains("no school") -> frag.edu = "class5"
            s.contains("iti") || s.contains("diploma") || s.contains("ஐடிஐ") || s.contains("டிப்ளமோ") -> frag.edu = "class12"
        }

        when {
            s.contains("own") || s.contains("self") || s.contains("business") || s.contains("entrepreneur") || s.contains("my own") || s.contains("shop") || s.contains("enterprise") || s.contains("startup") || s.contains("சொந்த") || s.contains("स्वयं") || s.contains("सொंत") || s.contains("సొంత") || s.contains("ಸ್ವಂತ") || s.contains("സ്വന്തം") -> frag.preference = "pref_self"
            s.contains("job") || s.contains("wage") || s.contains("employer") || s.contains("salary") || s.contains("company") || s.contains("private") || s.contains("placement") || s.contains("வேலை") || s.contains("नौकरी") || s.contains("ఉద్యోగం") || s.contains("ಕೆಲಸ") || s.contains("ജോലി") -> frag.preference = "pref_wage"
        }

        when {
            s.contains("village") || s.contains("nearby") || s.contains("local") || s.contains("close") || s.contains("cannot travel") || s.contains("can't travel") || s.contains("அருகில்") || s.contains("गाँव") || s.contains("సమీపంలో") -> frag.mobility = "local"
            s.contains("district") || s.contains("மாவட்டம்") || s.contains("जिला") -> frag.mobility = "district"
            s.contains("anywhere") || s.contains("state") || s.contains("tamil nadu") || s.contains("far") || s.contains("any place") || s.contains("எங்கும்") || s.contains("कहीं भी") -> frag.mobility = "state"
        }

        val familyIndicators = listOf("family", "father", "mother", "parents", "traditional", "generations", "குடும்ப", "परिवार", "पारंपरिक", "my father", "my mother", "my family", "family is", "father is", "mother is", "కుటుంబ", "ಕುಟುಂಬ", "കുടുംബ")
        val hasFamilyContext = familyIndicators.any { s.contains(it) }

        if (s.contains("government employee") || s.contains("govt employee") || s.contains("government job") && hasFamilyContext || s.contains("govt job") && hasFamilyContext || s.contains("sarkari naukri") || s.contains("அரசு") || s.contains("सरकारी")) {
            frag.familyOccupation = "Government employee"
        } else if ((s.contains("farm") || s.contains("agri") || s.contains("விவசாய") || s.contains("खेती")) && (hasFamilyContext || s.length < 60)) {
            if (s.contains("family") || hasFamilyContext || s.contains("father") || s.contains("mother") || s.contains("குடும்ப") || s.contains("परिवार")) {
                frag.familyOccupation = "Farming"
            }
        } else if (s.contains("cattle") && hasFamilyContext) frag.familyOccupation = "Cattle rearing"
        else if (s.contains("dairy") && hasFamilyContext) frag.familyOccupation = "Dairy farming"
        else if (s.contains("goat") && hasFamilyContext) frag.familyOccupation = "Goat rearing"
        else if (s.contains("tailor") && hasFamilyContext) frag.familyOccupation = "Tailoring"
        else if ((s.contains("weav") || s.contains("loom")) && hasFamilyContext) frag.familyOccupation = "Weaving"
        else if (s.contains("construct") && hasFamilyContext) frag.familyOccupation = "Construction labour"
        else if (s.contains("labour") && hasFamilyContext) frag.familyOccupation = "Daily wage labour"
        else if (s.contains("fisher") && hasFamilyContext) frag.familyOccupation = "Fishing"
        else if (s.contains("business") && hasFamilyContext) frag.familyOccupation = "Business"

        if (frag.familyOccupation == null && hasFamilyContext && originalSentence.length in 5..120 && !s.contains("?")) {
            val occupationKeywords = listOf("farm", "cattle", "dairy", "goat", "tailor", "weav", "labour", "construct", "government", "employee", "business", "shop", "teacher", "driver", "fishing", "விவசாய", "அரசு")
            if (occupationKeywords.any { s.contains(it) }) {
                var cleaned = originalSentence.trim()
                if (cleaned.length > 80) cleaned = cleaned.take(80)
                frag.familyOccupation = cleaned
            }
        }

        when {
            s.contains("currently studying") || s.contains("i am studying") || s.contains("i'm studying") || s.contains("i am a student") || s.contains("i'm a student") || s.contains("i am just a student") || s.contains("currently a student") || s.contains("studying in") || s.contains("student") && (s.contains("currently") || s.contains("i am") || s.length < 50) -> frag.currentLivelihood = "Student"
            s.contains("மாணவர்") || s.contains("படிக்கிறேன்") || s.contains("छात्र") || s.contains("विद्यार्थी") || s.contains("విద్యార్థి") || s.contains("ವಿದ್ಯಾರ್ಥಿ") || s.contains("വിദ്യാർത്ഥി") -> frag.currentLivelihood = "Student"
            s.contains("computer science") && s.contains("study") -> frag.currentLivelihood = "Student - Computer Science"
            s.contains("engineering") && (s.contains("study") || s.contains("student")) -> frag.currentLivelihood = "Student - Engineering"
            s.contains("daily wage") || s.contains("coolie") || s.contains("கூலி") -> frag.currentLivelihood = "Daily wage labour"
            s.contains("no job") || s.contains("unemployed") || s.contains("jobless") || s.contains("வேலை இல்லை") -> frag.currentLivelihood = "Unemployed"
            s.contains("i am farmer") || s.contains("i'm farmer") || s.contains("i am a farmer") || s.contains("விவசாயி") -> frag.currentLivelihood = "Farmer"
            s.contains("housewife") || s.contains("homemaker") || s.contains("இல்லத்தரசி") -> frag.currentLivelihood = "Homemaker"
        }

        when {
            s.contains("cannot walk") || s.contains("can't walk") || s.contains("disability") || s.contains("physically") || s.contains("cannot do heavy") || s.contains("can't do heavy") || s.contains("health issue") || s.contains("medical") || s.contains("back pain") || s.contains("cannot lift") -> frag.physicalConstraints = originalSentence.take(120).trim()
        }

        when {
            s.contains("in my village") || s.contains("nearby") || s.contains("local market") || s.contains("in my area") || s.contains("demand") -> frag.localOpportunity = originalSentence.take(120).trim()
        }

        val interestKeywords = mapOf(
            "dairy" to listOf("dairy", "milk", "பால்", "दूध", "పాలు", "ಹಾಲು", "പാൽ", "milk business", "milk products", "dairy farm"),
            "cattle" to listOf("cattle", "cow", "livestock", "மாடு", "गाय", "buffalo", "cattle rearing", "கால்நடை"),
            "goat" to listOf("goat", "sheep", "ஆடு", "बकरी", "మేక", "ಆಡು", "ആട്", "goat farming"),
            "poultry" to listOf("poultry", "chicken", "egg", "கோழி", "मुर्गी", "కోడి", "ಕೋಳಿ", "കോഴി", "poultry farm"),
            "farming" to listOf("farming", "farm", "agriculture", "crop", "விவசாயம்", "खेती", "వ్యవసాయం", "ಕೃಷಿ", "കൃഷി", "organic farming", "vegetable"),
            "food" to listOf("food", "baking", "baker", "cooking", "உணவு", "food processing", "pickle", "millet", "cook", "खाना", "உணவு பதப்படுத்துதல்"),
            "machine" to listOf("machine", "operator", "technician", "mechanic", "இயந்திரம்", "electrical", "plumbing", "motor", "electrician", "welding", "fitter", "computer", "software", "it ", "cyber", "cyber security", "cybersecurity", "programming", "coding", "technology", "tech ", "hardware", "network", "engineering", "engineer", "computer science", "information technology", "artificial intelligence", "ai ", "data science", "robotics", "கணினி", "कंप्यूटर", "కంప్యూటర్", "ಕಂಪ್ಯೂಟರ್", "കമ്പ്യൂട്ടർ"),
            "textile" to listOf("textile", "handloom", "weaving", "loom", "நெசவு", "बुनाई", "నేత", "ನೇಯ್ಗೆ", "നെയ്ത്ത്", "handloom", "weave"),
            "construction" to listOf("construction", "mason", "building", "கட்டுமானம்", "निर्माण", "నిర్మాణం", "ನಿರ್ಮಾಣ", "നിർമ്മാണം", "carpenter", "bar bender", "masonry"),
            "tailor" to listOf("tailor", "stitching", "sewing", "தையல்", "सिलाई", "కుట్టు", "ಹೊಲಿಗೆ", "തയ്യൽ", "stitching", "embroidery", "tailoring", "fashion design")
        )

        for ((key, keywords) in interestKeywords) {
            for (kw in keywords) {
                if (s.contains(kw)) {
                    if (!frag.interests.contains(key)) frag.interests.add(key)
                    break
                }
            }
        }

        if (s.contains("i know") || s.contains("i can") || s.contains("skill") || s.contains("experience") || s.contains("specialisation") || s.contains("specialization")) {
            frag.skills.add(originalSentence.take(80).trim())
        }

        if (s.contains("computer science") || s.contains("cyber security") || s.contains("cybersecurity") || s.contains("programming") || s.contains("coding") || s.contains("கணினி")) {
            if (!frag.skills.contains("Computer Science")) frag.skills.add("Computer Science")
            if (s.contains("cyber")) {
                if (!frag.skills.contains("Cyber Security")) frag.skills.add("Cyber Security")
            }
        }

        return frag
    }

    fun validate(fragment: ProfileFragment, originalText: String): ProfileFragment {
        val allowedEdu = setOf("none", "class5", "below_8th", "class8", "class10", "class12", "graduate", "iti_diploma", "read_write")
        val allowedPref = setOf("pref_self", "pref_wage", "self_employment", "wage_employment", "self", "wage")
        val allowedMob = setOf("local", "district", "state", "within_village", "within_block", "within_district", "anywhere", "village", "nearby")
        val allowedInterests = setOf("dairy", "cattle", "goat", "poultry", "farming", "food", "machine", "textile", "construction", "tailor")

        if (fragment.edu != null && fragment.edu !in allowedEdu) {
            val eduLower = fragment.edu!!.lowercase()
            fragment.edu = when {
                eduLower.contains("5") || eduLower.contains("below") || eduLower.contains("none") -> "class5"
                eduLower.contains("8") -> "class8"
                eduLower.contains("10") -> "class10"
                eduLower.contains("12") -> "class12"
                eduLower.contains("grad") -> "graduate"
                else -> null
            }
        }
        if (fragment.preference != null && fragment.preference !in allowedPref) {
            val prefLower = fragment.preference!!.lowercase()
            fragment.preference = when {
                prefLower.contains("self") || prefLower.contains("own") || prefLower.contains("business") -> "pref_self"
                prefLower.contains("wage") || prefLower.contains("job") -> "pref_wage"
                else -> null
            }
        }
        if (fragment.mobility != null && fragment.mobility !in allowedMob) {
            val mobLower = fragment.mobility!!.lowercase()
            fragment.mobility = when {
                mobLower.contains("local") || mobLower.contains("village") -> "local"
                mobLower.contains("district") -> "district"
                mobLower.contains("state") || mobLower.contains("anywhere") -> "state"
                else -> null
            }
        }
        fragment.interests = fragment.interests.filter { it in allowedInterests }.toMutableList()
        return fragment
    }
}
