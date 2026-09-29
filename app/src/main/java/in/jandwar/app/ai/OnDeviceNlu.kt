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

        frag.state = detectState(s, text)
        frag.district = detectDistrict(s, text, frag.state)
        // A recognised district settles the state even when the person never
        // named it — "I'm in Ernakulam" is unambiguous.
        if (frag.state == null) frag.district?.let { frag.state = stateOfDistrict(it) }
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
            ProfileFragment.Slot.EDUCATION -> {
                detectEducationLoose(s)?.let {
                    // Record what was actually *completed*. "college 2nd
                    // year" mentions a degree but has finished Class 12, and
                    // reading it as `graduate` put the person at the top
                    // education rank and offered them courses they cannot
                    // yet enrol in. "12th dropout" completed Class 10.
                    frag.edu = if (isInProgress(s) || isDropout(s))
                        IN_PROGRESS_DOWNGRADE[it] ?: it else it
                }
                // Still enrolled is also a fact about their livelihood, and
                // it answers a question we would otherwise ask again.
                if (isInProgress(s) && !isDropout(s) && frag.currentLivelihood == null) {
                    frag.currentLivelihood = "Student"
                }
            }

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

            // "No" is a real answer here, not a failure to understand. Without
            // this the slot would stay empty and the interview would ask again.
            ProfileFragment.Slot.CONSTRAINTS ->
                frag.physicalConstraints = when {
                    hasExact(s, "marker.constraint") -> text.take(120)
                    has(s, "marker.no") -> NO_CONSTRAINT
                    has(s, "marker.yes") -> text.take(120)
                    // Anything else volunteered is kept verbatim; a blank or
                    // unintelligible reply leaves the slot open to re-ask.
                    text.length >= 2 -> text.take(120)
                    else -> null
                }

            // A wrong guess here sends someone to a centre in another state,
            // so the generic pass stays strict; only when this *is* the
            // question do "TN", "AP", "UP" count as answers.
            ProfileFragment.Slot.STATE ->
                if (!frag.hasState()) frag.state = extractStateAnswer(text)

            ProfileFragment.Slot.DISTRICT -> Unit
        }
        return frag
    }

    /**
     * District answer interpreted inside a state the user already named.
     *
     * Scoping matters twice over: it removes cross-state false hits, and it
     * lets a short or misheard reply be resolved against 14–75 candidates
     * instead of 187.
     */
    fun extractDistrictIn(raw: String, state: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return detectDistrict(normalise(text), text, state)
    }

    /** All districts of a state, for the caller to offer as choices. */
    fun districtsOfState(state: String): List<String> =
        try {
            assetDataSource.loadDistricts().districtsOf(state)
        } catch (e: Exception) {
            emptyList()
        }

    companion object {
        /** Sentinel stored when the user says they have no difficulty. */
        const val NO_CONSTRAINT = "None"
    }

    // ── Normalisation ───────────────────────────────────────────────────────

    /**
     * Delegates to [Lexicon.fold] so the utterance and the lexicon forms are
     * normalised by one function. Two near-identical copies is how every
     * punctuated form became dead data last time.
     */
    private fun normalise(t: String): String = Lexicon.fold(t)

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

    private val EDU_KEYS = EDU_ORDER.map { it.first }

    /**
     * Longest matched surface form wins, not list order.
     *
     * "pre university" contains "university", so an ordered scan reported
     * `graduate` for a Class 12 answer. Ties fall back to [EDU_ORDER].
     */
    /**
     * What a person has *completed* when they are still mid-course. Someone
     * in their second year of a degree has finished Class 12, not the degree.
     */
    private val IN_PROGRESS_DOWNGRADE = mapOf(
        "graduate" to "class12",
        "iti_diploma" to "class10",
        "class12" to "class10",
        "class10" to "class8",
        "class8" to "class5",
        "class5" to "class5"
    )

    /** Still enrolled - "college 2nd year", "studying", "final year". */
    private fun isInProgress(s: String): Boolean = has(s, "marker.in_progress")

    /** Left before finishing - completed only the level below, not a student. */
    private fun isDropout(s: String): Boolean = has(s, "marker.dropout")

    private fun detectEducation(s: String): String? {
        val key = lex.bestMatch(s, EDU_KEYS) ?: return null
        return EDU_ORDER.firstOrNull { it.first == key }?.second
    }

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
    private val MOB_ORDER = listOf(
        "mob.state" to "state",
        "mob.district" to "district",
        "mob.local" to "local",
        "mob.anywhere" to "state"
    )

    /**
     * Longest matched form wins, exactly as for education.
     *
     * Precedence cannot express specificity, and here it was inverting the
     * answer. "வெளியூர் போக முடியாது" -- *cannot* go out of town --
     * contains the bare word "வெளியூர்" (out-of-town), which is a
     * `mob.state` form, so a state-first scan recorded someone who cannot
     * leave their village as willing to travel anywhere in the state, and the
     * recommender then offered them a course in another district. Hindi
     * "बाहर नहीं जा सकता" (contains "बाहर") failed the same way.
     *
     * The negated phrase is always the longer match, so longest-wins reads
     * the negation correctly without a separate negation parser.
     */
    private fun detectMobility(s: String): String? {
        val cat = lex.bestMatch(s, MOB_ORDER.map { it.first }) ?: return null
        return MOB_ORDER.firstOrNull { it.first == cat }?.second
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

    private fun detectDistrict(s: String, original: String, state: String? = null): String? {
        val lowerOriginal = original.lowercase()
        val allowed: Set<String>? = state
            ?.takeIf { it.isNotBlank() }
            ?.let { districtsOfState(it).toSet().takeIf { set -> set.isNotEmpty() } }
        // Longest form first so "Tiruvannamalai" wins over "Tiruvallur"-like prefixes.
        return districtIndex
            .filter { allowed == null || allowed.contains(it.first) }
            .flatMap { (name, forms) -> forms.map { name to it } }
            .sortedByDescending { it.second.length }
            .firstOrNull { (_, form) -> s.contains(form) || lowerOriginal.contains(form) }
            ?.first
    }

    // ── State ───────────────────────────────────────────────────────────────

    private val stateOfDistrictIndex: Map<String, String> by lazy {
        val out = HashMap<String, String>()
        try {
            assetDataSource.loadDistricts().by_state.forEach { (st, d) ->
                d.all.forEach { out[it.lowercase()] = st }
            }
        } catch (e: Exception) {
            // leave empty; state simply stays unknown and gets asked
        }
        out
    }

    fun stateOfDistrict(district: String): String? =
        stateOfDistrictIndex[district.trim().lowercase()]

    /**
     * English state name -> spoken forms across the six languages.
     *
     * Deliberately hand-written rather than generated: these are the five
     * states the catalogue covers, people say them in many ways ("TN",
     * "AP", "Andhra", "UP", "Kerala"), and a wrong reading here silently
     * routes someone to centres 2000 km away.
     */
    private val STATE_FORMS: Map<String, List<String>> = mapOf(
        "Tamil Nadu" to listOf(
            "tamil nadu", "tamilnadu", "tamil naadu", "tn", "tamil",
            "தமிழ்நாடு", "தமிழ் நாடு", "तमिलनाडु", "तमिल नाडु",
            "తమిళనాడు", "ತಮಿಳುನಾಡು", "തമിഴ്നാട്", "തമിഴ്‌നാട്"
        ),
        "Kerala" to listOf(
            "kerala", "keralam", "kerela",
            "கேரளா", "கேரளம்", "केरल", "केरला",
            "కేరళ", "ಕೇರಳ", "കേരളം", "കേരള"
        ),
        "Karnataka" to listOf(
            "karnataka", "karnatak", "karnataka state",
            "கர்நாடகா", "கர்நாடகம்", "कर्नाटक", "कर्नाटका",
            "కర్ణాటక", "ಕರ್ನಾಟಕ", "കർണാടക", "കർണ്ണാടക"
        ),
        "Andhra Pradesh" to listOf(
            "andhra pradesh", "andhrapradesh", "andhra", "ap", "andra",
            "ஆந்திரப் பிரதேசம்", "ஆந்திரா", "आंध्र प्रदेश", "आंध्रप्रदेश", "आंध्रा",
            "ఆంధ్రప్రదేశ్", "ఆంధ్ర", "ಆಂಧ್ರಪ್ರದೇಶ", "ಆಂಧ್ರ", "ആന്ധ്രാപ്രദേശ്", "ആന്ധ്ര"
        ),
        "Uttar Pradesh" to listOf(
            "uttar pradesh", "uttarpradesh", "up", "u p", "uttra pradesh", "utter pradesh",
            "உத்தரப் பிரதேசம்", "உத்திரப் பிரதேசம்", "उत्तर प्रदेश", "उत्तरप्रदेश", "यूपी",
            "ఉత్తరప్రదేశ్", "ಉತ್ತರ ಪ್ರದೇಶ", "ഉത്തർപ്രദേശ്", "ഉത്തര്‍പ്രദേശ്"
        )
    )

    /**
     * Ambiguous forms, only honoured when the person was *just asked* which
     * state they live in.
     *
     * "up" is the reason this set exists: "I studied up to 10th" would
     * otherwise put a Tamil speaker in Uttar Pradesh. "tamil" is a language
     * as often as a place, and "ap" collides with ordinary speech. Inside the
     * state question they are exactly what people say, so they are matched
     * there — as whole tokens, never as substrings.
     */
    private val AMBIGUOUS_STATE_FORMS = setOf("tn", "ap", "up", "u p", "tamil")

    /**
     * @param allowAmbiguous true only when answering the state question.
     */
    private fun detectState(s: String, original: String, allowAmbiguous: Boolean = false): String? {
        val lowerOriginal = original.lowercase()
        val tokens = (s + " " + lowerOriginal)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
            .toSet()
        return STATE_FORMS.entries
            .flatMap { (name, forms) -> forms.map { name to it } }
            .sortedByDescending { it.second.length }
            .firstOrNull { (_, form) ->
                if (form in AMBIGUOUS_STATE_FORMS) {
                    allowAmbiguous && tokens.contains(form.replace(" ", ""))
                } else {
                    s.contains(form) || lowerOriginal.contains(form)
                }
            }
            ?.first
    }

    /** State answer read in reply to the state question. */
    fun extractStateAnswer(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return detectState(normalise(text), text, allowAmbiguous = true)
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
