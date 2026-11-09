package org.mpc.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The language requirements a student must meet to graduate, as served by
 * `GET /api/v1/intl/graduation-language`; see docs/api-contract.md.
 */
@Serializable
data class GraduationLanguage(
    /** The day the numbers were last compared with the official sources. */
    val verifiedOn: String,
    val disclaimer: LocalizedText,
    /** Test id to its display name; the keys of [GraduationLanguageRule.english]. */
    val tests: Map<String, LocalizedText>,
    val general: GraduationLanguageGeneral,
    val chinese: ChineseRequirements,
    val rules: List<GraduationLanguageRule>,
    val sources: List<GraduationLanguageSource>,
)

@Serializable
data class LocalizedText(
    val zh: String,
    val en: String,
)

@Serializable
enum class Degree {
    @SerialName("bachelor")
    BACHELOR,

    @SerialName("master")
    MASTER,

    @SerialName("phd")
    PHD,
}

@Serializable
data class GraduationLanguageRule(
    val id: String,
    val degree: Degree,
    val college: LocalizedText,
    /** Null when the rule covers the whole college. */
    val department: LocalizedText? = null,
    val applyAt: LocalizedText? = null,
    val summary: LocalizedText? = null,
    val english: Map<String, List<LanguageThreshold>> = emptyMap(),
    val otherLanguages: List<OtherLanguageTest> = emptyList(),
    val otherLanguagesWithoutThreshold: List<String> = emptyList(),
    val alternatives: List<String> = emptyList(),
    val internationalStudents: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
)

@Serializable
data class LanguageThreshold(
    /** Transcribed from the official document; text to show, not a number to compare. */
    val value: String,
    /** First admission academic year (ROC calendar) the value applies to; null means no lower bound. */
    val cohortMin: Int? = null,
    val cohortMax: Int? = null,
    val note: String? = null,
) {
    fun appliesTo(cohort: Int): Boolean = cohort >= (cohortMin ?: cohort) && cohort <= (cohortMax ?: cohort)
}

@Serializable
data class OtherLanguageTest(
    val language: String,
    val test: String,
    val requirement: String,
)

@Serializable
data class GraduationLanguageSource(
    val id: String,
    val title: String,
    val url: String,
    val org: String,
)

@Serializable
data class GraduationLanguageGeneral(
    val summary: LocalizedText,
    val procedure: List<String> = emptyList(),
    val alternatives: List<String> = emptyList(),
    val internationalStudents: List<String> = emptyList(),
    val legalBasis: List<String> = emptyList(),
    val graduate: GraduateOverview? = null,
)

@Serializable
data class GraduateOverview(
    val summary: LocalizedText,
    val internationalStudents: List<String> = emptyList(),
    val noRuleFound: List<String> = emptyList(),
    val notChecked: List<String> = emptyList(),
)

@Serializable
data class ChineseRequirements(
    val summary: LocalizedText,
    val requirements: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val notGraduationRequirements: List<String> = emptyList(),
)

/**
 * The rules of one [degree] for a student admitted in academic year [cohort], each narrowed to the
 * thresholds of that year. [college] is a college's Chinese name; null keeps every college.
 */
fun GraduationLanguage.rulesFor(
    degree: Degree,
    college: String?,
    cohort: Int,
): List<GraduationLanguageRule> = rules
    .filter { it.degree == degree && (college == null || it.college.zh == college) }
    .mapNotNull { rule ->
        val english = rule.english
            .mapValues { (_, thresholds) -> thresholds.filter { it.appliesTo(cohort) } }
            .filterValues { it.isNotEmpty() }
        // A rule whose every threshold belongs to other admission years is not this student's rule.
        if (english.isEmpty() && rule.english.isNotEmpty()) null else rule.copy(english = english)
    }

/** Chinese names of the colleges that have a rule for [degree], in the order the backend lists them. */
fun GraduationLanguage.collegesFor(degree: Degree): List<LocalizedText> = rules
    .filter { it.degree == degree }
    .map { it.college }
    .distinctBy { it.zh }
