package `in`.jandwar.app.ai

/**
 * Everything an extractor (cloud LLM or on-device parser) managed to pull out
 * of one utterance. Merged cumulatively across the interview.
 */
data class ProfileFragment(
    var edu: String? = null,
    var preference: String? = null,
    var mobility: String? = null,
    /** English state name, e.g. "Tamil Nadu". Asked, never assumed. */
    var state: String? = null,
    var district: String? = null,
    var familyOccupation: String? = null,
    var currentLivelihood: String? = null,
    var physicalConstraints: String? = null,
    var localOpportunity: String? = null,
    var interests: MutableList<String> = mutableListOf(),
    var skills: MutableList<String> = mutableListOf(),
    /** Assistant's next utterance, already phrased in the user's language. */
    var nextQuestion: String? = null,
    /** Set by the LLM when it believes the interview is finished. */
    var isComplete: Boolean = false
) {
    fun hasEdu() = !edu.isNullOrBlank()
    fun hasPref() = !preference.isNullOrBlank()
    fun hasMobility() = !mobility.isNullOrBlank()
    fun hasState() = !state.isNullOrBlank()
    fun hasDistrict() = !district.isNullOrBlank()
    fun hasFamilyOccupation() = !familyOccupation.isNullOrBlank()
    fun hasCurrentLivelihood() = !currentLivelihood.isNullOrBlank()
    /**
     * Answered either way: a stated difficulty, or the sentinel "None" when the
     * user says there is none. Both are answers -- only an unasked question is
     * unfilled.
     */
    fun hasConstraints() = !physicalConstraints.isNullOrBlank()
    fun hasPhysicalConstraints() = !physicalConstraints.isNullOrBlank()
    fun hasInterests() = interests.isNotEmpty()
    fun hasSkills() = skills.isNotEmpty()

    /** Folds newly extracted values into this fragment. Non-destructive. */
    fun merge(other: ProfileFragment?) {
        if (other == null) return
        if (other.hasEdu()) edu = other.edu
        if (other.hasPref()) preference = other.preference
        if (other.hasMobility()) mobility = other.mobility
        if (other.hasState()) state = other.state
        if (other.hasDistrict()) district = other.district
        if (other.hasFamilyOccupation()) familyOccupation = other.familyOccupation
        if (other.hasCurrentLivelihood()) currentLivelihood = other.currentLivelihood
        if (other.hasPhysicalConstraints()) physicalConstraints = other.physicalConstraints
        if (!other.localOpportunity.isNullOrBlank()) localOpportunity = other.localOpportunity
        other.interests.forEach { if (!interests.contains(it)) interests.add(it) }
        other.skills.forEach { if (!skills.contains(it)) skills.add(it) }
        if (!other.nextQuestion.isNullOrBlank()) nextQuestion = other.nextQuestion
        if (other.isComplete) isComplete = true
    }

    fun copyOf(): ProfileFragment = copy(
        interests = interests.toMutableList(),
        skills = skills.toMutableList()
    )

    /** The slots still unknown, in the order we want to ask about them. */
    fun missingSlots(): List<Slot> = Slot.entries.filter { !it.isFilled(this) }

    fun filledSlotCount(): Int = Slot.entries.count { it.isFilled(this) }

    /** Enough collected to produce a trustworthy recommendation. */
    fun isUsable(): Boolean =
        hasEdu() && hasState() && hasDistrict() && (hasInterests() || hasFamilyOccupation())

    /** Every slot answered. */
    fun isFullyComplete(): Boolean = missingSlots().isEmpty()

    fun summaryLine(): String = buildList {
        edu?.let { add(it) }
        familyOccupation?.let { add(it) }
        currentLivelihood?.let { add(it) }
        if (interests.isNotEmpty()) add(interests.joinToString("/"))
        district?.let { add(it) }
        state?.let { add(it) }
    }.joinToString(" · ")

    /** Interview slots in priority order. */
    enum class Slot {
        EDUCATION,
        FAMILY_OCCUPATION,
        CURRENT_LIVELIHOOD,
        INTERESTS,
        PREFERENCE,
        MOBILITY,
        CONSTRAINTS,
        // State is asked immediately before the district so the district
        // question can name it ("...which district of Kerala?") and the
        // district answer can be validated against that state's list.
        STATE,
        DISTRICT;

        fun isFilled(f: ProfileFragment): Boolean = when (this) {
            EDUCATION -> f.hasEdu()
            FAMILY_OCCUPATION -> f.hasFamilyOccupation()
            CURRENT_LIVELIHOOD -> f.hasCurrentLivelihood()
            INTERESTS -> f.hasInterests()
            PREFERENCE -> f.hasPref()
            MOBILITY -> f.hasMobility()
            CONSTRAINTS -> f.hasConstraints()
            STATE -> f.hasState()
            DISTRICT -> f.hasDistrict()
        }
    }

    companion object {
        val TOTAL_SLOTS = Slot.entries.size
    }
}
