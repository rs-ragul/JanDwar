package com.thozhilthunai.app.data.model

import com.google.gson.annotations.SerializedName

data class JobRole(
    @SerializedName("qp_code") val qpCode: String,
    @SerializedName("job_role") val jobRole: String,
    @SerializedName("nsqf_level") val nsqfLevel: String,
    @SerializedName("notional_hours") val notionalHours: String,
    @SerializedName("ssc") val ssc: String,
    @SerializedName("sector") val sector: String
)

data class Centre(
    @SerializedName("district") val district: String,
    @SerializedName("name") val name: String,
    @SerializedName("address") val address: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("trades") val trades: List<String>,
    @SerializedName("dairy_course") val dairyCourse: Boolean,
    @SerializedName("confidence") val confidence: String
)

data class District(
    @SerializedName("name") val name: String,
    @SerializedName("has_centre") val hasCentre: Boolean
)

data class I18nStrings(
    @SerializedName("langs") val langs: List<List<String>>,
    @SerializedName("strings") val strings: Map<String, Map<String, String>>,
    @SerializedName("interest") val interest: Map<String, Map<String, String>>
)

enum class EducationLevel(val key: String, val order: Int) {
    BELOW_8("below8", 0),
    STANDARD_8("8", 1),
    STANDARD_10("10", 2),
    STANDARD_12("12", 3),
    ITI_DIPLOMA("iti", 4),
    GRADUATE("grad", 5);

    companion object {
        fun fromKey(key: String): EducationLevel =
            values().find { it.key == key } ?: STANDARD_10
    }
}

data class IntakeFields(
    val education: EducationLevel = EducationLevel.STANDARD_10,
    val district: String = "",
    val interests: Set<String> = emptySet()
)

data class MatchedRole(
    val role: JobRole,
    val score: Int,
    val levelInferred: Boolean
)
