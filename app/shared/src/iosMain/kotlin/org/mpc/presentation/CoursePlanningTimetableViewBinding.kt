package org.mpc.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mpc.bridge.CoursePlanBridge
import org.mpc.presentation.state.CoursePlanDraftStore
import org.mpc.presentation.views.coursePlanning.CoursePlanningTimetableView

@Composable
fun CoursePlanningTimetableViewBinding(
    planBridge: CoursePlanBridge,
    draftStore: CoursePlanDraftStore,
) {
    val planUiState by draftStore.uiState.collectAsStateWithLifecycle()
    // The retained catalog binding owns planBridge requests and selected-course updates.

    CoursePlanningTimetableView(
        uiState = planUiState,
        modifier = Modifier.fillMaxSize(),
        onCourseClick = { semester, course ->
            planBridge.requestCourseDetails(semester, course)
        },
    )
}
