package org.mpc.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            failureMessage = when (result.error) {
                CourseSearchError.NETWORK -> "目前無法連線，請確認網路狀態後再試一次。"
                CourseSearchError.INVALID_SEMESTER -> "目前不支援這個學期的課程查詢。"
                CourseSearchError.UNKNOWN -> "課程資料載入失敗，請稍後再試。"
            },
        )
    }
}
