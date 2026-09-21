package com.thozhilthunai.app

import com.thozhilthunai.app.data.model.*
import com.thozhilthunai.app.data.repository.DataRepository
import com.thozhilthunai.app.domain.MatcherUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MatcherUseCaseTest {

    private lateinit var matcher: MatcherUseCase
    private val dataRepository: DataRepository = mock()

    private val sampleRoles = listOf(
        JobRole("AGR/Q4101", "Dairy Farmer", "3", "300", "ASCI", "dairy"),
        JobRole("FIC/Q5005", "Assistant Baking Technician", "3", "300", "FICSI", "food_processing"),
        JobRole("TEX/Q7101", "Tailor", "4", "400", "TSCL", "apparel"),
        JobRole("IT/Q1001", "IT Support", "5", "500", "SSC NASSCOM", "it_ites"),
        JobRole("ELE/Q1001", "Electrician", "5", "500", "ESSC", "electrical"),
    )

    @Before
    fun setUp() {
        val stateFlow = MutableStateFlow(sampleRoles)
        whenever(dataRepository.jobRoles).thenReturn(stateFlow)
        whenever(dataRepository.getJobRoleSnapshot()).thenReturn(sampleRoles)
        matcher = MatcherUseCase(dataRepository)
    }

    @Test
    fun `grad level includes all roles`() {
        val fields = IntakeFields(education = EducationLevel.GRADUATE, district = "Erode", interests = emptySet())
        val result = matcher.match(fields)
        assertEquals(5, result.size)
    }

    @Test
    fun `8th standard excludes NSQF 4 and above`() {
        val fields = IntakeFields(education = EducationLevel.STANDARD_8, district = "", interests = emptySet())
        val result = matcher.match(fields)
        // NSQF 3 roles should be included: Dairy Farmer, Assistant Baking Technician
        assertTrue(result.any { it.role.qpCode == "AGR/Q4101" })
        assertTrue(result.any { it.role.qpCode == "FIC/Q5005" })
        // NSQF 4 and 5 should be excluded
        assertFalse(result.any { it.role.qpCode == "TEX/Q7101" })
        assertFalse(result.any { it.role.qpCode == "IT/Q1001" })
    }

    @Test
    fun `interest boosts dairy roles`() {
        val fields = IntakeFields(
            education = EducationLevel.GRADUATE,
            district = "",
            interests = setOf("dairy", "cattle")
        )
        val result = matcher.match(fields)
        // Dairy Farmer should be ranked first
        assertEquals("AGR/Q4101", result.first().role.qpCode)
    }

    @Test
    fun `no interests returns all eligible roles`() {
        val fields = IntakeFields(education = EducationLevel.STANDARD_10, district = "", interests = emptySet())
        val result = matcher.match(fields)
        // NSQF levels 3,4 are accessible for 10th (max=4)
        assertTrue(result.isNotEmpty())
        assertTrue(result.none { (it.role.nsqfLevel.toIntOrNull() ?: 0) > 4 })
    }

    @Test
    fun `tailor interest boosts apparel roles`() {
        val fields = IntakeFields(
            education = EducationLevel.STANDARD_10,
            district = "",
            interests = setOf("tailor")
        )
        val result = matcher.match(fields)
        val tailorRole = result.find { it.role.qpCode == "TEX/Q7101" }
        assertNotNull(tailorRole)
        // Tailor should be at the top
        assertEquals("TEX/Q7101", result.first().role.qpCode)
    }

    @Test
    fun `results are not empty for standard 10 with food interest`() {
        val fields = IntakeFields(
            education = EducationLevel.STANDARD_10,
            district = "Salem",
            interests = setOf("food")
        )
        val result = matcher.match(fields)
        assertTrue(result.isNotEmpty())
        // Food processing role should be in results
        assertTrue(result.any { it.role.qpCode == "FIC/Q5005" })
    }
}
