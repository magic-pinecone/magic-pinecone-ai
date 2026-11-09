package org.mpc.presentation.views.coursePlanning

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.mpc.domain.model.CourseSummary
import org.mpc.presentation.state.CoursePlanUiState

@Composable
fun CoursePlanningTimetableView(
    uiState: CoursePlanUiState,
    modifier: Modifier,
    onCourseClick: (semester: String, course: CourseSummary) -> Unit = { _, _ -> },
) {
    when (uiState) {
        is CoursePlanUiState.Failure -> {
            CoursePlanningTimetableFailureView(modifier, uiState.cause)
        }

        CoursePlanUiState.Loading -> {
            CoursePlanningTimetableLoadingView(modifier)
        }

        is CoursePlanUiState.Success -> {
            CoursePlanningTimetableSuccessView(
                plan = uiState.plan,
                modifier = modifier,
                onCourseClick = onCourseClick,
            )
        }
    }
}
