package org.mpc.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AdaptStrategy
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.repository.CourseRepository
import org.mpc.presentation.state.CoursePlanUiState
import org.mpc.presentation.viewModel.CoursePlanViewModel
import org.mpc.presentation.views.courseDetails.CourseDetailsBottomSheet
import org.mpc.presentation.views.courseDetails.CourseDetailsSelection
import org.mpc.presentation.views.coursePlanning.CoursePlanningTimetableView
import org.mpc.presentation.views.coursePlanning.SelectedCoursesBottomSheet

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun CoursePlanningScreen(
    modifier: Modifier = Modifier,
    selectedView: CoursePlanningView,
    onSelectedViewChange: (CoursePlanningView) -> Unit,
    courseRepository: CourseRepository,
    onSelectedCoursesChange: (List<CourseSummary>) -> Unit = {},
    isShowingSelectedCourses: Boolean = false,
    onDismissSelectedCourses: () -> Unit = {},
    onOpenWebPage: (String) -> Unit = {},
    isCompactCatalog: Boolean = false,
    planViewModel: CoursePlanViewModel = metroViewModel(),
) {
    val planUiState by planViewModel.uiState.collectAsStateWithLifecycle()
    val stateHolder = rememberSaveableStateHolder()
    val selectedCourses =
        (planUiState as? CoursePlanUiState.Success)
            ?.plan
            ?.selectedCourses
            ?.values
            ?.sortedBy { course -> course.title }
            .orEmpty()
    var selectedCourseDetails by rememberSaveable(stateSaver = CourseDetailsSelectionSaver) {
        mutableStateOf<CourseDetailsSelection?>(null)
    }
    val onCourseClick: (semester: String, course: CourseSummary) -> Unit = { semester, course ->
        selectedCourseDetails = CourseDetailsSelection(semester, course)
    }

    SideEffect {
        onSelectedCoursesChange(selectedCourses)
    }
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isTabletop = adaptiveInfo.windowPosture.isTabletop
    val directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(adaptiveInfo).let {
        it.copy(
            maxHorizontalPartitions = if (isTabletop) 1 else it.maxHorizontalPartitions.coerceAtMost(2),
            maxVerticalPartitions = if (isTabletop) 2 else 1,
        )
    }
    val currentPane = if (isTabletop || selectedView == CoursePlanningView.TIMETABLE) {
        SupportingPaneScaffoldRole.Supporting
    } else {
        SupportingPaneScaffoldRole.Main
    }
    val scaffoldValue = calculateThreePaneScaffoldValue(
        maxHorizontalPartitions = directive.maxHorizontalPartitions,
        maxVerticalPartitions = directive.maxVerticalPartitions,
        adaptStrategies = SupportingPaneScaffoldDefaults.adaptStrategies(
            mainPaneAdaptStrategy = if (isTabletop) {
                AdaptStrategy.Reflow(SupportingPaneScaffoldRole.Supporting)
            } else {
                AdaptStrategy.Hide
            },
            supportingPaneAdaptStrategy = AdaptStrategy.Hide,
        ),
        currentDestination = ThreePaneScaffoldDestinationItem<Unit>(currentPane),
    )
    val showsBothPanes = scaffoldValue.primary != PaneAdaptedValue.Hidden &&
        scaffoldValue.secondary != PaneAdaptedValue.Hidden

    Column(modifier = modifier) {
        if (!showsBothPanes) {
            CoursePlanningSectionPicker(selectedView, onSelectedViewChange)
        }
        if (showsBothPanes) {
            SupportingPaneScaffold(
                modifier = Modifier.fillMaxWidth().weight(1f),
                directive = directive,
                value = scaffoldValue,
                mainPane = {
                    AnimatedPane {
                        CourseCatalogPane(
                            modifier = Modifier.fillMaxSize(),
                            stateHolder = stateHolder,
                            isCompactCatalog = isCompactCatalog,
                            planViewModel = planViewModel,
                            onCourseClick = onCourseClick,
                        )
                    }
                },
                supportingPane = {
                    AnimatedPane {
                        CourseTimetablePane(
                            modifier = Modifier.fillMaxSize(),
                            stateHolder = stateHolder,
                            planUiState = planUiState,
                            onCourseClick = onCourseClick,
                        )
                    }
                },
            )
        } else {
            CoursePlanningCompactPager(
                modifier = Modifier.fillMaxWidth().weight(1f),
                selectedView = selectedView,
                onSelectedViewChange = onSelectedViewChange,
            ) { view ->
                when (view) {
                    CoursePlanningView.CATALOG -> CourseCatalogPane(
                        modifier = Modifier.fillMaxSize(),
                        stateHolder = stateHolder,
                        isCompactCatalog = isCompactCatalog,
                        planViewModel = planViewModel,
                        onCourseClick = onCourseClick,
                    )

                    CoursePlanningView.TIMETABLE -> CourseTimetablePane(
                        modifier = Modifier.fillMaxSize(),
                        stateHolder = stateHolder,
                        planUiState = planUiState,
                        onCourseClick = onCourseClick,
                    )
                }
            }
        }
    }

    selectedCourseDetails?.let { selection ->
        CourseDetailsBottomSheet(
            selection = selection,
            courseRepository = courseRepository,
            onDismissRequest = { selectedCourseDetails = null },
            onOpenWebPage = { url ->
                selectedCourseDetails = null
                onOpenWebPage(url)
            },
            planViewModel = planViewModel,
        )
    }

    if (isShowingSelectedCourses) {
        SelectedCoursesBottomSheet(
            courses = selectedCourses,
            onDismissRequest = onDismissSelectedCourses,
            onRemoveCourse = planViewModel::toggleCourse,
            canEditPlan = planUiState is CoursePlanUiState.Success,
        )
    }
}

@Composable
private fun CoursePlanningCompactPager(
    modifier: Modifier,
    selectedView: CoursePlanningView,
    onSelectedViewChange: (CoursePlanningView) -> Unit,
    content: @Composable (CoursePlanningView) -> Unit,
) {
    val pages = CoursePlanningView.entries
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(selectedView),
        pageCount = { pages.size },
    )
    val currentSelectedView by rememberUpdatedState(selectedView)
    val currentOnSelectedViewChange by rememberUpdatedState(onSelectedViewChange)

    LaunchedEffect(selectedView, pagerState) {
        val targetPage = pages.indexOf(selectedView)
        if (pagerState.currentPage != targetPage || pagerState.currentPageOffsetFraction != 0f) {
            pagerState.animateScrollToPage(targetPage)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .distinctUntilChanged()
            .collect { page ->
                val settledView = pages[page]
                if (settledView != currentSelectedView) {
                    currentOnSelectedViewChange(settledView)
                }
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        key = { page -> pages[page] },
    ) { page ->
        content(pages[page])
    }
}

// Persist only the lookup key; the sheet reloads its summary after recreation.
internal val CourseDetailsSelectionSaver = listSaver<CourseDetailsSelection?, String>(
    save = { selection -> selection?.let { listOf(it.semester, it.serialNumber) }.orEmpty() },
    restore = { CourseDetailsSelection(semester = it[0], serialNumber = it[1]) },
)

@Composable
private fun CourseCatalogPane(
    modifier: Modifier,
    stateHolder: SaveableStateHolder,
    isCompactCatalog: Boolean,
    planViewModel: CoursePlanViewModel,
    onCourseClick: (semester: String, course: CourseSummary) -> Unit,
) {
    stateHolder.SaveableStateProvider(CoursePlanningView.CATALOG) {
        CourseCatalogScreen(
            modifier = modifier,
            isCompact = isCompactCatalog,
            planViewModel = planViewModel,
            onCourseClick = onCourseClick,
        )
    }
}

@Composable
private fun CourseTimetablePane(
    modifier: Modifier,
    stateHolder: SaveableStateHolder,
    planUiState: CoursePlanUiState,
    onCourseClick: (semester: String, course: CourseSummary) -> Unit,
) {
    stateHolder.SaveableStateProvider(CoursePlanningView.TIMETABLE) {
        CoursePlanningTimetableView(
            uiState = planUiState,
            onCourseClick = onCourseClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun CoursePlanningSectionPicker(
    selectedView: CoursePlanningView,
    onSelectedViewChange: (CoursePlanningView) -> Unit,
) {
    SectionPicker(
        items = CoursePlanningView.entries,
        selected = selectedView,
        onSelectedChange = onSelectedViewChange,
        label = { it.label },
        icon = { it.icon },
    )
}

@Composable
internal fun <T> SectionPicker(
    items: List<T>,
    selected: T,
    onSelectedChange: (T) -> Unit,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        items.forEach { item ->
            toggleableItem(
                checked = selected == item,
                onCheckedChange = { isChecked ->
                    if (isChecked) onSelectedChange(item)
                },
                label = label(item),
                icon = { Icon(imageVector = icon(item), contentDescription = null) },
                weight = 1f,
            )
        }
    }
}

enum class CoursePlanningView(
    val label: String,
    val title: String,
    val icon: ImageVector,
) {
    CATALOG("搜尋", "課程搜尋", Icons.Default.Search),
    TIMETABLE("課表", "我的課表", Icons.Default.DateRange),
}
