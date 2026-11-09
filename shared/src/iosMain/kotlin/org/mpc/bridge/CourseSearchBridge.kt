package org.mpc.bridge

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.SlotMatch
import org.mpc.presentation.state.CourseFilterOptions

internal sealed interface CourseSearchAction {
    data class UpdateQuery(
        val semester: String,
        val query: String,
    ) : CourseSearchAction

    data class Submit(
        val semester: String,
        val query: String,
    ) : CourseSearchAction

    data class AddFilter(
        val filter: CourseFilter,
    ) : CourseSearchAction

    data class PickSuggestion(
        val filter: CourseFilter,
    ) : CourseSearchAction

    data class RemoveFilter(
        val filter: CourseFilter,
    ) : CourseSearchAction

    data class SetSchedule(
        val slots: Set<CourseTime>,
        val match: SlotMatch,
    ) : CourseSearchAction

    data object ClearFilters : CourseSearchAction
}

class CourseSearchBridge {
    private val mutableActions = Channel<CourseSearchAction>(Channel.BUFFERED)
    internal val actions = mutableActions.receiveAsFlow()
    private val mutableCatalogSnapshot = MutableStateFlow(CourseCatalogSnapshot())
    val catalogSnapshot: StateFlow<CourseCatalogSnapshot> = mutableCatalogSnapshot.asStateFlow()

    fun updateQuery(
        semester: String,
        query: String,
    ) = send(CourseSearchAction.UpdateQuery(semester, query))

    fun submit(
        semester: String,
        query: String,
    ) = send(CourseSearchAction.Submit(semester, query))

    fun addFilter(filter: CourseFilter) = send(CourseSearchAction.AddFilter(filter))

    fun pickSuggestion(filter: CourseFilter) = send(CourseSearchAction.PickSuggestion(filter))

    fun removeFilter(filter: CourseFilter) = send(CourseSearchAction.RemoveFilter(filter))

    fun setSchedule(
        slots: Set<CourseTime>,
        match: SlotMatch,
    ) = send(CourseSearchAction.SetSchedule(slots, match))

    fun clearFilters() = send(CourseSearchAction.ClearFilters)

    fun updateCatalogSnapshot(snapshot: CourseCatalogSnapshot) {
        mutableCatalogSnapshot.value = snapshot
    }

    private fun send(action: CourseSearchAction) {
        mutableActions.trySend(action)
    }
}

data class CourseCatalogSnapshot(
    val semester: String = "115-1",
    val query: String = "",
    val courses: List<CourseSummary> = emptyList(),
    val filters: List<CourseFilter> = emptyList(),
    val suggestions: List<CourseFilter> = emptyList(),
    val filterOptions: CourseFilterOptions = CourseFilterOptions(),
    val isLoading: Boolean = true,
    val failureMessage: String? = null,
)
