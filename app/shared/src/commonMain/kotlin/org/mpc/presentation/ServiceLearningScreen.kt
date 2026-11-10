package org.mpc.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.compose.stringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.presentation.state.SourcedRulesUiState
import org.mpc.presentation.viewModel.ServiceLearningViewModel
import org.mpc.shared.MR
import org.mpc.shared.service_learning_intro
import org.mpc.shared.service_learning_title

/** NCU's service-learning requirement for international students. */
@Composable
fun ServiceLearningScreen(
    state: SourcedRulesUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SourcedRulesScreen(
        state = state,
        title = stringResource(MR.strings.service_learning_title),
        intro = stringResource(MR.strings.service_learning_intro),
        onRetry = onRetry,
        modifier = modifier,
    )
}

@Composable
fun ServiceLearningRoute(modifier: Modifier = Modifier) {
    val viewModel: ServiceLearningViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ServiceLearningScreen(state = state, onRetry = viewModel::refresh, modifier = modifier)
}
