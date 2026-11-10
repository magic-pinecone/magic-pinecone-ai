package org.mpc.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.compose.stringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.presentation.state.SourcedRulesUiState
import org.mpc.presentation.viewModel.CreditsViewModel
import org.mpc.shared.MR
import org.mpc.shared.credits_intro
import org.mpc.shared.credits_title

/** NCU's credit rules for international students. */
@Composable
fun CreditsScreen(
    state: SourcedRulesUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SourcedRulesScreen(
        state = state,
        title = stringResource(MR.strings.credits_title),
        intro = stringResource(MR.strings.credits_intro),
        onRetry = onRetry,
        modifier = modifier,
    )
}

@Composable
fun CreditsRoute(modifier: Modifier = Modifier) {
    val viewModel: CreditsViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CreditsScreen(state = state, onRetry = viewModel::refresh, modifier = modifier)
}
