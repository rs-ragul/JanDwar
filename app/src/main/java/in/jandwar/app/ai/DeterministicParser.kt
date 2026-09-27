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
            val result = parse(text)
            callback.onResult(result)
        } catch (e: Exception) {
            callback.onError(e.message ?: "Parse failed")
        }
    }

    fun parse(originalSentence: String): ProfileFragment {
        val sentence = originalSentence.lowercase().trim()
        val s = sentence
        val frag = ProfileFragment()

        // Districts
        try {
            val districts = assetDataSource.loadDistricts()
            for (d in districts.all) {
                if (s.contains(d.lowercase())) {
                    frag.district = d
                    break
                }
            }
        } catch (_: Exception) {}

        // Education - expanded including engineering, computer science
        when {
            s.contains("phd") || s.contains("doctorate") || s.contains("m.tech") || s.contains("mtech") || s.contains("master") || s.contains("post graduate") || s.contains("postgraduate") -> frag.edu = "graduate"
            s.contains("b.tech") || s.contains("btech") || s.contains("b.e") || s.contains("be ") || s.contains("bachelor") || s.contains("engineering") || s.contains("computer science") || s.contains("bsc") || s.contains("b.sc") || s.contains("bcom") || s.contains("ba ") || s.contains("degree") || s.contains("college") || s.contains("graduate") || s.contains("graduation") || s.contains("பட்டதாரி") || s.contains("इंजीनियरिंग") -> frag.edu = "graduate"
            s.contains("12th") || s.contains("class 12") || s.contains("twelfth") || s.contains("hsc") || s.contains("12 வது") || s.contains("बारहवीं") || s.contains("intermediate") || s.contains("plus two") || s.contains("+2") -> frag.edu = "class12"
            s.contains("10th") || s.contains("class 10") || s.contains("tenth") || s.contains("sslc") || s.contains("10 வது") || s.contains("दसवीं") || s.contains("matric") -> frag.edu = "class10"
            s.contains("8th") || s.contains("class 8") || s.contains("eighth") || s.contains("8 வது") || s.contains("8th pass") -> frag.edu = "class8"
            s.contains("below") || s.contains("read") || s.contains("write") || s.contains("5th") || s.contains("class 5") || s.contains("illiterate") || s.contains("no school") || s.contains("படிக்கவில்லை") || s.contains("पढ़ा नहीं") -> frag.edu = "class5"
            s.contains("iti") || s.contains("diploma") || s.contains("ஐடிஐ") -> frag.edu = "class12"
        }

        // Preference - self vs wage
        when {
            s.contains("own") || s.contains("self") || s.contains("business") || s.contains("entrepreneur") || s.contains("my own") || s.contains("shop") || s.contains("enterprise") || s.contains("startup") || s.contains("சொந்த") || s.contains("स्वयं") || s.contains("खुद का") || s.contains("अपना बिजनेस") -> frag.preference = "pref_self"
            s.contains("job") || s.contains("wage") || s.contains("employer") || s.contains("salary") || s.contains("company") || s.contains("private") || s.contains("government job") || s.contains("placement") || s.contains("வேலை") || s.contains("नौकरी") || s.contains("जॉब") -> frag.preference = "pref_wage"
        }

        // Mobility
        when {
            s.contains("village") || s.contains("nearby") || s.contains("local") || s.contains("close") || s.contains("cannot travel") || s.contains("can't travel") || s.contains("கிராமம்") || s.contains("गांव") -> frag.mobility = "local"
            s.contains("district") || s.contains("மாவட்டம்") || s.contains("जिला") -> frag.mobility = "district"
            s.contains("anywhere") || s.contains("state") || s.contains("tamil nadu") || s.contains("far") || s.contains("any place") || s.contains("எங்கும்") || s.contains("कहीं भी") || s.contains("any location") -> frag.mobility = "state"
        }

        // Family occupation - improved
        val familyIndicators = listOf("family", "father", "mother", "parents", "traditional", "generations", "குடும்ப", "परिवार", "पारंपरिक", "my father", "my mother", "my family", "family is", "father is", "mother is")
        val hasFamilyContext = familyIndicators.any { s.contains(it) }

        // Government employee
        if (s.contains("government employee") || s.contains("govt employee") || s.contains("government job") && hasFamilyContext || s.contains("govt job") && hasFamilyContext || s.contains("sarkari naukri") || s.contains("அரசு வேலை")) {
            frag.familyOccupation = "Government employee"
        }
        // Farming
        else if ((s.contains("farm") || s.contains("agri") || s.contains("cultivation")) && (hasFamilyContext || s.contains("family occupation") || frag.familyOccupation == null && s.length < 60)) {
            if (s.contains("family") || hasFamilyContext || s.contains("father") || s.contains("mother")) {
                frag.familyOccupation = "Farming"
            }
        }
        // Cattle, dairy, etc
        else if (s.contains("cattle") && (hasFamilyContext || s.contains("family"))) frag.familyOccupation = "Cattle rearing"
        else if (s.contains("dairy") && hasFamilyContext) frag.familyOccupation = "Dairy farming"
        else if (s.contains("goat") && hasFamilyContext) frag.familyOccupation = "Goat rearing"
        else if (s.contains("tailor") && hasFamilyContext) frag.familyOccupation = "Tailoring"
        else if ((s.contains("weav") || s.contains("loom")) && hasFamilyContext) frag.familyOccupation = "Weaving"
        else if (s.contains("construct") && hasFamilyContext) frag.familyOccupation = "Construction labour"
        else if (s.contains("labour") && hasFamilyContext) frag.familyOccupation = "Daily wage labour"
        else if (s.contains("fisher") && hasFamilyContext) frag.familyOccupation = "Fishing"
        else if (s.contains("business") && hasFamilyContext) frag.familyOccupation = "Business"

        // If sentence is short and contains occupation and has family context, capture whole
        if (frag.familyOccupation == null && hasFamilyContext && originalSentence.length in 5..120 && !s.contains("?")) {
            val occupationKeywords = listOf("farm", "cattle", "dairy", "goat", "tailor", "weav", "labour", "construct", "government", "employee", "business", "shop", "teacher", "driver", "fishing")
            if (occupationKeywords.any { s.contains(it) }) {
                // Clean capture
                var cleaned = originalSentence.trim()
                if (cleaned.length > 80) cleaned = cleaned.take(80)
                frag.familyOccupation = cleaned
            }
        }

        // Current livelihood - improved for student detection
        when {
            s.contains("currently studying") || s.contains("i am studying") || s.contains("i'm studying") || s.contains("i am a student") || s.contains("i'm a student") || s.contains("i am just a student") || s.contains("currently a student") || s.contains("studying in") || s.contains("student") && (s.contains("currently") || s.contains("i am") || s.length < 50) -> {
                frag.currentLivelihood = "Student"
            }
            s.contains("computer science") && s.contains("study") -> frag.currentLivelihood = "Student - Computer Science"
            s.contains("engineering") && (s.contains("study") || s.contains("student")) -> frag.currentLivelihood = "Student - Engineering"
            (s.contains("daily wage") || s.contains("coolie") || s.contains("கூலி") || s.contains("दिहाड़ी")) -> frag.currentLivelihood = "Daily wage labour"
            s.contains("no job") || s.contains("unemployed") || s.contains("jobless") || s.contains("வேலை இல்லை") || s.contains("बेरोजगार") -> frag.currentLivelihood = "Unemployed"
            s.contains("i am farmer") || s.contains("i'm farmer") || s.contains("i am a farmer") -> frag.currentLivelihood = "Farmer"
            s.contains("housewife") || s.contains("homemaker") -> frag.currentLivelihood = "Homemaker"
            s.contains("currently") && s.contains("work") && originalSentence.length in 10..100 -> {
                if (frag.currentLivelihood == null) {
                    frag.currentLivelihood = originalSentence.take(100).trim()
                }
            }
        }

        // Physical constraints
        when {
            s.contains("cannot walk") || s.contains("can't walk") || s.contains("disability") || s.contains("physically") || s.contains("cannot do heavy") || s.contains("can't do heavy") || s.contains("உடல்") || s.contains("शारीरिक") || s.contains("health issue") || s.contains("medical") || s.contains("back pain") || s.contains("cannot lift") -> frag.physicalConstraints = originalSentence.take(120).trim()
        }

        // Local opportunity
        when {
            s.contains("in my village") || s.contains("nearby") || s.contains("local market") || s.contains("in my area") || s.contains("demand") || s.contains("என் ஊரில்") || s.contains("मेरे गांव में") -> frag.localOpportunity = originalSentence.take(120).trim()
        }

        // Interests - expanded with computer, IT, cyber security
        val interestKeywords = mapOf(
            "dairy" to listOf("dairy", "milk", "பால்", "दूध", "పాలు", "ಹಾಲು", "പാൽ", "milk business", "milk products", "dairy farm"),
            "cattle" to listOf("cattle", "cow", "livestock", "மாடு", "गाय", "buffalo", "cattle rearing"),
            "goat" to listOf("goat", "sheep", "ஆடு", "बकरी", "మేక", "ಆಡು", "ആട്", "goat farming"),
            "poultry" to listOf("poultry", "chicken", "egg", "கோழி", "मुर्गी", "కోడి", "ಕೋಳಿ", "കോഴി", "poultry farm"),
            "farming" to listOf("farming", "farm", "agriculture", "crop", "விவசாயம்", "खेती", "వ్యవసాయం", "ಕೃಷಿ", "കൃഷി", "organic farming", "vegetable"),
            "food" to listOf("food", "baking", "baker", "cooking", "உணவு", "food processing", "pickle", "millet", "cook", "खाना"),
            "machine" to listOf("machine", "operator", "technician", "mechanic", "இயந்திரம்", "electrical", "plumbing", "motor", "electrician", "welding", "fitter", "computer", "software", "it ", "cyber", "cyber security", "cybersecurity", "programming", "coding", "technology", "tech ", "hardware", "network", "engineering", "engineer", "computer science", "information technology", "artificial intelligence", "ai ", "data science", "robotics"),
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

        // Skills - extract computer skills
        if (s.contains("i know") || s.contains("i can") || s.contains("skill") || s.contains("experience") || s.contains("specialisation") || s.contains("specialization") || s.contains("தெரியும்") || s.contains("जानता")) {
            frag.skills.add(originalSentence.take(80).trim())
        }

        // Specific skills for CSE
        if (s.contains("computer science") || s.contains("cyber security") || s.contains("cybersecurity") || s.contains("programming") || s.contains("coding")) {
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
