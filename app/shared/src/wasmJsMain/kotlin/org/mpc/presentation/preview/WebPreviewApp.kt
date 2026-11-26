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
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icerock.moko.resources.desc.StringDesc
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.browser.window
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.mpc.domain.model.AppLanguage
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
    GOV_GUIDES(
        "政府資訊",
        Icons.Filled.LocalLibrary,
        listOf(ComposePreviewScenario.GUIDES_LIVE, ComposePreviewScenario.GUIDES_POPULATED),
    ),
    SERVICE_LEARNING(
        "服務學習",
        Icons.Filled.VolunteerActivism,
        listOf(ComposePreviewScenario.SERVICE_LEARNING_LIVE, ComposePreviewScenario.SERVICE_LEARNING_POPULATED),
    ),
    CREDITS(
        "學分",
        Icons.Filled.Calculate,
        listOf(ComposePreviewScenario.CREDITS_LIVE, ComposePreviewScenario.CREDITS_POPULATED),
    ),
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
    GRADUATION(
        "國際生",
        Icons.Filled.Language,
        listOf(
            ComposePreviewScenario.GRADUATION_LIVE,
            ComposePreviewScenario.GRADUATION_POPULATED,
            ComposePreviewScenario.GRADUATION_LOADING,
            ComposePreviewScenario.GRADUATION_FAILURE,
            ComposePreviewScenario.GRADUATION_NEEDSBACKEND,
        ),
    ),
    ASSISTANT(
        "問答",
        Icons.Filled.QuestionAnswer,
        listOf(
            ComposePreviewScenario.ASSISTANT_LIVE,
            ComposePreviewScenario.ASSISTANT_POPULATED,
            ComposePreviewScenario.ASSISTANT_NOMODEL,
            ComposePreviewScenario.ASSISTANT_FAILURE,
            ComposePreviewScenario.ASSISTANT_NEEDSBACKEND,
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
    val language by remember { PreviewSettings.settings.map { it.language } }.collectAsState(AppLanguage.SYSTEM)
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
                            // moko reads StringDesc.localeType when it resolves a string, and resolves each one once,
                            // so a new language re-keys the scenario to resolve its text again.
                            remember(language) { StringDesc.localeType = language.toLocaleType() }
                            key(language) {
                                ComposePreviewScenarioContent(
                                    scenario = scenario,
                                    language = language,
                                    onLanguageSelected = { selected -> PreviewSettings.settings.update { it.copy(language = selected) } },
                                )
                            }
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

private fun AppLanguage.toLocaleType(): StringDesc.LocaleType = tag.ifEmpty { null }?.let(StringDesc.LocaleType::Custom) ?: StringDesc.LocaleType.System
