package `in`.jandwar.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class JobRole(
    val qp_code: String = "",
    val job_role: String = "",
    val nsqf_level: String = "",
    val notional_hours: String = "",
    val ssc: String = "",
    val sector: String = ""
) {
    fun levelInt(): Int = nsqf_level.filter { it.isDigit() }.toIntOrNull() ?: 3
    fun hoursInt(): Int = notional_hours.filter { it.isDigit() }.toIntOrNull() ?: 300
    fun isLongTerm(): Boolean = hoursInt() >= 600
    fun isValidName(): Boolean {
        val lower = job_role.lowercase()
        return job_role.length > 3 && lower != "english hindi" && !job_role.startsWith("QG-")
    }
    fun levelLabel(trLevel: String): String =
        if (nsqf_level.isBlank()) "$trLevel ${levelInt()} (inferred)" else nsqf_level

    fun isFundable(): Boolean =
        sector == "agriculture" || sector == "food_processing" ||
                sector == "construction" || sector == "handloom_textile"

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
            "dairy" -> s == "agriculture" || n.contains("dairy") || n.contains("milk")
            "cattle" -> n.contains("cattle") || n.contains("livestock") || n.contains("dairy") || s == "agriculture"
            "goat" -> n.contains("goat") || n.contains("sheep") || s == "agriculture"
            "poultry" -> n.contains("poultry") || n.contains("chicken") || s == "agriculture"
            "farming" -> s == "agriculture" || n.contains("farm") || n.contains("crop") || n.contains("agri")
            "food" -> s == "food_processing" || n.contains("food") || n.contains("baker") || n.contains("miller")
            "machine" -> n.contains("machine") || n.contains("technician") || n.contains("operator") || s == "electronics_automation"
            "textile" -> s == "handloom_textile" || s == "apparel" || n.contains("textile") || n.contains("loom")
            "construction" -> s == "construction" || n.contains("construction") || n.contains("mason") || n.contains("bar bender")
            "tailor" -> s == "apparel" || n.contains("sewing") || n.contains("tailor") || n.contains("stitch")
            else -> false
        }
    }

    fun matchesFamilyOccupation(familyOcc: String): Boolean {
        if (familyOcc.isBlank()) return false
        val f = familyOcc.lowercase()
        val n = job_role.lowercase()
        val s = sector.lowercase()
        return when {
            f.contains("farm") || f.contains("agri") || f.contains("cattle") || f.contains("dairy") || f.contains("milk") || f.contains("goat") || f.contains("sheep") || f.contains("poultry") -> s == "agriculture" || n.contains("farm") || n.contains("agri") || n.contains("livestock")
            f.contains("tailor") || f.contains("weav") || f.contains("loom") || f.contains("textile") || f.contains("stitch") -> s in listOf("handloom_textile", "apparel")
            f.contains("construct") || f.contains("mason") || f.contains("labour") || f.contains("labor") -> s == "construction"
            f.contains("food") || f.contains("cook") || f.contains("baker") -> s == "food_processing"
            else -> false
        }
    }

    fun selfEmploymentFit(): Boolean {
        val n = job_role.lowercase()
        return sector == "agriculture" || sector == "food_processing" ||
                sector == "handloom_textile" || sector == "apparel" ||
                n.contains("entrepreneur") || n.contains("artisan") || n.contains("farm")
    }

    fun wageFit(): Boolean {
        val n = job_role.lowercase()
        return n.contains("assistant") || n.contains("operator") || n.contains("technician") ||
                n.contains("worker") || n.contains("supervisor") || sector == "construction" ||
                sector == "media_entertainment" || sector == "electronics_automation"
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
)

@Serializable
data class DistrictsData(
    val all: List<String> = emptyList(),
    val with_centre: List<String> = emptyList(),
    val without_centre: List<String> = emptyList()
)

data class I18nData(
    val langs: List<Pair<String, String>> = emptyList(),
    val strings: Map<String, Map<String, String>> = emptyMap(),
    val interests: Map<String, Map<String, String>> = emptyMap()
)

enum class EducationLevel(val key: String, val rank: Int) {
    BELOW_8("edu_below8", 0),
    CLASS_8("edu_8", 1),
    CLASS_10("edu_10", 2),
    CLASS_12("edu_12", 3),
    ITI_DIPLOMA("edu_iti", 4),
    GRADUATE("edu_grad", 5);

    companion object {
        fun fromKey(key: String): EducationLevel? = values().find { it.key == key }
        fun fromAiString(ai: String?): EducationLevel? = when (ai?.lowercase()) {
            "none", "class5", "below_8th", "read_write", "below8" -> BELOW_8
            "class8", "8th", "edu_8" -> CLASS_8
            "class10", "10th", "edu_10", "sslc" -> CLASS_10
            "class12", "12th", "edu_12", "hsc" -> CLASS_12
            "iti_diploma", "iti", "diploma", "edu_iti" -> ITI_DIPLOMA
            "graduate", "grad", "edu_grad", "degree", "college" -> GRADUATE
            else -> null
        }
    }
}

enum class Preference(val key: String) {
    SELF("pref_self"),
    WAGE("pref_wage");

    companion object {
        fun fromAiString(ai: String?): Preference? = when (ai?.lowercase()) {
            "pref_self", "self_employment", "self", "business", "own", "entrepreneur" -> SELF
            "pref_wage", "wage_employment", "wage", "job", "employer", "salary" -> WAGE
            else -> null
        }
    }
}

enum class Mobility(val key: String, val aiValue: String) {
    LOCAL("travel_local", "local"),
    DISTRICT("travel_district", "district"),
    STATE("travel_any", "state");

    companion object {
        fun fromAiString(ai: String?): Mobility? = when (ai?.lowercase()) {
            "local", "within_village", "within_block", "travel_local", "village", "nearby" -> LOCAL
            "district", "within_district", "travel_district" -> DISTRICT
            "state", "anywhere", "travel_any", "far", "tamil nadu" -> STATE
            else -> null
        }
    }
}

// Extended profile per SIH26097 problem statement
data class UserProfile(
    var education: EducationLevel? = null,
    var preference: Preference? = null,
    var mobility: Mobility? = null,
    var district: String = "",
    var interests: Set<String> = emptySet(),
    // New fields from problem statement
    var familyOccupation: String = "",
    var currentLivelihood: String = "",
    var physicalConstraints: String = "",
    var localOpportunity: String = "",
    var skills: Set<String> = emptySet()
) {
    fun isComplete(): Boolean {
        // Core fields required for matching, others optional but collected for empathy
        return education != null && preference != null && mobility != null &&
                district.isNotBlank() && interests.isNotEmpty()
    }

    fun isPartiallyComplete(): Boolean {
        return education != null || preference != null || mobility != null ||
                district.isNotBlank() || interests.isNotEmpty() ||
                familyOccupation.isNotBlank() || currentLivelihood.isNotBlank()
    }

    fun isFullyComplete(): Boolean {
        return isComplete() && familyOccupation.isNotBlank() && currentLivelihood.isNotBlank()
    }

    fun missingFields(): List<String> {
        val missing = mutableListOf<String>()
        if (education == null) missing.add("education")
        if (familyOccupation.isBlank()) missing.add("familyOccupation")
        if (currentLivelihood.isBlank()) missing.add("currentLivelihood")
        if (preference == null) missing.add("preference")
        if (mobility == null) missing.add("mobility")
        if (district.isBlank()) missing.add("district")
        if (interests.isEmpty()) missing.add("interests")
        if (physicalConstraints.isBlank()) missing.add("physicalConstraints")
        return missing
    }

    fun toReadableSummary(lang: String = "en"): String {
        return buildString {
            append("Education: ${education?.name ?: "not set"}, ")
            append("Family: $familyOccupation, ")
            append("Current: $currentLivelihood, ")
            append("Interests: ${interests.joinToString()}, ")
            append("Preference: ${preference?.name ?: "not set"}, ")
            append("Mobility: ${mobility?.name ?: "not set"}, ")
            append("District: $district, ")
            if (physicalConstraints.isNotBlank()) append("Constraints: $physicalConstraints, ")
            if (localOpportunity.isNotBlank()) append("Local: $localOpportunity")
        }
    }
}

data class MatchedRole(
    val role: JobRole,
    val score: Int,
    val reason: String,
    val skillGapNote: String,
    val centre: Centre?,
    // Enhanced per problem statement
    val familyFitNote: String = "",
    val regionOpportunity: String = ""
)

data class InterestChip(val key: String, val label: String)

// Conversation history for natural chat UI
data class ConversationMessage(
    val role: String = "assistant", // "user" or "assistant"
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isUser: Boolean = role == "user"
) {
    constructor(isUser: Boolean, text: String) : this(
        role = if (isUser) "user" else "assistant",
        text = text,
        isUser = isUser
    )
}
