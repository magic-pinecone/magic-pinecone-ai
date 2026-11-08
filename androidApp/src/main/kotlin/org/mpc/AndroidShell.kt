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
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.di.AppGraph
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.session.CampusSites
import org.mpc.domain.session.SitePath
import org.mpc.domain.session.browserEntry
import org.mpc.navigation.AndroidNavigator
import org.mpc.navigation.AppRoot
import org.mpc.navigation.CoursePlanningRoot
import org.mpc.navigation.EeclassBulletinRoute
import org.mpc.navigation.EeclassCategoryRoute
import org.mpc.navigation.EeclassCourseRoute
import org.mpc.navigation.HomeRoot
import org.mpc.navigation.PortalRoot
import org.mpc.navigation.PortalWebRoute
import org.mpc.navigation.SettingsRoute
import org.mpc.navigation.TopLevelRoute
import org.mpc.navigation.rememberAndroidNavigationState
import org.mpc.navigation.scene.BottomSheetSceneStrategy
import org.mpc.presentation.CoursePlanningScreen
import org.mpc.presentation.CoursePlanningView
import org.mpc.presentation.EeclassBulletinScreen
import org.mpc.presentation.EeclassCategoryScreen
import org.mpc.presentation.EeclassCourseScreen
import org.mpc.presentation.EeclassHomeScreen
import org.mpc.presentation.PortalScreen
import org.mpc.presentation.PortalWebScreen
import org.mpc.presentation.SettingsScreen
import org.mpc.presentation.state.CoursePlanUiState
import org.mpc.presentation.theme.AndroidMpcTheme
import org.mpc.presentation.viewModel.AppSettingsViewModel
import org.mpc.presentation.viewModel.CoursePlanViewModel

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
                    DialogProperties(windowTitle = "設定"),
                ),
            ) {
                SettingsDialog(
                    settingsViewModel = settingsViewModel,
                    onClose = { appBackStack.removeLastOrNull() },
                )
            }
        }

    AndroidMpcTheme(themeMode = settings.themeMode, timetableColorMode = settings.timetableColorMode) {
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
    LaunchedEffect(planViewModel) {
        planViewModel.saveResult.collect { saved ->
            snackbarHostState.showSnackbar(if (saved) "課表已儲存" else "課表儲存失敗，請重試")
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
                EeclassHomeScreen(
                    onOpenCourse = { number, name -> navigator.navigate(EeclassCourseRoute(number, name)) },
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
                            navigator.navigate(PortalWebRoute(title = "官方課程頁面", url = url))
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
                            contentDescription = item.label,
                        )
                    },
                    label = { Text(item.label) },
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
        title = { Text(state.title) },
        navigationIcon = {
            if (state.currentPortalWebRoute != null || state.pushedTitle != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
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
                            contentDescription = "上一頁",
                        )
                    }
                }
                WebPageMenu(url = state.currentPortalWebRoute.url)
            } else if (state.pushedTitle == null) {
                if (state.showsCoursePlanAction) {
                    TextButton(onClick = coursePlanActions.onSavePlan, enabled = state.canSavePlan) {
                        Text("儲存")
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
                        FilledTonalIconButton(
                            onClick = coursePlanActions.onOpenSelectedCourses,
                            modifier =
                            Modifier.semantics {
                                contentDescription = "查看已選課程"
                                stateDescription = "${state.selectedCourseCount} 門課"
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                            )
                        }
                    }
                }
                if (state.showsCoursePlanAction && (state.isExpanded || state.selectedCoursePlanningView == CoursePlanningView.CATALOG)) {
                    var isShowingDisplayOptions by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { isShowingDisplayOptions = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "顯示選項")
                        }
                        DropdownMenu(
                            expanded = isShowingDisplayOptions,
                            onDismissRequest = { isShowingDisplayOptions = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("精簡課程卡片") },
                                leadingIcon = {
                                    if (state.isCompactCatalog) Icon(Icons.Default.Check, contentDescription = "已啟用")
                                },
                                onClick = {
                                    coursePlanActions.onToggleCompactCatalog()
                                    isShowingDisplayOptions = false
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "設定",
                    )
                }
            }
        },
    )
}

/** Shares or opens the page the browser was opened with; later pages can carry one-time sign-in URLs. */
@Composable
private fun WebPageMenu(url: String) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var isShowingMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isShowingMenu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多選項")
        }
        DropdownMenu(
            expanded = isShowingMenu,
            onDismissRequest = { isShowingMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("分享") },
                onClick = {
                    isShowingMenu = false
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                    context.startActivity(Intent.createChooser(send, null))
                },
            )
            DropdownMenuItem(
                text = { Text("在瀏覽器中開啟") },
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
    val title: String
        get() =
            when {
                currentPortalWebRoute != null -> currentPortalWebRoute.title
                pushedTitle != null -> pushedTitle
                selectedTopLevelRoute == CoursePlanningRoot && isExpanded -> "選課"
                selectedTopLevelRoute == CoursePlanningRoot -> selectedCoursePlanningView.title
                selectedTopLevelRoute == HomeRoot -> "首頁"
                selectedTopLevelRoute == PortalRoot -> "Portal"
                else -> "Magic Pinecone"
            }

    val showsCoursePlanAction: Boolean
        get() =
            selectedTopLevelRoute == CoursePlanningRoot
}

private fun NavKey.pushedTitle(): String? = when (this) {
    is EeclassCourseRoute -> name
    is EeclassCategoryRoute -> category.title
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
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "關閉設定",
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
            )
        }
    }
}

private data class TopLevelNavigationItem(
    val route: TopLevelRoute,
    val label: String,
    val icon: ImageVector,
)

private val topLevelNavigationItems =
    listOf(
        TopLevelNavigationItem(HomeRoot, "首頁", Icons.Default.Home),
        TopLevelNavigationItem(CoursePlanningRoot, "選課", Icons.Default.DateRange),
        TopLevelNavigationItem(PortalRoot, "Portal", Icons.Default.AccountCircle),
    )
