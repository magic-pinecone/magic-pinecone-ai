package org.mpc.presentation

import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.domain.model.Degree
import org.mpc.shared.MR
import org.mpc.shared.intl_admission_year
import org.mpc.shared.intl_all_colleges
import org.mpc.shared.intl_alternatives
import org.mpc.shared.intl_apply_at
import org.mpc.shared.intl_chinese
import org.mpc.shared.intl_chinese_requirements
import org.mpc.shared.intl_cohort
import org.mpc.shared.intl_degree_bachelor
import org.mpc.shared.intl_degree_master
import org.mpc.shared.intl_degree_phd
import org.mpc.shared.intl_earlier_year
import org.mpc.shared.intl_english_tests
import org.mpc.shared.intl_failed
import org.mpc.shared.intl_international_students
import org.mpc.shared.intl_later_year
import org.mpc.shared.intl_needs_backend
import org.mpc.shared.intl_no_rule
import org.mpc.shared.intl_no_rule_found
import org.mpc.shared.intl_not_checked
import org.mpc.shared.intl_not_graduation_requirements
import org.mpc.shared.intl_notes
import org.mpc.shared.intl_open_questions
import org.mpc.shared.intl_other_languages
import org.mpc.shared.intl_procedure
import org.mpc.shared.intl_retry
import org.mpc.shared.intl_show_chinese
import org.mpc.shared.intl_show_english
import org.mpc.shared.intl_sources
import org.mpc.shared.intl_title
import org.mpc.shared.intl_unofficial_translation
import org.mpc.shared.intl_verified_on
import org.mpc.shared.settings_course_source

/** The screen's own text in the app's language, resolved once so the cards below can share it. */
internal class GraduationLanguageLabels(
    val title: String,
    val showChinese: String,
    val showEnglish: String,
    val allColleges: String,
    val admissionYear: String,
    val earlierYear: String,
    val laterYear: String,
    val englishTests: String,
    val otherLanguages: String,
    val internationalStudents: String,
    val alternatives: String,
    val procedure: String,
    val notes: String,
    val sources: String,
    val noRule: String,
    val noRuleFound: String,
    val notChecked: String,
    val chinese: String,
    val chineseRequirements: String,
    val notGraduationRequirements: String,
    val openQuestions: String,
    val unofficialTranslation: String,
    val failed: String,
    val retry: String,
    val needsBackend: String,
    private val degrees: Map<Degree, String>,
) {
    fun degree(degree: Degree): String = degrees.getValue(degree)
}

@Composable
internal fun graduationLanguageLabels(): GraduationLanguageLabels = GraduationLanguageLabels(
    title = stringResource(MR.strings.intl_title),
    showChinese = stringResource(MR.strings.intl_show_chinese),
    showEnglish = stringResource(MR.strings.intl_show_english),
    allColleges = stringResource(MR.strings.intl_all_colleges),
    admissionYear = stringResource(MR.strings.intl_admission_year),
    earlierYear = stringResource(MR.strings.intl_earlier_year),
    laterYear = stringResource(MR.strings.intl_later_year),
    englishTests = stringResource(MR.strings.intl_english_tests),
    otherLanguages = stringResource(MR.strings.intl_other_languages),
    internationalStudents = stringResource(MR.strings.intl_international_students),
    alternatives = stringResource(MR.strings.intl_alternatives),
    procedure = stringResource(MR.strings.intl_procedure),
    notes = stringResource(MR.strings.intl_notes),
    sources = stringResource(MR.strings.intl_sources),
    noRule = stringResource(MR.strings.intl_no_rule),
    noRuleFound = stringResource(MR.strings.intl_no_rule_found),
    notChecked = stringResource(MR.strings.intl_not_checked),
    chinese = stringResource(MR.strings.intl_chinese),
    chineseRequirements = stringResource(MR.strings.intl_chinese_requirements),
    notGraduationRequirements = stringResource(MR.strings.intl_not_graduation_requirements),
    openQuestions = stringResource(MR.strings.intl_open_questions),
    unofficialTranslation = stringResource(MR.strings.intl_unofficial_translation),
    failed = stringResource(MR.strings.intl_failed),
    retry = stringResource(MR.strings.intl_retry),
    needsBackend = stringResource(MR.strings.intl_needs_backend, stringResource(MR.strings.settings_course_source)),
    degrees = mapOf(
        Degree.BACHELOR to stringResource(MR.strings.intl_degree_bachelor),
        Degree.MASTER to stringResource(MR.strings.intl_degree_master),
        Degree.PHD to stringResource(MR.strings.intl_degree_phd),
    ),
)

/** Admission year [year] of the ROC calendar with its Gregorian year. */
@Composable
internal fun cohortLabel(year: Int): String = stringResource(MR.strings.intl_cohort, year.toString(), (year + ROC_OFFSET).toString())

@Composable
internal fun applyAtLabel(office: String): String = stringResource(MR.strings.intl_apply_at, office)

@Composable
internal fun verifiedOnLabel(date: String): String = stringResource(MR.strings.intl_verified_on, date)

private const val ROC_OFFSET = 1911
