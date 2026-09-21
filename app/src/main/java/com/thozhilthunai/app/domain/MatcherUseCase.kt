package com.thozhilthunai.app.domain

import com.thozhilthunai.app.data.model.EducationLevel
import com.thozhilthunai.app.data.model.IntakeFields
import com.thozhilthunai.app.data.model.JobRole
import com.thozhilthunai.app.data.model.MatchedRole
import com.thozhilthunai.app.data.repository.DataRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatcherUseCase @Inject constructor(
    private val repository: DataRepository
) {
    // Map sector strings from JSON to interest keys
    private val sectorToInterests = mapOf(
        "food_processing" to setOf("food"),
        "animal_husbandry" to setOf("dairy", "cattle", "goat", "poultry"),
        "dairy" to setOf("dairy", "cattle"),
        "agriculture" to setOf("farming", "cattle"),
        "textile" to setOf("textile", "tailor"),
        "apparel" to setOf("tailor", "textile"),
        "construction" to setOf("construction", "machine"),
        "electrical" to setOf("machine"),
        "it_ites" to setOf("machine"),
        "retail" to emptySet<String>(),
        "logistics" to emptySet<String>(),
        "beauty" to emptySet<String>(),
        "healthcare" to emptySet<String>(),
        "media" to emptySet<String>()
    )

    // NSQF level ordering for entry qualification
    private val nsqfLevelOrder = mapOf(
        "1" to 0, "2" to 1, "3" to 2, "4" to 3,
        "5" to 4, "6" to 5, "7" to 6, "8" to 7
    )

    // Education → max NSQF level accessible
    private val eduToMaxNsqf = mapOf(
        EducationLevel.BELOW_8 to 2,
        EducationLevel.STANDARD_8 to 3,
        EducationLevel.STANDARD_10 to 4,
        EducationLevel.STANDARD_12 to 5,
        EducationLevel.ITI_DIPLOMA to 6,
        EducationLevel.GRADUATE to 8
    )

    fun match(fields: IntakeFields): List<MatchedRole> {
        val roles = repository.jobRoles.let {
            // Use synchronous snapshot from StateFlow
            (it as kotlinx.coroutines.flow.StateFlow).value
        }

        val maxNsqf = eduToMaxNsqf[fields.education] ?: 4

        return roles
            .map { role ->
                val levelNum = nsqfLevelOrder[role.nsqfLevel] ?: -1
                val levelInferred = role.nsqfLevel.contains("inferred", ignoreCase = true)

                // Education gate: only include roles within user's level
                if (levelNum > maxNsqf) return@map null

                // Score: interest matching
                val roleInterests = sectorToInterests[role.sector] ?: emptySet()
                val interestScore = if (fields.interests.isEmpty()) 1
                else (roleInterests intersect fields.interests).size

                // Tie-break: prefer lower NSQF for lower-literacy
                val levelScore = maxNsqf - levelNum

                val score = (interestScore * 10) + levelScore

                MatchedRole(role = role, score = score, levelInferred = levelInferred)
            }
            .filterNotNull()
            .sortedByDescending { it.score }
    }
}
