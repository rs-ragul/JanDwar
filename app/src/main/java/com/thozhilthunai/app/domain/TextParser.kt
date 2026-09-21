package com.thozhilthunai.app.domain

import com.thozhilthunai.app.data.model.EducationLevel
import com.thozhilthunai.app.data.model.IntakeFields
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TextParser parses a free-text sentence into structured IntakeFields.
 * This is a deterministic implementation using keyword matching.
 * Replace [DeterministicTextParser] with an AI-backed implementation via the seam.
 */
interface TextParser {
    fun parse(sentence: String): IntakeFields
}

@Singleton
class DeterministicTextParser @Inject constructor() : TextParser {

    // Education keywords → EducationLevel
    private val educationKeywords = mapOf(
        // Tamil
        "8-க்கு கீழ்" to EducationLevel.BELOW_8,
        "8ம் வகுப்பு" to EducationLevel.STANDARD_8,
        "10ம் வகுப்பு" to EducationLevel.STANDARD_10,
        "12ம் வகுப்பு" to EducationLevel.STANDARD_12,
        // English
        "below 8" to EducationLevel.BELOW_8,
        "8th" to EducationLevel.STANDARD_8,
        "10th" to EducationLevel.STANDARD_10,
        "sslc" to EducationLevel.STANDARD_10,
        "12th" to EducationLevel.STANDARD_12,
        "hsc" to EducationLevel.STANDARD_12,
        "plus two" to EducationLevel.STANDARD_12,
        "+2" to EducationLevel.STANDARD_12,
        "iti" to EducationLevel.ITI_DIPLOMA,
        "diploma" to EducationLevel.ITI_DIPLOMA,
        "graduate" to EducationLevel.GRADUATE,
        "degree" to EducationLevel.GRADUATE,
        "bsc" to EducationLevel.GRADUATE,
        "ba " to EducationLevel.GRADUATE,
        "bcom" to EducationLevel.GRADUATE,
        "btech" to EducationLevel.GRADUATE,
        "be " to EducationLevel.GRADUATE
    )

    // Interest keywords — English + Tamil
    private val interestKeywords: Map<String, List<String>> = mapOf(
        "dairy" to listOf("dairy", "milk", "பால்", "பால்பண்ணை", "doodh"),
        "cattle" to listOf("cattle", "livestock", "cow", "bull", "கால்நடை", "pashu"),
        "goat" to listOf("goat", "sheep", "ஆடு", "bakri"),
        "poultry" to listOf("poultry", "chicken", "hen", "கோழி", "murgi"),
        "farming" to listOf("farm", "agriculture", "crop", "விவசாயம்", "krishi"),
        "food" to listOf("food", "processing", "bakery", "pickle", "உணவு", "khana"),
        "machine" to listOf("machine", "mechanic", "repair", "இயந்திரம்"),
        "textile" to listOf("textile", "handloom", "weaving", "நெசவு", "kapda"),
        "construction" to listOf("construction", "mason", "plumber", "கட்டுமானம்", "nirmaan"),
        "tailor" to listOf("tailor", "stitch", "sewing", "தையல்", "silai")
    )

    // TN districts keywords
    private val districtKeywords: Map<String, List<String>> = mapOf(
        "Chennai" to listOf("chennai", "madras"),
        "Coimbatore" to listOf("coimbatore", "kovai"),
        "Madurai" to listOf("madurai"),
        "Tiruchirappalli" to listOf("trichy", "tiruchirappalli"),
        "Salem" to listOf("salem"),
        "Tirunelveli" to listOf("tirunelveli", "nellai"),
        "Tiruppur" to listOf("tiruppur", "tirupur"),
        "Erode" to listOf("erode"),
        "Vellore" to listOf("vellore"),
        "Thoothukudi" to listOf("thoothukudi", "tuticorin"),
        "Kanchipuram" to listOf("kanchipuram"),
        "Thanjavur" to listOf("thanjavur", "tanjore"),
        "Dindigul" to listOf("dindigul"),
        "Cuddalore" to listOf("cuddalore"),
        "Krishnagiri" to listOf("krishnagiri"),
        "Namakkal" to listOf("namakkal"),
        "Nagapattinam" to listOf("nagapattinam"),
        "Ramanathapuram" to listOf("ramanathapuram", "ramnad"),
        "Sivagangai" to listOf("sivagangai"),
        "Theni" to listOf("theni"),
        "Tiruvannamalai" to listOf("tiruvannamalai"),
        "Virudhunagar" to listOf("virudhunagar"),
        "Karur" to listOf("karur"),
        "Pudukkottai" to listOf("pudukkottai"),
        "Ariyalur" to listOf("ariyalur"),
        "Perambalur" to listOf("perambalur"),
        "Dharmapuri" to listOf("dharmapuri"),
        "Villupuram" to listOf("villupuram"),
        "Kallakurichi" to listOf("kallakurichi"),
        "Tiruvallur" to listOf("tiruvallur"),
        "Kanyakumari" to listOf("kanyakumari", "nagercoil"),
        "Tiruvarur" to listOf("tiruvarur"),
        "The Nilgiris" to listOf("nilgiris", "ooty", "udhagamandalam"),
        "Ranipet" to listOf("ranipet"),
        "Tenkasi" to listOf("tenkasi"),
        "Chengalpattu" to listOf("chengalpattu"),
        "Mayiladuthurai" to listOf("mayiladuthurai"),
        "Tirupathur" to listOf("tirupathur")
    )

    override fun parse(sentence: String): IntakeFields {
        val lower = sentence.lowercase()

        // Education
        val education = educationKeywords.entries
            .filter { (kw, _) -> lower.contains(kw) }
            .maxByOrNull { (kw, _) -> kw.length }
            ?.value ?: EducationLevel.STANDARD_10

        // District
        val district = districtKeywords.entries
            .firstOrNull { (_, keywords) -> keywords.any { lower.contains(it) } }
            ?.key ?: ""

        // Interests
        val interests = interestKeywords.entries
            .filter { (_, keywords) -> keywords.any { lower.contains(it) } }
            .map { (key, _) -> key }
            .toSet()

        return IntakeFields(
            education = education,
            district = district,
            interests = interests
        )
    }
}
