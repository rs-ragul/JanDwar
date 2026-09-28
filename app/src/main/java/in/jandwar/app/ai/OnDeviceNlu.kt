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
        // Strict here: this utterance may be answering something else
        // entirely, and a loose read of "I work in the district office"
        // used to silently overwrite a travel answer the user had already
        // given. The slot-directed path below applies the full detector.
        frag.preference = detectPreferenceStrict(s)
        frag.mobility = detectMobilityStrict(s)
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
                detectEducationLoose(s)?.let { frag.edu = it }

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

            // The user is answering this exact question, so whatever we read
            // here outranks anything inferred from an earlier sentence.
            ProfileFragment.Slot.PREFERENCE ->
                detectPreferenceLoose(s)?.let { frag.preference = it }

            ProfileFragment.Slot.MOBILITY ->
                detectMobilityLoose(s)?.let { frag.mobility = it }

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

    // ── Vocabulary ──────────────────────────────────────────────────────────
    //
    // Every surface form now lives in assets/lexicon.json and is matched
    // substring-first, then fuzzily per token. Regional slang is added by
    // editing that file — no code change.

    private val lex: Lexicon by lazy { Lexicon(assetDataSource.loadLexicon()) }

    private fun has(s: String, key: String) = lex.has(s, key)
    private fun hasExact(s: String, key: String) = lex.hasExact(s, key)

    // ── Education ───────────────────────────────────────────────────────────

    /**
     * Ordered most-specific first: "ITI diploma" must not be read as
     * "class 10" just because the sentence also mentions school.
     */
    private val EDU_ORDER = listOf(
        "edu.none" to "class5",
        "edu.iti_diploma" to "iti_diploma",
        "edu.graduate" to "graduate",
        "edu.class12" to "class12",
        "edu.class10" to "class10",
        "edu.class8" to "class8",
        "edu.class5" to "class5"
    )

    private fun detectEducation(s: String): String? =
        EDU_ORDER.firstOrNull { (key, _) -> has(s, key) }?.second

    private fun detectEducationLoose(s: String): String? {
        detectEducation(s)?.let { return it }
        // Bare numbers in answer to "which class did you finish?".
        Regex("\\b(\\d{1,2})\\b").find(s)?.groupValues?.get(1)?.toIntOrNull()?.let { n ->
            return when {
                n >= 13 -> "graduate"
                n == 12 || n == 11 -> "class12"
                n == 10 || n == 9 -> "class10"
                n in 6..8 -> "class8"
                n in 1..5 -> "class5"
                else -> null
            }
        }
        if (has(s, "marker.no")) return "class5"
        return null
    }

    // ── Preference ──────────────────────────────────────────────────────────

    private fun detectPreference(s: String): String? {
        // Strong phrases win, and self is tested first so a sentence carrying
        // both ("I don't want a job, I want to work for myself") resolves the
        // way the speaker meant it.
        if (has(s, "pref.self_strong")) return "pref_self"
        if (has(s, "pref.wage_strong")) return "pref_wage"
        if (has(s, "pref.self_weak")) return "pref_self"
        if (has(s, "pref.wage_weak")) return "pref_wage"
        return null
    }

    /** Only unmistakable evidence, for utterances answering a different question. */
    private fun detectPreferenceStrict(s: String): String? = when {
        hasExact(s, "pref.self_strong") -> "pref_self"
        hasExact(s, "pref.wage_strong") -> "pref_wage"
        else -> null
    }

    private fun detectPreferenceLoose(s: String): String? {
        detectPreference(s)?.let { return it }
        if (s.hasAny("first", "one", "1", "முதல்", "पहला")) return "pref_self"
        if (s.hasAny("second", "two", "2", "இரண்டு", "दूसरा")) return "pref_wage"
        return null
    }

    // ── Mobility ────────────────────────────────────────────────────────────

    /**
     * Precedence matters more here than anywhere else. Both of the realistic
     * answers contain the word "anywhere" — "anywhere in my district" and
     * "anywhere in the state" — so an explicit district or state word must be
     * consulted *before* the generic one. The bare "anywhere" then means the
     * widest option, which is what a speaker who volunteers it intends.
     */
    private fun detectMobility(s: String): String? = when {
        has(s, "mob.state") -> "state"
        has(s, "mob.district") -> "district"
        has(s, "mob.local") -> "local"
        has(s, "mob.anywhere") -> "state"
        else -> null
    }

    /** Only unmistakable evidence, for utterances answering a different question. */
    private fun detectMobilityStrict(s: String): String? = when {
        hasExact(s, "mob.state") -> "state"
        hasExact(s, "mob.district") -> "district"
        hasExact(s, "mob.local") -> "local"
        else -> null
    }

    private fun detectMobilityLoose(s: String): String? {
        detectMobility(s)?.let { return it }
        if (has(s, "marker.yes")) return "district"
        if (has(s, "marker.no")) return "local"
        return null
    }

    // ── Interests ───────────────────────────────────────────────────────────

    private val INTEREST_KEYS = listOf(
        "dairy", "cattle", "goat", "poultry", "farming",
        "food", "machine", "textile", "construction", "tailor"
    )

    private fun detectInterests(s: String): List<String> =
        INTEREST_KEYS.filter { has(s, "interest.$it") }

    // ── Occupation ──────────────────────────────────────────────────────────

    private fun mentionsFamily(s: String): Boolean = has(s, "marker.family")

    /**
     * Canonical livelihood label. Occupation-only trades are checked before
     * the interest vocabulary so "government office" is not swallowed by a
     * broader category.
     */
    private val OCCUPATION_ORDER: List<Pair<String, String>> = listOf(
        "occ.government" to "Government service",
        "occ.driver" to "Driving",
        "occ.fishing" to "Fishing",
        "occ.coolie" to "Daily wage labour",
        "occ.shop" to "Small shop",
        "interest.dairy" to "Dairy farming",
        "interest.cattle" to "Cattle rearing",
        "interest.goat" to "Goat rearing",
        "interest.poultry" to "Poultry",
        "interest.tailor" to "Tailoring",
        "interest.textile" to "Weaving",
        "interest.construction" to "Construction work",
        "interest.food" to "Cooking / catering",
        "interest.machine" to "Machine work / repair",
        "interest.farming" to "Farming"
    )

    private fun detectOccupation(s: String): String? =
        OCCUPATION_ORDER.firstOrNull { (key, _) -> has(s, key) }?.second

    private fun detectStudentStatus(s: String, original: String): String? = when {
        has(s, "marker.student") -> "Student"
        has(s, "marker.unemployed") -> "Looking for work"
        else -> null
    }


    // ── Physical constraints ────────────────────────────────────────────────

    private fun detectConstraints(s: String, original: String): String? =
        if (has(s, "marker.constraint")) original.trim().take(120) else null


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
