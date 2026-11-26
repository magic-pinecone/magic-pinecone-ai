package org.mpc.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.presentation.model.InternationalPage
import org.mpc.presentation.viewModel.GraduationLanguageViewModel

@Composable
fun InternationalPageScreen(
    page: InternationalPage,
    modifier: Modifier = Modifier,
) {
    when (page) {
        InternationalPage.GRADUATION_LANGUAGE -> GraduationLanguageRoute(modifier = modifier.fillMaxSize())
        InternationalPage.GOV_GUIDES -> GovGuidesRoute(modifier = modifier.fillMaxSize())
        InternationalPage.SERVICE_LEARNING -> ServiceLearningRoute(modifier = modifier.fillMaxSize())
        InternationalPage.CREDITS -> CreditsRoute(modifier = modifier.fillMaxSize())
        InternationalPage.ASK -> AssistantRoute(modifier = modifier.fillMaxSize())
    }
}

@Composable
private fun GraduationLanguageRoute(modifier: Modifier = Modifier) {
    val viewModel: GraduationLanguageViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GraduationLanguageScreen(
        state = state,
        actions = remember(viewModel) {
            GraduationLanguageActions(
                onSelectDegree = viewModel::selectDegree,
                onSelectCollege = viewModel::selectCollege,
                onSelectCohort = viewModel::selectCohort,
                onRetry = viewModel::refresh,
            )
        },
        modifier = modifier,
    )
}
