package org.mpc.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.compose.stringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.presentation.viewModel.GovGuidesViewModel
import org.mpc.presentation.viewModel.GraduationLanguageViewModel
import org.mpc.shared.MR
import org.mpc.shared.intl_section_graduation
import org.mpc.shared.intl_section_guides

/** The International tab: graduation language requirements, and the government guides. */
@Composable
fun InternationalScreen(modifier: Modifier = Modifier) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = section) {
            listOf(MR.strings.intl_section_graduation, MR.strings.intl_section_guides).forEachIndexed { index, label ->
                Tab(selected = section == index, onClick = { section = index }, text = { Text(stringResource(label)) })
            }
        }
        if (section == 0) {
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
        } else {
            val viewModel: GovGuidesViewModel = metroViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GovGuidesScreen(state = state, onRetry = viewModel::refresh, modifier = Modifier.fillMaxSize())
        }
    }
}
