package org.mpc.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.StringDesc
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.bridge.CourseCatalogSnapshot
import org.mpc.bridge.CoursePlanBridge
import org.mpc.bridge.CourseSearchAction
import org.mpc.bridge.CourseSearchBridge
import org.mpc.presentation.state.CoursePlanUiState
import org.mpc.presentation.state.CourseSearchError
import org.mpc.presentation.state.CourseSearchResultUiState
import org.mpc.presentation.state.CourseSearchUiState
import org.mpc.presentation.viewModel.CoursePlanViewModel
import org.mpc.presentation.viewModel.CourseSearchViewModel
import org.mpc.shared.MR
import org.mpc.shared.planning_search_error_invalid_semester
import org.mpc.shared.planning_search_error_network
import org.mpc.shared.planning_search_error_unknown

@Composable
fun CourseCatalogViewBinding(
    bridge: CourseSearchBridge,
    planBridge: CoursePlanBridge,
) {
    val searchViewModel: CourseSearchViewModel = metroViewModel()
    val planViewModel: CoursePlanViewModel = metroViewModel()

    val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val planUiState by planViewModel.uiState.collectAsStateWithLifecycle()

    val selectedCourses =
        (planUiState as? CoursePlanUiState.Success)
            ?.plan
            ?.selectedCourses
            ?.values
            ?.sortedBy { course -> course.title }
            .orEmpty()
    val catalogSnapshot = searchUiState.toCatalogSnapshot()

    SideEffect {
        planBridge.updateSelectedCourses(selectedCourses, canEditPlan = planUiState is CoursePlanUiState.Success)
        bridge.updateCatalogSnapshot(catalogSnapshot)
    }

    LaunchedEffect(bridge, searchViewModel) {
        bridge.actions.collect { action ->
            when (action) {
                is CourseSearchAction.UpdateQuery -> searchViewModel.updateQuery(action.semester, action.query)

                is CourseSearchAction.Submit -> {
                    searchViewModel.updateQuery(action.semester, action.query)
                    searchViewModel.onSearch()
                }

                is CourseSearchAction.AddFilter -> searchViewModel.addFilter(action.filter)

                is CourseSearchAction.PickSuggestion -> searchViewModel.pickSuggestion(action.filter)

                is CourseSearchAction.RemoveFilter -> searchViewModel.removeFilter(action.filter)

                is CourseSearchAction.SetSchedule -> searchViewModel.setSchedule(action.slots, action.match)

                CourseSearchAction.ClearFilters -> searchViewModel.clearFilters()
            }
        }
    }

    LaunchedEffect(planBridge, planViewModel) {
        planBridge.saveRequests.collect {
            planViewModel.savePlan()
        }
    }

    LaunchedEffect(planBridge, planViewModel) {
        planBridge.toggleRequests.collect { course ->
            planViewModel.toggleCourse(course)
        }
    }
}

private fun CourseSearchUiState.toCatalogSnapshot(): CourseCatalogSnapshot {
    val base = CourseCatalogSnapshot(
        semester = semester,
        query = query,
        filters = filters,
        suggestions = suggestions,
        filterOptions = filterOptions,
    )
    return when (result) {
        CourseSearchResultUiState.Loading -> base

        is CourseSearchResultUiState.Success -> base.copy(courses = result.result.courses, isLoading = false)

        is CourseSearchResultUiState.Failure -> base.copy(
            isLoading = false,
            failureMessage = StringDesc.Resource(
                when (result.error) {
                    CourseSearchError.NETWORK -> MR.strings.planning_search_error_network
                    CourseSearchError.INVALID_SEMESTER -> MR.strings.planning_search_error_invalid_semester
                    CourseSearchError.UNKNOWN -> MR.strings.planning_search_error_unknown
                },
            ).localized(),
        )
    }
}
