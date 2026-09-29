package `in`.jandwar.app.data.repository

import `in`.jandwar.app.data.local.AssetDataSource
import `in`.jandwar.app.data.model.Centre
import `in`.jandwar.app.data.model.DistrictEconomy
import `in`.jandwar.app.data.model.DistrictsData
import `in`.jandwar.app.data.model.EducationLevel
import `in`.jandwar.app.data.model.I18nData
import `in`.jandwar.app.data.model.InterestChip
import `in`.jandwar.app.data.model.JobRole
import `in`.jandwar.app.data.model.MatchFactor
import `in`.jandwar.app.data.model.MatchedRole
import `in`.jandwar.app.data.model.Mobility
import `in`.jandwar.app.data.model.Preference
import `in`.jandwar.app.data.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Single source of truth for the offline catalogue, translations and the
 * NSQF recommendation engine.
 */
@Singleton
class AppRepository @Inject constructor(
    private val assetDataSource: AssetDataSource
) {

    // ── Lazily cached catalogue ─────────────────────────────────────────────
    private val roles: List<JobRole> by lazy {
        assetDataSource.loadJobRoles().filter { it.isValidName() }
    }
    private val centreList: List<Centre> by lazy { assetDataSource.loadCentres() }
    private val districtData: DistrictsData by lazy { assetDataSource.loadDistricts() }
    private val districtEconomy: Map<String, DistrictEconomy> by lazy {
        assetDataSource.loadDistrictEconomy()
    }
    private val i18nData: I18nData by lazy { assetDataSource.loadI18n() }

    fun getJobRoles(): List<JobRole> = roles
    fun getCentres(): List<Centre> = centreList
    fun getDistricts(): DistrictsData = districtData
    fun getI18n(): I18nData = i18nData

    fun availableLanguages(): List<Pair<String, String>> =
        i18nData.langs.ifEmpty {
            listOf(
                "en" to "English", "ta" to "தமிழ்", "hi" to "हिन्दी",
                "te" to "తెలుగు", "kn" to "ಕನ್ನಡ", "ml" to "മലയാളം"
            )
        }

    /**
     * Nearest centre for a district, preferring a CONFIRMED record.
     *
     * [state] disambiguates: district names are not unique across India and
     * the catalogue now spans five states. When it is known the search is
     * scoped to it, so a Tiruvallur answer can never surface a centre in
     * Uttar Pradesh.
     */
    fun getCentreForDistrict(district: String, state: String = ""): Centre? {
        if (district.isBlank()) return null
        val inDistrict = centreList.filter {
            it.district.equals(district, ignoreCase = true) &&
                    (state.isBlank() || it.state.equals(state, ignoreCase = true))
        }
        if (inDistrict.isEmpty()) return null
        return inDistrict.firstOrNull { it.isConfirmed() } ?: inDistrict.first()
    }

    /** Every centre in a district, best-evidence first. */
    fun centresForDistrict(district: String, state: String = ""): List<Centre> {
        if (district.isBlank()) return emptyList()
        return centreList
            .filter {
                it.district.equals(district, ignoreCase = true) &&
                        (state.isBlank() || it.state.equals(state, ignoreCase = true))
            }
            .sortedByDescending { if (it.isConfirmed()) 1 else 0 }
    }

    // ── States ──────────────────────────────────────────────────────────────

    /** The states the offline catalogue actually covers. */
    fun states(): List<String> = districtData.states().ifEmpty {
        centreList.map { it.state }.filter { it.isNotBlank() }.distinct().sorted()
    }

    fun districtsForState(state: String): List<String> {
        if (state.isBlank()) return districtData.all
        return districtData.districtsOf(state).ifEmpty {
            centreList.filter { it.state.equals(state, ignoreCase = true) }
                .map { it.district }.distinct().sorted()
        }
    }

    fun districtsWithCentreIn(state: String): Set<String> =
        if (state.isBlank()) districtData.with_centre.toSet()
        else districtData.withCentreIn(state)

    /** Which state a district sits in; null when the name is unknown. */
    fun stateForDistrict(district: String): String? =
        districtData.stateOf(district)
            ?: centreList.firstOrNull { it.district.equals(district, ignoreCase = true) }?.state

    fun centreCountForState(state: String): Int =
        centreList.count { it.state.equals(state, ignoreCase = true) }

    fun totalCentreCount(): Int = centreList.size

    fun confirmedCentreCount(): Int = centreList.count { it.isConfirmed() }

    fun fundableCount(): Int = roles.count { it.isFundable() }

    fun sectors(): List<String> = roles.map { it.sector }.filter { it.isNotBlank() }.distinct().sorted()

    // ── Translations ────────────────────────────────────────────────────────

    /** Look up [key] in [lang], falling back to English, then to the key itself. */
    fun tr(lang: String, key: String): String {
        i18nData.strings[lang]?.get(key)?.takeIf { it.isNotBlank() }?.let { return it }
        i18nData.strings["en"]?.get(key)?.takeIf { it.isNotBlank() }?.let { return it }
        return FALLBACK_EN[key] ?: key
    }

    fun interestLabel(lang: String, key: String): String {
        i18nData.interests[lang]?.get(key)?.takeIf { it.isNotBlank() }?.let { return it }
        i18nData.interests["en"]?.get(key)?.takeIf { it.isNotBlank() }?.let { return it }
        return key.replaceFirstChar { it.uppercase() }
    }

    /**
     * Display form of a canonical occupation label. Free text the user typed is
     * returned unchanged, so this is safe to call on any occupation string.
     */
    fun occupationLabel(lang: String, raw: String): String {
        val key = raw.trim().lowercase()
        if (key.isEmpty()) return raw
        i18nData.occupations[lang]?.get(key)?.takeIf { it.isNotBlank() }?.let { return it }
        i18nData.occupations["en"]?.get(key)?.takeIf { it.isNotBlank() }?.let { return it }
        return raw
    }

    fun getInterestChips(lang: String): List<InterestChip> =
        INTEREST_KEYS.map { InterestChip(it, interestLabel(lang, it)) }

    /** Human label for a raw sector id, e.g. `food_processing` → `Food processing`. */
    fun sectorLabel(sector: String): String =
        sector.split('_').joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
        }

    // ── Recommendation engine ───────────────────────────────────────────────

    /**
     * Scores every valid NSQF pack against [profile] and returns the best
     * [limit] matches, each carrying a localized, human-readable explanation.
     *
     * Design notes:
     *  - Education is a *soft* gate: a pack far above the beneficiary's
     *    schooling is penalised and flagged, not silently dropped, so the
     *    assistant can suggest a bridge course instead of showing nothing.
     *  - Every points award has a matching [MatchFactor] so the UI can show
     *    exactly why a course surfaced (the PS asks for explainability).
     */
    fun matchRoles(
        profile: UserProfile,
        lang: String = "en",
        limit: Int = 6
    ): List<MatchedRole> {
        if (roles.isEmpty()) return emptyList()

        val eduRank = profile.education?.rank ?: EducationLevel.CLASS_8.rank
        val centre = getCentreForDistrict(profile.district, profile.state)
        val results = ArrayList<MatchedRole>(roles.size)

        for (role in roles) {
            var score = 0
            val factors = mutableListOf<MatchFactor>()
            var eligible = true

            // ── Education gate (soft) ───────────────────────────────────────
            val required = role.requiredEduRank()
            if (required <= eduRank) {
                score += 30
            } else {
                val gap = required - eduRank
                score -= gap * 22
                eligible = false
                // A graduate-level pack for someone below Class 10 is not a
                // realistic suggestion at all — drop it entirely.
                if (required >= EducationLevel.GRADUATE.rank &&
                    eduRank < EducationLevel.CLASS_10.rank
                ) continue
            }

            // Long programmes are hard to complete with very little schooling.
            if (role.isLongTerm() && eduRank < EducationLevel.CLASS_10.rank) score -= 14

            // ── Stated interests ────────────────────────────────────────────
            val matchedInterests = profile.interests.filter { role.matchesInterest(it) }
            if (matchedInterests.isNotEmpty()) {
                // First hit is worth the most; extra hits add diminishing value.
                score += 85 + (matchedInterests.size - 1) * 18
                factors += MatchFactor(
                    "${tr(lang, "reason_interest")} " +
                            matchedInterests.joinToString(", ") { interestLabel(lang, it) },
                    true
                )
            }

            // ── Family / traditional occupation ─────────────────────────────
            var familyNote = ""
            val familyFit = profile.familyOccupation.isNotBlank() &&
                    role.matchesOccupationText(profile.familyOccupation)
            if (familyFit) {
                score += 55
                familyNote = "${tr(lang, "reason_family")} ${occupationLabel(lang, profile.familyOccupation)}"
                factors += MatchFactor(familyNote, true)
            }

            // ── Current livelihood ──────────────────────────────────────────
            if (profile.currentLivelihood.isNotBlank() &&
                role.matchesOccupationText(profile.currentLivelihood)
            ) {
                score += 32
                if (familyNote.isBlank()) {
                    familyNote = "${tr(lang, "reason_family")} ${occupationLabel(lang, profile.currentLivelihood)}"
                    factors += MatchFactor(familyNote, true)
                }
            }

            // ── Free-text skills ────────────────────────────────────────────
            // NOTE: the original code combined `&&` and `||` without brackets
            // here, which made every role whose sector merely contained the
            // letters "it" score a large bonus. Fixed with explicit grouping.
            val skillHit = profile.skills.any { skill ->
                val s = skill.trim().lowercase()
                s.length >= 3 && (
                        role.job_role.lowercase().contains(s) ||
                                role.sector.lowercase().contains(s)
                        )
            }
            if (skillHit) score += 40

            if (matchedInterests.isEmpty() && !familyFit && !skillHit) score -= 12

            // ── PM-AJAY GIA priority domain ─────────────────────────────────
            if (role.isFundable()) {
                score += 26
                factors += MatchFactor(tr(lang, "reason_fundable"), true)
            }

            // ── Self-employment vs wage preference ──────────────────────────
            when (profile.preference) {
                Preference.SELF -> if (role.selfEmploymentFit()) {
                    score += 24
                    factors += MatchFactor(tr(lang, "reason_self"), true)
                }
                Preference.WAGE -> if (role.wageFit()) {
                    score += 24
                    factors += MatchFactor(tr(lang, "reason_wage"), true)
                }
                null -> Unit
            }

            // ── Local centre availability & mobility ────────────────────────
            if (centre != null) {
                score += 12
                if (role.sector == "agriculture" || role.sector == "food_processing") score += 10
                factors += MatchFactor(tr(lang, "reason_centre"), true)
            }
            when (profile.mobility) {
                Mobility.LOCAL -> score += if (centre != null) 20 else -14
                Mobility.DISTRICT -> score += 10
                Mobility.STATE -> score += 5
                null -> Unit
            }

            // ── Physical constraints ────────────────────────────────────────
            if (profile.physicalConstraints.isNotBlank() && role.isPhysicallyDemanding()) {
                val pc = profile.physicalConstraints.lowercase()
                val limiting = LIMITING_WORDS.any { pc.contains(it) }
                if (limiting) score -= 55
            }

            // Prefer accessible entry levels when everything else is equal.
            score += (8 - role.levelInt()).coerceAtLeast(0)

            if (factors.isEmpty()) factors += MatchFactor(tr(lang, "reason_base"), true)

            results += MatchedRole(
                role = role,
                score = score,
                confidence = 0, // filled in after normalisation
                reason = factors.first().label,
                skillGapNote = buildSkillGapNote(role, profile, lang),
                centre = centre,
                familyFitNote = familyNote,
                regionOpportunity = buildRegionOpportunity(role, profile, lang),
                factors = factors,
                eligible = eligible
            )
        }

        if (results.isEmpty()) return emptyList()

        // Prefer eligible packs, then score. Keep at most 2 per sector so the
        // list is varied rather than six near-identical agriculture packs.
        val ranked = results
            .sortedWith(compareByDescending<MatchedRole> { it.eligible }.thenByDescending { it.score })

        val perSector = mutableMapOf<String, Int>()
        val diversified = mutableListOf<MatchedRole>()
        for (m in ranked) {
            val used = perSector.getOrDefault(m.role.sector, 0)
            if (used >= 2) continue
            perSector[m.role.sector] = used + 1
            diversified += m
            if (diversified.size >= limit) break
        }
        // Top up if the sector cap left us short.
        if (diversified.size < limit) {
            for (m in ranked) {
                if (diversified.size >= limit) break
                if (diversified.none { it.role.qp_code == m.role.qp_code }) diversified += m
            }
        }

        val best = diversified.firstOrNull()?.score ?: 1
        val worstReference = (best - 120).coerceAtLeast(1)
        return diversified.map { m ->
            val pct = if (best <= worstReference) 95
            else (55 + 45f * (m.score - worstReference) / (best - worstReference)).roundToInt()
            m.copy(confidence = pct.coerceIn(35, 99))
        }
    }

    private fun buildSkillGapNote(role: JobRole, profile: UserProfile, lang: String): String {
        val userRank = profile.education?.rank ?: EducationLevel.CLASS_8.rank
        val required = role.requiredEduRank()
        return if (userRank >= required) {
            tr(lang, "eligible")
        } else {
            val needKey = when (required) {
                0 -> "edu_below8"
                1 -> "edu_8"
                2 -> "edu_10"
                3 -> "edu_12"
                4 -> "edu_iti"
                else -> "edu_grad"
            }
            "${tr(lang, "needs_edu")} · ${tr(lang, needKey)}"
        }
    }

    /**
     * Why this trade makes sense *here*.
     *
     * This used to print the nearest centre and stop, which answers "where do
     * I train?" but never the question the PS actually asks -- whether the
     * local economy can absorb the trade. The researched per-district notes
     * carry that: Ariyalur is a cement and lime belt, Salem is the silver
     * anklet hub. The note is appended only when the district's strong
     * sectors actually include this role's sector, so the claim is never
     * decorative.
     */
    private fun buildRegionOpportunity(role: JobRole, profile: UserProfile, lang: String): String {
        val district = profile.district
        val centre = getCentreForDistrict(district, profile.state)
        val econ = districtEconomy[district]

        val parts = mutableListOf<String>()
        if (centre != null) parts += "${centre.name}, ${centre.district}"
        if (econ != null && econ.note.isNotBlank() &&
            econ.strongSectors.contains(role.sector)
        ) {
            parts += econ.note
        }
        if (parts.isNotEmpty()) return parts.joinToString(" — ")

        return when {
            profile.localOpportunity.isNotBlank() -> profile.localOpportunity
            econ != null && econ.note.isNotBlank() -> econ.note
            district.isNotBlank() && districtData.without_centre.contains(district) ->
                tr(lang, "no_centre")
            else -> tr(lang, "reason_base")
        }
    }

    // ── Browse / search ─────────────────────────────────────────────────────

    fun searchRoles(query: String, sector: String? = null, limit: Int = 200): List<JobRole> {
        val q = query.trim().lowercase()
        return roles.asSequence()
            .filter { sector.isNullOrBlank() || it.sector == sector }
            .filter {
                q.isEmpty() ||
                        it.job_role.lowercase().contains(q) ||
                        it.qp_code.lowercase().contains(q) ||
                        it.sector.lowercase().contains(q) ||
                        it.ssc.lowercase().contains(q)
            }
            .take(limit)
            .toList()
    }

    fun roleByCode(qpCode: String): JobRole? = roles.firstOrNull { it.qp_code == qpCode }

    companion object {
        val INTEREST_KEYS = listOf(
            "dairy", "cattle", "goat", "poultry", "farming",
            "food", "machine", "textile", "construction", "tailor"
        )

        private val LIMITING_WORDS = listOf(
            "cannot", "can't", "cant", "unable", "heavy", "disab", "injur",
            "weak", "pain", "surgery", "முடியாது", "नहीं", "भारी"
        )

        /**
         * Last-resort English copy. i18n.json is the real source of truth; this
         * only guards against a corrupted asset so the UI never shows raw keys.
         */
        private val FALLBACK_EN = mapOf(
            "name" to "JanDwar",
            "tagline" to "Gateway for Citizens",
            "continue" to "Continue",
            "back" to "Back",
            "next" to "Next",
            "settings" to "Settings",
            "search" to "Search",
            "submit" to "Show my options",
            "options" to "Your options",
            "eligible" to "You meet the entry requirement",
            "needs_edu" to "Needs more schooling",
            "reason_base" to "Matched on education, scheme rules and centre availability.",
            "no_results" to "No safe match yet"
        )
    }
}
