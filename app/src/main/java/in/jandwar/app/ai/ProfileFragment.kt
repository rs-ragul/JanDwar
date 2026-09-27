package `in`.jandwar.app.ai

data class ProfileFragment(
    var edu: String? = null,
    var preference: String? = null,
    var interests: MutableList<String> = mutableListOf(),
    var district: String? = null,
    var mobility: String? = null,
    var nextQuestion: String? = null,
    // Extended per SIH26097 problem statement
    var familyOccupation: String? = null,
    var currentLivelihood: String? = null,
    var physicalConstraints: String? = null,
    var localOpportunity: String? = null,
    var skills: MutableList<String> = mutableListOf()
) {
    fun hasEdu() = !edu.isNullOrBlank()
    fun hasPref() = !preference.isNullOrBlank()
    fun hasInterests() = interests.isNotEmpty()
    fun hasDistrict() = !district.isNullOrBlank()
    fun hasMobility() = !mobility.isNullOrBlank()
    fun hasFamilyOccupation() = !familyOccupation.isNullOrBlank()
    fun hasCurrentLivelihood() = !currentLivelihood.isNullOrBlank()
    fun hasPhysicalConstraints() = !physicalConstraints.isNullOrBlank()
    fun hasLocalOpportunity() = !localOpportunity.isNullOrBlank()
    fun hasSkills() = skills.isNotEmpty()

    fun merge(other: ProfileFragment?) {
        if (other == null) return
        if (other.hasEdu()) edu = other.edu
        if (other.hasPref()) preference = other.preference
        if (other.hasDistrict()) district = other.district
        if (other.hasMobility()) mobility = other.mobility
        if (other.hasFamilyOccupation()) familyOccupation = other.familyOccupation
        if (other.hasCurrentLivelihood()) currentLivelihood = other.currentLivelihood
        if (other.hasPhysicalConstraints()) physicalConstraints = other.physicalConstraints
        if (other.hasLocalOpportunity()) localOpportunity = other.localOpportunity
        if (other.hasInterests()) {
            for (i in other.interests) if (!interests.contains(i)) interests.add(i)
        }
        if (other.hasSkills()) {
            for (s in other.skills) if (!skills.contains(s)) skills.add(s)
        }
        if (!other.nextQuestion.isNullOrBlank()) nextQuestion = other.nextQuestion
    }

    fun isComplete(): Boolean = hasEdu() && hasPref() && hasDistrict() && hasMobility() && hasInterests()
    
    fun isFullyComplete(): Boolean = isComplete() && hasFamilyOccupation() && hasCurrentLivelihood()

    fun missingFields(): List<String> {
        val missing = mutableListOf<String>()
        if (!hasEdu()) missing.add("education")
        if (!hasFamilyOccupation()) missing.add("familyOccupation")
        if (!hasCurrentLivelihood()) missing.add("currentLivelihood")
        if (!hasPref()) missing.add("preference")
        if (!hasMobility()) missing.add("mobility")
        if (!hasDistrict()) missing.add("district")
        if (!hasInterests()) missing.add("interests")
        return missing
    }
}
