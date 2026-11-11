package org.mpc.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.compose.stringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.presentation.viewModel.GraduationLanguageViewModel
import org.mpc.shared.MR
import org.mpc.shared.intl_section_ask
import org.mpc.shared.intl_section_credits
import org.mpc.shared.intl_section_graduation
import org.mpc.shared.intl_section_guides
import org.mpc.shared.intl_section_service_learning

/** Where Credits sits in the section row; the sections before it are matched by their small indexes. */
private const val CREDITS_SECTION = 3

/** The International tab: graduation language requirements, the government guides, service learning, credits and the assistant. */
@Composable
fun InternationalScreen(modifier: Modifier = Modifier) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = section, edgePadding = 0.dp) {
            listOf(
                MR.strings.intl_section_graduation,
                MR.strings.intl_section_guides,
                MR.strings.intl_section_service_learning,
                MR.strings.intl_section_credits,
                MR.strings.intl_section_ask,
            ).forEachIndexed { index, label ->
                Tab(selected = section == index, onClick = { section = index }, text = { Text(stringResource(label)) })
            }
        }
        when (section) {
            0 -> {
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
                    modifier = Modifier.fillMaxSize(),
                )
            }

            1 -> GovGuidesRoute(modifier = Modifier.fillMaxSize())

            2 -> ServiceLearningRoute(modifier = Modifier.fillMaxSize())

            CREDITS_SECTION -> CreditsRoute(modifier = Modifier.fillMaxSize())

            else -> AssistantRoute(modifier = Modifier.fillMaxSize())
        }
    }
}
