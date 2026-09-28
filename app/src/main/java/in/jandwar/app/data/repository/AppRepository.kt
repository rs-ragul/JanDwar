package `in`.jandwar.app.data.repository

import `in`.jandwar.app.data.local.AssetDataSource
import `in`.jandwar.app.data.model.Centre
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

    fun getCentreForDistrict(district: String): Centre? {
        if (district.isBlank()) return null
        return centreList.firstOrNull { it.district.equals(district, ignoreCase = true) }
    }

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
        val centre = getCentreForDistrict(profile.district)
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
                familyNote = "${tr(lang, "reason_family")} ${profile.familyOccupation}"
                factors += MatchFactor(familyNote, true)
            }

            // ── Current livelihood ──────────────────────────────────────────
            if (profile.currentLivelihood.isNotBlank() &&
                role.matchesOccupationText(profile.currentLivelihood)
            ) {
                score += 32
                if (familyNote.isBlank()) {
                    familyNote = "${tr(lang, "reason_family")} ${profile.currentLivelihood}"
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

    private fun buildRegionOpportunity(role: JobRole, profile: UserProfile, lang: String): String {
        val district = profile.district
        val centre = getCentreForDistrict(district)
        return when {
            centre != null -> "${centre.name}, ${centre.district}"
            profile.localOpportunity.isNotBlank() -> profile.localOpportunity
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
