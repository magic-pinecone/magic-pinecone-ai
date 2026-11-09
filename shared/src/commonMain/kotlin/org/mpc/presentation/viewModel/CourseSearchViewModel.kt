package org.mpc.presentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CourseQuery
import org.mpc.domain.model.CourseResult
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.SlotMatch
import org.mpc.domain.repository.CourseRepository
import org.mpc.domain.search.searchCourses
import org.mpc.domain.search.suggestCourseFilters
import org.mpc.presentation.state.CourseSearchError
import org.mpc.presentation.state.CourseSearchResultUiState
import org.mpc.presentation.state.CourseSearchUiState
import org.mpc.presentation.state.courseFilterOptions

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class CourseSearchViewModel(
    private val courseRepository: CourseRepository,
) : ViewModel() {
    // TODO: update semester so it's not hard coded
    private val _uiState: MutableStateFlow<CourseSearchUiState> =
        MutableStateFlow(
            CourseSearchUiState(semester = "115-1", query = ""),
        )

    val uiState = _uiState.asStateFlow()

    private var catalog: CourseResult? = null
    private var catalogJob: Job? = null

    init {
        onSearch()
    }

    fun updateQuery(
        semester: String,
        query: String,
    ) {
        _uiState.update {
            it.copy(semester = semester, query = query).withSuggestions()
        }
    }

    fun addFilter(filter: CourseFilter) {
        updateFilters { filters -> if (filter in filters) filters else filters + filter }
    }

    fun pickSuggestion(filter: CourseFilter) {
        _uiState.update { it.copy(query = "") }
        addFilter(filter)
    }

    fun removeFilter(filter: CourseFilter) {
        updateFilters { filters -> filters - filter }
    }

    fun clearFilters() {
        updateFilters { emptyList() }
    }

    fun setSchedule(
        slots: Set<CourseTime>,
        match: SlotMatch,
    ) {
        updateFilters { filters ->
            val position = filters.indexOfFirst { it is CourseFilter.Schedule }
            val others = filters.filterNot { it is CourseFilter.Schedule }
            when {
                slots.isEmpty() -> others
                position < 0 -> others + CourseFilter.Schedule(slots, match)
                else -> others.toMutableList().apply { add(position, CourseFilter.Schedule(slots, match)) }
            }
        }
    }

    fun onSearch() {
        val state = uiState.value
        // TODO: do proper validation
        if (state.semester != "115-1") {
            _uiState.update {
                it.copy(result = CourseSearchResultUiState.Failure(CourseSearchError.INVALID_SEMESTER))
            }
            return
        }

        val query = CourseQuery(semester = state.semester, keyword = state.query, filters = state.filters.toSet())
        val loaded = catalog?.takeIf { it.semester == query.semester }
        if (loaded != null) {
            catalogJob?.cancel()
            showResult(loaded, query)
            return
        }

        _uiState.update {
            it.copy(result = CourseSearchResultUiState.Loading)
        }
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            runCatching {
                courseRepository.fetchAllCourses(query.semester)
            }.onSuccess { result ->
                catalog = result
                _uiState.update {
                    it.copy(filterOptions = result.courses.courseFilterOptions()).withSuggestions()
                }
                showResult(result, query)
            }.onFailure(::acceptSearchFailure)
        }
    }

    private fun updateFilters(transform: (List<CourseFilter>) -> List<CourseFilter>) {
        _uiState.update {
            it.copy(filters = transform(it.filters)).withSuggestions()
        }
        onSearch()
    }

    private fun CourseSearchUiState.withSuggestions(): CourseSearchUiState = copy(
        suggestions = suggestCourseFilters(catalog?.courses.orEmpty(), query, active = filters.toSet()),
    )

    private fun showResult(
        catalog: CourseResult,
        query: CourseQuery,
    ) {
        val matches = catalog.copy(courses = searchCourses(catalog.courses, query))
        _uiState.update {
            it.copy(result = CourseSearchResultUiState.Success(matches))
        }
    }

    private fun acceptSearchFailure(cause: Throwable) {
        cause.rethrowIfCancellationOrFatal()
        Logger.i(cause.toString())
        _uiState.update {
            it.copy(
                result = CourseSearchResultUiState.Failure(CourseSearchError.UNKNOWN),
            )
        }
    }
}
