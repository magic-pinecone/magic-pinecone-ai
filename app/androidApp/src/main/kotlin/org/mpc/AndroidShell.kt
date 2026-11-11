package org.mpc

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.mohamedrejeb.calf.ui.web.WebViewNavigator
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.pluralStringResource
import dev.icerock.moko.resources.compose.stringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.calendar.CalendarExportButton
import org.mpc.calendar.ClassCalendar
import org.mpc.di.AppGraph
import org.mpc.domain.model.AppLanguage
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.session.CampusSites
import org.mpc.domain.session.SitePath
import org.mpc.domain.session.browserEntry
import org.mpc.navigation.AndroidNavigator
import org.mpc.navigation.AppRoot
import org.mpc.navigation.ClubsRoute
import org.mpc.navigation.CoursePlanningRoot
import org.mpc.navigation.EeclassBulletinRoute
import org.mpc.navigation.EeclassCategoryRoute
import org.mpc.navigation.EeclassCourseRoute
import org.mpc.navigation.HomeRoot
import org.mpc.navigation.InternationalRoute
import org.mpc.navigation.PortalRoot
import org.mpc.navigation.PortalWebRoute
import org.mpc.navigation.SettingsRoute
import org.mpc.navigation.TopLevelRoute
import org.mpc.navigation.rememberAndroidNavigationState
import org.mpc.navigation.scene.BottomSheetSceneStrategy
import org.mpc.presentation.ClubsScreen
import org.mpc.presentation.CoursePlanningScreen
import org.mpc.presentation.CoursePlanningView
import org.mpc.presentation.EeclassBulletinScreen
import org.mpc.presentation.EeclassCategoryScreen
import org.mpc.presentation.EeclassCourseScreen
import org.mpc.presentation.HomeScreen
import org.mpc.presentation.InternationalPageScreen
import org.mpc.presentation.PortalScreen
import org.mpc.presentation.PortalWebScreen
import org.mpc.presentation.SettingsScreen
import org.mpc.presentation.rememberOnDeviceTranslator
import org.mpc.presentation.state.CoursePlanUiState
import org.mpc.presentation.theme.AndroidMpcTheme
import org.mpc.presentation.translation.LocalTextTranslator
import org.mpc.presentation.viewModel.AppSettingsViewModel
import org.mpc.presentation.viewModel.BackendEndpointViewModel
import org.mpc.presentation.viewModel.CoursePlanViewModel
import org.mpc.shared.MR
import org.mpc.shared.clubs_title
import org.mpc.shared.shell_app_title
import org.mpc.shared.shell_back
import org.mpc.shared.shell_calendar_export_failed
import org.mpc.shared.shell_calendar_permission_denied
import org.mpc.shared.shell_close_settings
import org.mpc.shared.shell_compact_course_cards
import org.mpc.shared.shell_display_options
import org.mpc.shared.shell_enabled
import org.mpc.shared.shell_more_options
import org.mpc.shared.shell_official_course_page
import org.mpc.shared.shell_open_in_browser
import org.mpc.shared.shell_plan_save_failed
import org.mpc.shared.shell_plan_saved
import org.mpc.shared.shell_previous_page
import org.mpc.shared.shell_save
import org.mpc.shared.shell_selected_course_count
import org.mpc.shared.shell_settings
import org.mpc.shared.shell_share
import org.mpc.shared.shell_tab_course_selection
import org.mpc.shared.shell_tab_home
import org.mpc.shared.shell_tab_portal
import org.mpc.shared.shell_view_selected_courses

@Composable
fun AndroidAppShell(appGraph: AppGraph) {
    ProvideAppDependencies(appGraph) {
        AndroidAppContent(appGraph)
    }
}

@Composable
private fun AndroidAppContent(appGraph: AppGraph) {
    val settingsViewModel: AppSettingsViewModel = metroViewModel()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val appBackStack = rememberNavBackStack(AppRoot)
    val isExpanded =
        currentWindowAdaptiveInfo()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val dialogSceneStrategy = remember { DialogSceneStrategy<NavKey>() }

    // Keep entry metadata independent of the window size. Navigation 3 caches metadata by the
    // back-stack key, while the scene-strategy list is re-evaluated when the window changes.
    val entryProvider =
        entryProvider<NavKey> {
            entry<AppRoot> {
                AndroidPrimaryNavigation(
                    appGraph = appGraph,
                    onOpenSettings = {
                        if (SettingsRoute !in appBackStack) {
                            appBackStack.add(SettingsRoute)
                        }
                    },
                )
            }
            entry<SettingsRoute>(
                metadata =
                DialogSceneStrategy.dialog(
                    DialogProperties(windowTitle = stringResource(MR.strings.shell_settings)),
                ),
            ) {
                SettingsDialog(
                    settingsViewModel = settingsViewModel,
                    onClose = { appBackStack.removeLastOrNull() },
                )
            }
        }

    AndroidMpcTheme(themeMode = settings.themeMode, timetableColorMode = settings.timetableColorMode) {
        CompositionLocalProvider(LocalTextTranslator provides rememberOnDeviceTranslator()) {
            NavDisplay(
                backStack = appBackStack,
                onBack = { appBackStack.removeLastOrNull() },
                entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                sceneStrategies =
                if (isExpanded) {
                    listOf(dialogSceneStrategy)
                } else {
                    emptyList()
                },
                entryProvider = entryProvider,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AndroidPrimaryNavigation(
    appGraph: AppGraph,
    onOpenSettings: () -> Unit,
) {
    val navigationState = rememberAndroidNavigationState()
    val navigator = remember(navigationState) { AndroidNavigator(navigationState) }
    val uriHandler = LocalUriHandler.current
    val planViewModel: CoursePlanViewModel = metroViewModel()
    val planUiState by planViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val bottomSheetSceneStrategy = remember { BottomSheetSceneStrategy<NavKey>() }
    val context = LocalContext.current
    val displayPreferences = remember(context) {
        context.getSharedPreferences("course_catalog_display", android.content.Context.MODE_PRIVATE)
    }
    var isCompactCatalog by rememberSaveable {
        mutableStateOf(displayPreferences.getBoolean("compact", false))
    }
    val planSavedMessage = stringResource(MR.strings.shell_plan_saved)
    val planSaveFailedMessage = stringResource(MR.strings.shell_plan_save_failed)
    val calendarExportFailedMessage = stringResource(MR.strings.shell_calendar_export_failed)
    val calendarPermissionDeniedMessage = stringResource(MR.strings.shell_calendar_permission_denied)
    val officialCoursePageTitle = stringResource(MR.strings.shell_official_course_page)
    LaunchedEffect(planViewModel) {
        planViewModel.saveResult.collect { saved ->
            snackbarHostState.showSnackbar(if (saved) planSavedMessage else planSaveFailedMessage)
        }
    }
    var selectedCoursePlanningView by rememberSaveable { mutableStateOf(CoursePlanningView.CATALOG) }
    var selectedCourses by remember { mutableStateOf(emptyList<CourseSummary>()) }
    var isShowingSelectedCourses by rememberSaveable { mutableStateOf(false) }
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isExpanded =
        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) ||
            adaptiveInfo.windowPosture.isTabletop
    val openEeclassPage = { title: String, path: String ->
        // The URL is resolved on open because it depends on the session at that moment.
        val url = appGraph.campusSessions.browserEntry(CampusSites.Eeclass, SitePath.of(path))
        navigator.navigate(PortalWebRoute(title = title, url = url))
    }
    // The top bar goes back within the open page, so each page's navigator lives here.
    val webNavigatorScope = rememberCoroutineScope()
    val webNavigators = remember { mutableMapOf<PortalWebRoute, WebViewNavigator>() }
    val entryProvider =
        entryProvider<NavKey> {
            entry<HomeRoot> {
                HomeScreen(
                    onOpenCourse = { number, name -> navigator.navigate(EeclassCourseRoute(number, name)) },
                    onOpenInternational = { page -> navigator.navigate(InternationalRoute(page)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<EeclassCourseRoute> { route ->
                EeclassCourseScreen(
                    course = route.number,
                    onOpenCategory = { navigator.navigate(EeclassCategoryRoute(route.number, it)) },
                    onOpenBulletin = { number, title -> navigator.navigate(EeclassBulletinRoute(route.number, number, title)) },
                    onOpenPage = openEeclassPage,
                )
            }
            entry<EeclassCategoryRoute> { route ->
                EeclassCategoryScreen(
                    course = route.number,
                    category = route.category,
                    onOpenBulletin = { number, title -> navigator.navigate(EeclassBulletinRoute(route.number, number, title)) },
                    onOpenPage = openEeclassPage,
                )
            }
            entry<EeclassBulletinRoute>(metadata = BottomSheetSceneStrategy.bottomSheet()) { route ->
                EeclassBulletinScreen(
                    course = route.course,
                    number = route.number,
                    title = route.title,
                    onOpenPage = openEeclassPage,
                    onOpenLink = { url ->
                        // Links to ee-class need its session, so they stay in the in-app browser.
                        appGraph.campusSessions.browserEntry(url)
                            ?.let { navigator.navigate(PortalWebRoute(title = route.title, url = it)) }
                            ?: uriHandler.openUri(url)
                    },
                )
            }
            entry<PortalRoot> {
                PortalScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenClubs = { navigator.navigate(ClubsRoute) },
                    onOpenDestination = { destination ->
                        navigator.navigate(
                            PortalWebRoute(
                                title = destination.title,
                                url = destination.url,
                            ),
                        )
                    },
                )
            }
            entry<ClubsRoute> {
                ClubsScreen(modifier = Modifier.fillMaxSize())
            }
            entry<InternationalRoute> { route ->
                InternationalPageScreen(route.page, modifier = Modifier.fillMaxSize())
            }
            entry<PortalWebRoute> { route ->
                PortalWebScreen(
                    url = route.url,
                    navigator = webNavigators.getOrPut(route) { WebViewNavigator(webNavigatorScope) },
                )
            }
            entry<CoursePlanningRoot> {
                CoursePlanningScreen(
                    modifier = Modifier.fillMaxSize(),
                    planViewModel = planViewModel,
                    isCompactCatalog = isCompactCatalog,
                    selectedView = selectedCoursePlanningView,
                    onSelectedViewChange = { view -> selectedCoursePlanningView = view },
                    courseRepository = appGraph.courseRepository,
                    onSelectedCoursesChange = { courses -> selectedCourses = courses },
                    isShowingSelectedCourses = isShowingSelectedCourses,
                    onDismissSelectedCourses = { isShowingSelectedCourses = false },
                    onOpenWebPage = { url ->
                        // The WebView blocks cleartext traffic, so plain http pages open in the browser.
                        if (url.startsWith("https://")) {
                            navigator.navigate(PortalWebRoute(title = officialCoursePageTitle, url = url))
                        } else {
                            uriHandler.openUri(url)
                        }
                    },
                )
            }
        }

    NavigationSuiteScaffold(
        navigationItems = {
            topLevelNavigationItems.forEach { item ->
                NavigationSuiteItem(
                    selected = navigationState.selectedTopLevelRoute == item.route,
                    onClick = { navigator.navigate(item.route) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = stringResource(item.label),
                        )
                    },
                    label = { Text(stringResource(item.label)) },
                )
            }
        },
    ) {
        val currentPortalWebRoute = navigationState.currentBackStack.lastOrNull() as? PortalWebRoute
        val currentWebNavigator = currentPortalWebRoute?.let { webNavigators[it] }
        val topBarState =
            PrimaryTopBarState(
                currentPortalWebRoute = currentPortalWebRoute,
                pushedTitle = navigationState.currentBackStack.lastOrNull { it !is EeclassBulletinRoute }?.pushedTitle(),
                canGoBackInPage = currentWebNavigator?.canGoBack == true,
                selectedTopLevelRoute = navigationState.selectedTopLevelRoute,
                isExpanded = isExpanded,
                selectedCoursePlanningView = selectedCoursePlanningView,
                selectedCourseCount = selectedCourses.size,
                canSavePlan = planUiState is CoursePlanUiState.Success,
                isCompactCatalog = isCompactCatalog,
            )

        Scaffold(
            topBar = {
                AndroidPrimaryTopBar(
                    state = topBarState,
                    coursePlanActions = CoursePlanTopBarActions(
                        onSavePlan = planViewModel::savePlan,
                        onExportToCalendar = {
                            val semester = (planUiState as? CoursePlanUiState.Success)?.plan?.semester
                            val message = semester?.let {
                                runCatching { ClassCalendar.exportSavedPlan(context, appGraph, it) }
                                    .fold({ count -> ClassCalendar.resultMessage(context, count) }) { calendarExportFailedMessage }
                            }
                            message?.let { snackbarHostState.showSnackbar(it) }
                        },
                        onCalendarPermissionDenied = {
                            snackbarHostState.showSnackbar(calendarPermissionDeniedMessage)
                        },
                        onToggleCompactCatalog = {
                            isCompactCatalog = !isCompactCatalog
                            displayPreferences.edit().putBoolean("compact", isCompactCatalog).apply()
                        },
                        onOpenSelectedCourses = { isShowingSelectedCourses = true },
                    ),
                    onBack = { navigator.goBack() },
                    onBackInPage = { currentWebNavigator?.navigateBack() },
                    onOpenSettings = onOpenSettings,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavDisplay(
                    entries = navigationState.toDecoratedEntries(entryProvider),
                    onBack = { navigator.goBack() },
                    sceneStrategies = listOf(bottomSheetSceneStrategy),
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AndroidPrimaryTopBar(
    state: PrimaryTopBarState,
    coursePlanActions: CoursePlanTopBarActions,
    onBack: () -> Unit,
    onBackInPage: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    TopAppBar(
        title = { Text(state.title()) },
        navigationIcon = {
            if (state.currentPortalWebRoute != null || state.pushedTitle != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(MR.strings.shell_back),
                    )
                }
            }
        },
        actions = {
            if (state.currentPortalWebRoute != null) {
                if (state.canGoBackInPage) {
                    IconButton(onClick = onBackInPage) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(MR.strings.shell_previous_page),
                        )
                    }
                }
                WebPageMenu(url = state.currentPortalWebRoute.url)
            } else if (state.pushedTitle == null) {
                if (state.showsCoursePlanAction) {
                    TextButton(onClick = coursePlanActions.onSavePlan, enabled = state.canSavePlan) {
                        Text(stringResource(MR.strings.shell_save))
                    }
                    BadgedBox(
                        badge = {
                            if (state.selectedCourseCount > 0) {
                                Badge(modifier = Modifier.clearAndSetSemantics {}) {
                                    Text(
                                        state.selectedCourseCount
                                            .coerceAtMost(MAX_COURSE_COUNT_BADGE)
                                            .toString(),
                                    )
                                }
                            }
                        },
                    ) {
                        val viewSelectedCoursesDescription = stringResource(MR.strings.shell_view_selected_courses)
                        val selectedCourseCountDescription =
                            pluralStringResource(
                                MR.plurals.shell_selected_course_count,
                                state.selectedCourseCount,
                                state.selectedCourseCount,
                            )
                        FilledTonalIconButton(
                            onClick = coursePlanActions.onOpenSelectedCourses,
                            modifier =
                            Modifier.semantics {
                                contentDescription = viewSelectedCoursesDescription
                                stateDescription = selectedCourseCountDescription
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                            )
                        }
                    }
                }
                if (state.showsCoursePlanAction) CoursePlanningViewActions(state, coursePlanActions)
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(MR.strings.shell_settings),
                    )
                }
            }
        },
    )
}

/** The actions that belong to whichever Course Planning view is showing. */
@Composable
private fun CoursePlanningViewActions(
    state: PrimaryTopBarState,
    coursePlanActions: CoursePlanTopBarActions,
) {
    if (state.isExpanded || state.selectedCoursePlanningView == CoursePlanningView.TIMETABLE) {
        CalendarExportButton(
            enabled = state.canSavePlan,
            onExport = coursePlanActions.onExportToCalendar,
            onPermissionDenied = coursePlanActions.onCalendarPermissionDenied,
        )
    }
    if (state.isExpanded || state.selectedCoursePlanningView == CoursePlanningView.CATALOG) {
        var isShowingDisplayOptions by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { isShowingDisplayOptions = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(MR.strings.shell_display_options))
            }
            DropdownMenu(
                expanded = isShowingDisplayOptions,
                onDismissRequest = { isShowingDisplayOptions = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.shell_compact_course_cards)) },
                    leadingIcon = {
                        if (state.isCompactCatalog) Icon(Icons.Default.Check, contentDescription = stringResource(MR.strings.shell_enabled))
                    },
                    onClick = {
                        coursePlanActions.onToggleCompactCatalog()
                        isShowingDisplayOptions = false
                    },
                )
            }
        }
    }
}

/** Shares or opens the page the browser was opened with; later pages can carry one-time sign-in URLs. */
@Composable
private fun WebPageMenu(url: String) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var isShowingMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isShowingMenu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(MR.strings.shell_more_options))
        }
        DropdownMenu(
            expanded = isShowingMenu,
            onDismissRequest = { isShowingMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.shell_share)) },
                onClick = {
                    isShowingMenu = false
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                    context.startActivity(Intent.createChooser(send, null))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.shell_open_in_browser)) },
                onClick = {
                    isShowingMenu = false
                    uriHandler.openUri(url)
                },
            )
        }
    }
}

private data class CoursePlanTopBarActions(
    val onSavePlan: () -> Unit,
    val onExportToCalendar: suspend () -> Unit,
    val onCalendarPermissionDenied: suspend () -> Unit,
    val onToggleCompactCatalog: () -> Unit,
    val onOpenSelectedCourses: () -> Unit,
)

private data class PrimaryTopBarState(
    val currentPortalWebRoute: PortalWebRoute?,
    /** The title of the pushed ee-class screen showing, or null on a top-level screen. */
    val pushedTitle: String?,
    val canGoBackInPage: Boolean,
    val selectedTopLevelRoute: NavKey,
    val isExpanded: Boolean,
    val selectedCoursePlanningView: CoursePlanningView,
    val selectedCourseCount: Int,
    val canSavePlan: Boolean,
    val isCompactCatalog: Boolean,
) {
    @Composable
    fun title(): String = when {
        currentPortalWebRoute != null -> currentPortalWebRoute.title

        pushedTitle != null -> pushedTitle

        selectedTopLevelRoute == CoursePlanningRoot && isExpanded ->
            stringResource(MR.strings.shell_tab_course_selection)

        selectedTopLevelRoute == CoursePlanningRoot -> stringResource(selectedCoursePlanningView.title)

        selectedTopLevelRoute == HomeRoot -> stringResource(MR.strings.shell_tab_home)

        selectedTopLevelRoute == PortalRoot -> stringResource(MR.strings.shell_tab_portal)

        else -> stringResource(MR.strings.shell_app_title)
    }

    val showsCoursePlanAction: Boolean
        get() =
            selectedTopLevelRoute == CoursePlanningRoot
}

@Composable
private fun NavKey.pushedTitle(): String? = when (this) {
    is ClubsRoute -> stringResource(MR.strings.clubs_title)
    is InternationalRoute -> stringResource(page.title)
    is EeclassCourseRoute -> name
    is EeclassCategoryRoute -> stringResource(category.titleResource)
    else -> null
}

private const val MAX_COURSE_COUNT_BADGE = 99

@Composable
private fun SettingsDialog(
    settingsViewModel: AppSettingsViewModel,
    onClose: () -> Unit,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val settingsWriteFailed by settingsViewModel.settingsWriteFailed.collectAsStateWithLifecycle()
    val backendEndpointViewModel: BackendEndpointViewModel = metroViewModel()
    val backendEndpoint by backendEndpointViewModel.uiState.collectAsStateWithLifecycle()
    val isExpanded =
        currentWindowAdaptiveInfo()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val surfaceModifier =
        if (isExpanded) {
            Modifier
                .widthIn(max = 560.dp)
                .clip(MaterialTheme.shapes.extraLarge)
        } else {
            Modifier.fillMaxSize()
        }

    Surface(modifier = surfaceModifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(MR.strings.shell_settings)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(MR.strings.shell_close_settings),
                        )
                    }
                },
            )
            SettingsScreen(
                settings = settings,
                settingsWriteFailed = settingsWriteFailed,
                modifier = Modifier.fillMaxSize(),
                onTimetableColorModeSelected = settingsViewModel::setTimetableColorMode,
                onThemeModeSelected = settingsViewModel::setThemeMode,
                onPortalAuthenticationModeSelected = settingsViewModel::setPortalAuthenticationMode,
                onLanguageSelected = { language ->
                    val requestedTags = androidx.core.os.LocaleListCompat.forLanguageTags(language.tag).toLanguageTags()
                    val currentTags = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
                    if (requestedTags == currentTags) {
                        settingsViewModel.setLanguage(language)
                    } else {
                        setAndroidAppLanguage(language)
                    }
                },
                backendEndpoint = backendEndpoint,
                onUseCourseSource = backendEndpointViewModel::useSource,
            )
        }
    }
}

private data class TopLevelNavigationItem(
    val route: TopLevelRoute,
    val label: StringResource,
    val icon: ImageVector,
)

private val topLevelNavigationItems =
    listOf(
        TopLevelNavigationItem(HomeRoot, MR.strings.shell_tab_home, Icons.Default.Home),
        TopLevelNavigationItem(CoursePlanningRoot, MR.strings.shell_tab_course_selection, Icons.Default.DateRange),
        TopLevelNavigationItem(PortalRoot, MR.strings.shell_tab_portal, Icons.Default.AccountCircle),
    )
