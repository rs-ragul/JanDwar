package `in`.jandwar.app.data.model

import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
//  Catalogue entities (NSQF qualification packs + TAHDCO/TANUVAS centres)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class JobRole(
    val qp_code: String = "",
    val job_role: String = "",
    val nsqf_level: String = "",
    val notional_hours: String = "",
    val ssc: String = "",
    val sector: String = ""
) {
    /** NSQF level as an int. Blank levels default to 3 (typical entry pack). */
    fun levelInt(): Int = nsqf_level.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 10) ?: 3

    /** Notional training hours. Blank defaults to 300 (typical short-term pack). */
    fun hoursInt(): Int = notional_hours.filter { it.isDigit() }.toIntOrNull() ?: 300

    fun isLongTerm(): Boolean = hoursInt() >= 600

    fun durationLabel(): String {
        val h = hoursInt()
        val months = Math.round(h / 130f).coerceAtLeast(1)
        return "$h hrs · ~$months ${if (months == 1) "month" else "months"}"
    }

    /** Filters out malformed rows that exist in the source catalogue. */
    fun isValidName(): Boolean {
        val lower = job_role.trim().lowercase()
        return job_role.trim().length > 3 &&
                lower != "english hindi" &&
                !job_role.startsWith("QG-") &&
                !lower.startsWith("qp code")
    }

    /**
     * PM-AJAY GIA priority domains. These four sectors carry asset/enterprise
     * support under the Grant-in-Aid component.
     */
    fun isFundable(): Boolean = sector in FUNDABLE_SECTORS

    /**
     * Minimum education rank the pack realistically expects, derived from its
     * NSQF level (NSQF 1-2 ≈ literacy, 3 ≈ Class 8, 4 ≈ Class 10, 5 ≈ Class 12,
     * 6-7 ≈ ITI/Diploma, 8+ ≈ Graduate).
     */
    fun requiredEduRank(): Int = when (levelInt()) {
        1, 2 -> 0
        3 -> 1
        4 -> 2
        5 -> 3
        6, 7 -> 4
        else -> 5
    }

    fun matchesInterest(key: String): Boolean {
        val n = job_role.lowercase()
        val s = sector.lowercase()
        return when (key) {
            "dairy" -> n.contains("dairy") || n.contains("milk") || n.contains("cattle")
            "cattle" -> n.contains("cattle") || n.contains("livestock") || n.contains("dairy") ||
                    n.contains("animal") || n.contains("bovine")
            "goat" -> n.contains("goat") || n.contains("sheep") || n.contains("small ruminant")
            "poultry" -> n.contains("poultry") || n.contains("chicken") || n.contains("hatchery") ||
                    n.contains("broiler") || n.contains("quail")
            "farming" -> s == "agriculture" || n.contains("farm") || n.contains("crop") ||
                    n.contains("agri") || n.contains("horticulture") || n.contains("nursery")
            "food" -> s == "food_processing" || n.contains("food") || n.contains("baker") ||
                    n.contains("miller") || n.contains("dairy processing")
            "machine" -> s == "electronics_automation" || n.contains("machine") ||
                    n.contains("technician") || n.contains("operator") || n.contains("mechanic") ||
                    n.contains("electric")
            "textile" -> s == "handloom_textile" || n.contains("textile") || n.contains("loom") ||
                    n.contains("weav") || n.contains("dyeing")
            "construction" -> s == "construction" || n.contains("construction") ||
                    n.contains("mason") || n.contains("bar bender") || n.contains("plumb") ||
                    n.contains("carpent") || n.contains("painter")
            "tailor" -> s == "apparel" || n.contains("sewing") || n.contains("tailor") ||
                    n.contains("stitch") || n.contains("garment")
            else -> false
        }
    }

    /**
     * Whether this pack builds on a family / traditional occupation.
     * Used to honour the problem statement's "traditional family occupations" input.
     */
    fun matchesOccupationText(occupation: String): Boolean {
        if (occupation.isBlank()) return false
        val f = occupation.lowercase()
        val n = job_role.lowercase()
        val s = sector.lowercase()
        return when {
            listOf("farm", "agri", "cattle", "dairy", "milk", "goat", "sheep", "poultry", "cow")
                .any { f.contains(it) } ->
                s == "agriculture" || n.contains("farm") || n.contains("agri") || n.contains("livestock")

            listOf("tailor", "weav", "loom", "textile", "stitch", "garment", "sew")
                .any { f.contains(it) } ->
                s == "handloom_textile" || s == "apparel"

            listOf("construct", "mason", "labour", "labor", "coolie", "building")
                .any { f.contains(it) } -> s == "construction"

            listOf("food", "cook", "baker", "hotel", "catering", "mess")
                .any { f.contains(it) } -> s == "food_processing"

            listOf("electric", "mechanic", "repair", "machine", "welder", "fitter", "driver")
                .any { f.contains(it) } ->
                s == "electronics_automation" || n.contains("technician") || n.contains("operator")

            listOf("photo", "video", "media", "design", "print", "computer", "studio")
                .any { f.contains(it) } -> s == "media_entertainment"

            else -> false
        }
    }

    fun selfEmploymentFit(): Boolean {
        val n = job_role.lowercase()
        return sector in setOf("agriculture", "food_processing", "handloom_textile", "apparel") ||
                n.contains("entrepreneur") || n.contains("artisan") || n.contains("farm") ||
                n.contains("self")
    }

    fun wageFit(): Boolean {
        val n = job_role.lowercase()
        return n.contains("assistant") || n.contains("operator") || n.contains("technician") ||
                n.contains("worker") || n.contains("supervisor") || n.contains("helper") ||
                sector in setOf("construction", "media_entertainment", "electronics_automation")
    }

    /** Roles that involve sustained heavy physical work. */
    fun isPhysicallyDemanding(): Boolean {
        val n = job_role.lowercase()
        return sector == "construction" || n.contains("mason") || n.contains("bar bender") ||
                n.contains("lifting") || n.contains("loader") || n.contains("helper")
    }

    companion object {
        val FUNDABLE_SECTORS = setOf(
            "agriculture", "food_processing", "construction", "handloom_textile"
        )
    }
}

@Serializable
data class Centre(
    val district: String = "",
    val name: String = "",
    val address: String = "",
    val phone: String? = null,
    val trades: String = "",
    val dairy_course: Boolean? = null,
    val confidence: String = ""
) {
    fun isConfirmed(): Boolean = confidence.startsWith("CONFIRMED", ignoreCase = true)
    fun hasPhone(): Boolean = !phone.isNullOrBlank()
}

@Serializable
data class DistrictsData(
    val all: List<String> = emptyList(),
    val with_centre: List<String> = emptyList(),
    val without_centre: List<String> = emptyList()
)

data class I18nData(
    val langs: List<Pair<String, String>> = emptyList(),
    val strings: Map<String, Map<String, String>> = emptyMap(),
    val interests: Map<String, Map<String, String>> = emptyMap(),
    /**
     * Canonical English occupation label (lowercased) -> display label, per
     * language. The profile always stores the canonical English string because
     * role matching and the cloud prompt both key off it; this map exists so the
     * UI can show it in the user's own language.
     */
    val occupations: Map<String, Map<String, String>> = emptyMap()
)

// ─────────────────────────────────────────────────────────────────────────────
//  Beneficiary profile
// ─────────────────────────────────────────────────────────────────────────────

enum class EducationLevel(val key: String, val rank: Int) {
    BELOW_8("edu_below8", 0),
    CLASS_8("edu_8", 1),
    CLASS_10("edu_10", 2),
    CLASS_12("edu_12", 3),
    ITI_DIPLOMA("edu_iti", 4),
    GRADUATE("edu_grad", 5);

    companion object {
        fun fromKey(key: String): EducationLevel? = entries.find { it.key == key }

        /** Tolerant mapping from whatever the LLM or the on-device parser produced. */
        fun fromAiString(ai: String?): EducationLevel? {
            val v = ai?.trim()?.lowercase() ?: return null
            return when {
                v.isEmpty() || v == "null" -> null
                v in setOf("none", "class5", "below_8th", "below8", "read_write", "illiterate",
                    "edu_below8", "primary", "5th") -> BELOW_8
                v in setOf("class8", "8th", "edu_8", "eighth", "middle") -> CLASS_8
                v in setOf("class10", "10th", "edu_10", "sslc", "tenth", "matric") -> CLASS_10
                v in setOf("class12", "12th", "edu_12", "hsc", "twelfth", "higher_secondary",
                    "plus two", "+2") -> CLASS_12
                v in setOf("iti_diploma", "iti", "diploma", "edu_iti", "polytechnic") -> ITI_DIPLOMA
                v in setOf("graduate", "grad", "edu_grad", "degree", "college", "bachelor",
                    "postgraduate", "masters", "pg") -> GRADUATE
                else -> null
            }
        }
    }
}

enum class Preference(val key: String) {
    SELF("pref_self"),
    WAGE("pref_wage");

    companion object {
        fun fromAiString(ai: String?): Preference? = when (ai?.trim()?.lowercase()) {
            "pref_self", "self_employment", "self", "business", "own", "entrepreneur",
            "own_business" -> SELF
            "pref_wage", "wage_employment", "wage", "job", "employer", "salary",
            "employment" -> WAGE
            else -> null
        }
    }
}

enum class Mobility(val key: String, val aiValue: String) {
    LOCAL("travel_local", "local"),
    DISTRICT("travel_district", "district"),
    STATE("travel_any", "state");

    companion object {
        fun fromAiString(ai: String?): Mobility? = when (ai?.trim()?.lowercase()) {
            "local", "within_village", "within_block", "travel_local", "village", "nearby" -> LOCAL
            "district", "within_district", "travel_district" -> DISTRICT
            "state", "anywhere", "travel_any", "far", "tamil nadu", "any" -> STATE
            else -> null
        }
    }
}

/**
 * The full beneficiary picture the problem statement asks us to collect.
 */
data class UserProfile(
    val education: EducationLevel? = null,
    val preference: Preference? = null,
    val mobility: Mobility? = null,
    val district: String = "",
    val interests: Set<String> = emptySet(),
    val familyOccupation: String = "",
    val currentLivelihood: String = "",
    val physicalConstraints: String = "",
    val localOpportunity: String = "",
    val skills: Set<String> = emptySet()
) {
    /** Minimum needed to produce a trustworthy recommendation. */
    fun isComplete(): Boolean =
        education != null && district.isNotBlank() && (interests.isNotEmpty() || familyOccupation.isNotBlank())

    fun hasAnyData(): Boolean =
        education != null || preference != null || mobility != null ||
                district.isNotBlank() || interests.isNotEmpty() ||
                familyOccupation.isNotBlank() || currentLivelihood.isNotBlank()

    fun completedFieldCount(): Int {
        var n = 0
        if (education != null) n++
        if (familyOccupation.isNotBlank()) n++
        if (currentLivelihood.isNotBlank()) n++
        if (interests.isNotEmpty()) n++
        if (preference != null) n++
        if (mobility != null) n++
        if (district.isNotBlank()) n++
        return n
    }

    fun totalFieldCount(): Int = 7
}

// ─────────────────────────────────────────────────────────────────────────────
//  Recommendation output
// ─────────────────────────────────────────────────────────────────────────────

/** One human-readable line explaining part of the score. */
data class MatchFactor(val label: String, val positive: Boolean)

data class MatchedRole(
    val role: JobRole,
    val score: Int,
    /** 0..100, normalised for display. */
    val confidence: Int,
    val reason: String,
    val skillGapNote: String,
    val centre: Centre?,
    val familyFitNote: String = "",
    val regionOpportunity: String = "",
    val factors: List<MatchFactor> = emptyList(),
    val eligible: Boolean = true
)

data class InterestChip(val key: String, val label: String)

/** A single turn in the assistant conversation. */
data class ConversationMessage(
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
