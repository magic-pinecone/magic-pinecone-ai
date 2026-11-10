package org.mpc.presentation.views.coursePlanning

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.shared.MR
import org.mpc.shared.planning_timetable_error
import org.mpc.shared.planning_timetable_unknown_error

@Composable
fun CoursePlanningTimetableFailureView(
    modifier: Modifier,
    cause: Throwable,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(
                MR.strings.planning_timetable_error,
                cause.message ?: stringResource(MR.strings.planning_timetable_unknown_error),
            ),
        )
    }
}
