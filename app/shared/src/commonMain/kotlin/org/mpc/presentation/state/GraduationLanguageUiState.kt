package org.mpc.presentation.state

import org.mpc.domain.model.Degree
import org.mpc.domain.model.GraduationLanguage
import org.mpc.domain.model.GraduationLanguageRule
import org.mpc.domain.model.LocalizedText

sealed interface GraduationLanguageUiState {
    data object Loading : GraduationLanguageUiState

    /** No backend address is set, and this data is only served by a backend. */
    data object NeedsBackend : GraduationLanguageUiState

    data object Failed : GraduationLanguageUiState

    data class Loaded(
        val requirements: GraduationLanguage,
        val filter: GraduationLanguageFilter,
        /** Colleges that have a rule for the selected degree. */
        val colleges: List<LocalizedText>,
        /** The rules matching [filter]. */
        val rules: List<GraduationLanguageRule>,
    ) : GraduationLanguageUiState
}

data class GraduationLanguageFilter(
    val degree: Degree = Degree.BACHELOR,
    /** A college's Chinese name; null shows every college. */
    val college: String? = null,
    /** Admission academic year in the ROC calendar. */
    val cohort: Int = DEFAULT_COHORT,
)

/** The admission academic years (ROC calendar) the filter offers. */
const val FIRST_COHORT = 110
const val LAST_COHORT = 116
private const val DEFAULT_COHORT = 115
