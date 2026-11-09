package org.mpc.presentation.state

import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CourseResult
import org.mpc.domain.model.CourseSummary

data class CourseSearchUiState(
    val semester: String,
    val query: String,
    val filters: List<CourseFilter> = emptyList(),
    val suggestions: List<CourseFilter> = emptyList(),
    val filterOptions: CourseFilterOptions = CourseFilterOptions(),
    val result: CourseSearchResultUiState = CourseSearchResultUiState.Loading,
)

data class CourseFilterOptions(
    val teachers: List<String> = emptyList(),
    val departments: List<String> = emptyList(),
    val colleges: List<String> = emptyList(),
    val credits: List<Double> = emptyList(),
)

/** The choices the filter sheet offers for these courses, each sorted and listed once. */
fun List<CourseSummary>.courseFilterOptions() = CourseFilterOptions(
    teachers = flatMap { it.teachers }.distinctNames(),
    departments = map { it.departmentName }.distinctNames(),
    colleges = map { it.collegeName }.distinctNames(),
    credits = map { it.credit }.distinct().sorted(),
)

private fun List<String>.distinctNames() = filter { it.isNotBlank() }.distinct().sorted()

sealed interface CourseSearchResultUiState {
    data object Loading : CourseSearchResultUiState

    data class Success(
        val result: CourseResult,
    ) : CourseSearchResultUiState

    data class Failure(
        val error: CourseSearchError,
    ) : CourseSearchResultUiState
}

enum class CourseSearchError {
    NETWORK,
    INVALID_SEMESTER,
    UNKNOWN,
}
