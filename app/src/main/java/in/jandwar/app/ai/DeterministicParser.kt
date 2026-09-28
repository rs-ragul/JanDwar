package `in`.jandwar.app.ai

import `in`.jandwar.app.data.local.AssetDataSource
import javax.inject.Inject
import javax.inject.Singleton
import java.util.regex.Pattern

@Singleton
class DeterministicParser @Inject constructor(private val assetDataSource: AssetDataSource) : NluExtractor {
    override fun extract(text: String, langCode: String, currentProfile: ProfileFragment?, isOnline: Boolean, callback: NluExtractor.Callback) {
        try { val result = parse(text, langCode); callback.onResult(result) } catch (e: Exception) { callback.onError(e.message ?: "Parse failed") }
    }
    fun parse(originalSentence: String, langCode: String = "en"): ProfileFragment {
        val sentence = originalSentence.lowercase().trim()
        val s = sentence
        val frag = ProfileFragment()
        try { val districts = assetDataSource.loadDistricts(); for (d in districts.all) { if (s.contains(d.lowercase())) { frag.district = d; break } } } catch (_: Exception) {}
        val studyingPattern = Pattern.compile("(?:studying|pursuing|doing|completed|finished|passed|student of|student in|am in|in )\\s+([a-z. ]{2,40})", Pattern.CASE_INSENSITIVE)
        val matcher = studyingPattern.matcher(originalSentence)
        var extractedCourse: String? = null
        if (matcher.find()) {
            val courseRaw = matcher.group(1)?.trim()
            if (!courseRaw.isNullOrBlank() && courseRaw.length in 2..40) {
                val lowerCourse = courseRaw.lowercase()
                if (!listOf("school", "college", "class", "the", "my", "currently", "now", "just", "still").contains(lowerCourse)) {
                    extractedCourse = courseRaw
                    if (!s.contains("12th") && !s.contains("10th") && !s.contains("8th") && !s.contains("class 12") && !s.contains("class 10") && !s.contains("class 8")) { frag.edu = "graduate" }
                }
            }
        }
        val abbrPattern = Pattern.compile("\\b([A-Z]{2,5})\\b")
        val abbrMatcher = abbrPattern.matcher(originalSentence)
        var abbrCourse: String? = null
        while (abbrMatcher.find()) { val abbr = abbrMatcher.group(1); if (abbr.length in 2..5 && abbr != "AM" && abbr != "I" && abbr != "IN" && abbr != "OF" && abbr != "MY") { abbrCourse = abbr; break } }
        if (s.contains("studying") || s.contains("pursuing") || s.contains("doing") || s.contains("student") || abbrCourse != null) {
            when {
                s.contains("12th") || s.contains("class 12") || s.contains("twelfth") || s.contains("hsc") || s.contains("+2") || s.contains("plus two") -> frag.edu = "class12"
                s.contains("10th") || s.contains("class 10") || s.contains("tenth") || s.contains("sslc") -> frag.edu = "class10"
                s.contains("8th") || s.contains("class 8") || s.contains("eighth") -> frag.edu = "class8"
                s.contains("below") || s.contains("read") || s.contains("write") || s.contains("5th") || s.contains("illiterate") -> frag.edu = "class5"
                s.contains("iti") || s.contains("diploma") -> frag.edu = "class12"
                else -> { if (frag.edu == null) frag.edu = "graduate" }
            }
        }
        if (frag.edu == null) {
            val gradKeywords = listOf("b.tech", "btech", "b.e", "bachelor", "master", "m.tech", "mtech", "engineering", "degree", "college", "graduate", "phd", "doctorate", "bsc", "b.sc", "bcom", "b.com", "ba ", "ma ", "mba", "bba", "bca", "mca", "b.sc", "m.sc", "nursing", "pharmacy", "diploma", "polytechnic", "iti")
            if (gradKeywords.any { s.contains(it) }) { frag.edu = "graduate" }
        }
        when {
            s.contains("own") || s.contains("self") || s.contains("business") || s.contains("entrepreneur") || s.contains("my own") || s.contains("shop") || s.contains("enterprise") || s.contains("startup") -> frag.preference = "pref_self"
            s.contains("job") || s.contains("wage") || s.contains("employer") || s.contains("salary") || s.contains("company") || s.contains("private") || s.contains("placement") -> frag.preference = "pref_wage"
        }
        when {
            s.contains("village") || s.contains("nearby") || s.contains("local") || s.contains("close") || s.contains("cannot travel") || s.contains("can't travel") -> frag.mobility = "local"
            s.contains("district") -> frag.mobility = "district"
            s.contains("anywhere") || s.contains("state") || s.contains("tamil nadu") || s.contains("far") || s.contains("any place") -> frag.mobility = "state"
        }
        val familyIndicators = listOf("family", "father", "mother", "parents", "traditional", "my father", "my mother", "my family", "family is", "father is", "mother is")
        val hasFamilyContext = familyIndicators.any { s.contains(it) }
        if (hasFamilyContext) {
            when {
                s.contains("government") || s.contains("govt") -> frag.familyOccupation = "Government employee"
                s.contains("farm") || s.contains("agri") -> frag.familyOccupation = "Farming"
                s.contains("cattle") -> frag.familyOccupation = "Cattle rearing"
                s.contains("dairy") -> frag.familyOccupation = "Dairy farming"
                s.contains("goat") -> frag.familyOccupation = "Goat rearing"
                s.contains("tailor") -> frag.familyOccupation = "Tailoring"
                s.contains("weav") || s.contains("loom") -> frag.familyOccupation = "Weaving"
                s.contains("construct") -> frag.familyOccupation = "Construction labour"
                s.contains("labour") -> frag.familyOccupation = "Daily wage labour"
                s.contains("business") -> frag.familyOccupation = "Business"
            }
            if (frag.familyOccupation == null && originalSentence.length in 5..100 && !s.contains("?")) { frag.familyOccupation = originalSentence.trim().take(80) }
        }
        if (extractedCourse != null) { frag.currentLivelihood = "Student - ${extractedCourse.replaceFirstChar { it.uppercase() }}" }
        else if (abbrCourse != null && (s.contains("studying") || s.contains("student") || s.contains("pursuing") || s.contains("doing"))) { frag.currentLivelihood = "Student - $abbrCourse" }
        else {
            when {
                s.contains("currently studying") || s.contains("i am studying") || s.contains("i'm studying") || s.contains("i am a student") || s.contains("i'm a student") || s.contains("currently a student") -> frag.currentLivelihood = "Student"
                s.contains("daily wage") || s.contains("coolie") -> frag.currentLivelihood = "Daily wage labour"
                s.contains("no job") || s.contains("unemployed") || s.contains("jobless") -> frag.currentLivelihood = "Unemployed"
                s.contains("i am farmer") || s.contains("i'm farmer") || s.contains("i am a farmer") -> frag.currentLivelihood = "Farmer"
            }
        }
        val techCourses = listOf("computer", "software", "it ", "cyber", "programming", "coding", "technology", "tech ", "hardware", "network", "engineering", "engineer", "information technology", "artificial intelligence", "ai ", "data science", "robotics", "electronics", "communication", "circuit", "vlsi", "embedded", "mechanical", "civil", "electrical", "bca", "mca", "bba", "mba", "bcom", "bsc", "msc", "b.tech", "m.tech", "be ", "b.e")
        when {
            techCourses.any { s.contains(it) } || abbrCourse != null -> frag.interests.add("machine")
            listOf("agriculture", "farming", "dairy", "cattle", "goat", "poultry", "veterinary", "horticulture").any { s.contains(it) } -> frag.interests.add("farming")
            listOf("fashion", "textile", "tailoring", "design", "weaving", "apparel").any { s.contains(it) } -> { frag.interests.add("tailor"); frag.interests.add("textile") }
            listOf("food", "cooking", "hotel", "catering", "baking", "culinary").any { s.contains(it) } -> frag.interests.add("food")
            listOf("construction", "civil", "mason", "carpenter", "plumbing", "electrician", "welding").any { s.contains(it) } -> frag.interests.add("construction")
        }
        if (extractedCourse != null) { frag.skills.add(extractedCourse.replaceFirstChar { it.uppercase() }) }
        if (abbrCourse != null) {
            frag.skills.add(abbrCourse)
            when (abbrCourse.lowercase()) {
                "ece" -> { frag.skills.add("Electronics"); frag.skills.add("Electronics and Communication") }
                "cse" -> frag.skills.add("Computer Science")
                "eee" -> frag.skills.add("Electrical and Electronics")
                "mech" -> frag.skills.add("Mechanical Engineering")
                "civil" -> frag.skills.add("Civil Engineering")
                "it" -> frag.skills.add("Information Technology")
                "ai" -> frag.skills.add("Artificial Intelligence")
                "mba" -> frag.skills.add("Business Administration")
                "bca" -> frag.skills.add("Computer Applications")
                "mca" -> frag.skills.add("Computer Applications")
                "bba" -> frag.skills.add("Business Administration")
                "bcom" -> frag.skills.add("Commerce")
            }
        }
        if (frag.currentLivelihood == null && s.contains("studying") && originalSentence.length < 50) {
            val parts = originalSentence.split("studying")
            if (parts.size > 1) { val coursePart = parts[1].trim().take(30); if (coursePart.isNotBlank()) { frag.currentLivelihood = "Student - $coursePart"; frag.skills.add(coursePart); if (frag.edu == null) frag.edu = "graduate"; if (frag.interests.isEmpty()) frag.interests.add("machine") } }
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
                else -> "graduate"
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
