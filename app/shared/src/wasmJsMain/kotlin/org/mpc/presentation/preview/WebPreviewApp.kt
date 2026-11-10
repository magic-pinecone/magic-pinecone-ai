package org.mpc.presentation.preview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.browser.window
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.TimetableColorMode
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme

/** A navigation destination and the scenarios it previews, in the order its state chips show them. */
private enum class PreviewScreen(
    val label: String,
    val icon: ImageVector,
    val scenarios: List<ComposePreviewScenario>,
) {
    CATALOG(
        "課程",
        Icons.Filled.Search,
        listOf(
            ComposePreviewScenario.CATALOG_POPULATED,
            ComposePreviewScenario.CATALOG_EMPTY,
            ComposePreviewScenario.CATALOG_LOADING,
            ComposePreviewScenario.CATALOG_FAILURE,
        ),
    ),
    TIMETABLE(
        "課表",
        Icons.Filled.DateRange,
        listOf(
            ComposePreviewScenario.TIMETABLE_POPULATED,
            ComposePreviewScenario.TIMETABLE_EMPTY,
            ComposePreviewScenario.TIMETABLE_LOADING,
            ComposePreviewScenario.TIMETABLE_FAILURE,
        ),
    ),
    COURSE_DETAILS("詳情", Icons.Filled.Info, listOf(ComposePreviewScenario.COURSE_DETAILS)),
    SLOT_PICKER("節次", Icons.Filled.GridView, listOf(ComposePreviewScenario.SLOT_PICKER)),
    PORTAL("Portal", Icons.Filled.Home, listOf(ComposePreviewScenario.PORTAL)),
    CLUBS(
        "社團",
        Icons.Filled.Groups,
        listOf(
            ComposePreviewScenario.CLUBS_LIVE,
            ComposePreviewScenario.CLUBS_POPULATED,
            ComposePreviewScenario.CLUBS_NO_BACKEND,
            ComposePreviewScenario.CLUBS_FAILURE,
        ),
    ),
    SETTINGS("設定", Icons.Filled.Settings, listOf(ComposePreviewScenario.SETTINGS)),
}

private val ComposePreviewScenario.screen: PreviewScreen
    get() = PreviewScreen.entries.first { this in it.scenarios }

private val ComposePreviewScenario.stateLabel: String
    get() = name.substringAfter('_').lowercase()

/**
 * Every [ComposePreviewScenario] behind a navigation suite: a rail on medium and wider windows, a bar on compact
 * ones. The URL hash names the scenario, so `#TIMETABLE_EMPTY` opens that state directly.
 */
@Composable
fun WebPreviewApp() {
    var scenario by remember { mutableStateOf(scenarioFromHash() ?: ComposePreviewScenario.CATALOG_POPULATED) }
    var darkTheme by remember { mutableStateOf(window.matchMedia("(prefers-color-scheme: dark)").matches) }
    LaunchedEffect(scenario) { window.location.hash = scenario.name }

    MpcTheme(
        themeMode = if (darkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT,
        timetableColorMode = TimetableColorMode.CATEGORY,
    ) {
        CompositionLocalProvider(LocalMetroViewModelFactory provides WebPreviewViewModelFactory) {
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    PreviewScreen.entries.forEach { screen ->
                        item(
                            selected = scenario.screen == screen,
                            onClick = { scenario = screen.scenarios.first() },
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.label) },
                        )
                    }
                },
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MpcTheme.colors.background) {
                    Column {
                        PreviewToolbar(
                            scenario = scenario,
                            darkTheme = darkTheme,
                            onScenarioSelected = { scenario = it },
                            onToggleTheme = { darkTheme = !darkTheme },
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            ComposePreviewScenarioContent(scenario)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewToolbar(
    scenario: ComposePreviewScenario,
    darkTheme: Boolean,
    onScenarioSelected: (ComposePreviewScenario) -> Unit,
    onToggleTheme: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MpcSpacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
        ) {
            val states = scenario.screen.scenarios
            if (states.size > 1) {
                states.forEach { state ->
                    FilterChip(
                        selected = state == scenario,
                        onClick = { onScenarioSelected(state) },
                        label = { Text(state.stateLabel) },
                    )
                }
            }
        }
        IconButton(onClick = onToggleTheme) {
            Icon(
                imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = if (darkTheme) "Light theme" else "Dark theme",
            )
        }
    }
}

private fun scenarioFromHash(): ComposePreviewScenario? {
    val name = window.location.hash.removePrefix("#")
    return ComposePreviewScenario.entries.firstOrNull { it.name == name }
}
