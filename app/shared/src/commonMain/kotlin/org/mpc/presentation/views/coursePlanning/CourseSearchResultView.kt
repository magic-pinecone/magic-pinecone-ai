package org.mpc.presentation.views.coursePlanning

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.presentation.state.CourseSearchResultUiState

@Composable
fun CourseSearchResultView(
    modifier: Modifier = Modifier,
    uiState: CourseSearchResultUiState,
    selectedCourseSerialNumbers: Set<CourseSerialNo>,
    onCourseClick: (CourseSummary) -> Unit = {},
    onToggleCourse: (CourseSummary) -> Unit,
    isCompact: Boolean = false,
    canEditPlan: Boolean = true,
    onRetry: () -> Unit = {},
) {
    when (uiState) {
        is CourseSearchResultUiState.Success -> {
            CourseSearchResultSuccessView(
                modifier = modifier,
                courseResult = uiState.result,
                selectedCourseSerialNumbers = selectedCourseSerialNumbers,
                onCourseClick = onCourseClick,
                onCourseToggle = onToggleCourse,
                isCompact = isCompact,
                canEditPlan = canEditPlan,
            )
        }

        is CourseSearchResultUiState.Failure -> {
            CourseSearchResultFailureView(
                modifier = modifier,
                error = uiState.error,
                onRetry = onRetry,
            )
        }

        CourseSearchResultUiState.Loading -> {
            CourseSearchResultLoadingView(modifier)
        }
    }
}
