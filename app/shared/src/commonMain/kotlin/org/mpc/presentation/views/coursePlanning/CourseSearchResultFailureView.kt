package org.mpc.presentation.views.coursePlanning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.presentation.state.CourseSearchError
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.shared.MR
import org.mpc.shared.planning_search_error_invalid_semester
import org.mpc.shared.planning_search_error_network
import org.mpc.shared.planning_search_error_unknown
import org.mpc.shared.planning_search_reload

@Composable
fun CourseSearchResultFailureView(
    modifier: Modifier,
    error: CourseSearchError,
    onRetry: () -> Unit = {},
) {
    Box(
        modifier = modifier.padding(MpcSpacing.space6),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(
                    when (error) {
                        CourseSearchError.NETWORK -> MR.strings.planning_search_error_network
                        CourseSearchError.INVALID_SEMESTER -> MR.strings.planning_search_error_invalid_semester
                        CourseSearchError.UNKNOWN -> MR.strings.planning_search_error_unknown
                    },
                ),
                style = MpcTheme.typography.supporting,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(MpcSpacing.space3))
            Button(onClick = onRetry) {
                Text(stringResource(MR.strings.planning_search_reload))
            }
        }
    }
}
