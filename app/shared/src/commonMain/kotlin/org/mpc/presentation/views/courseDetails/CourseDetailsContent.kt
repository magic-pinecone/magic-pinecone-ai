package org.mpc.presentation.views.courseDetails

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.CancellationException
import org.mpc.domain.model.CourseDetail
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.repository.CourseRepository
import org.mpc.presentation.state.CoursePlanUiState
import org.mpc.presentation.viewModel.CoursePlanViewModel

@Composable
fun CourseDetailsContent(
    semester: String,
    serialNumber: String,
    courseRepository: CourseRepository,
    onClose: () -> Unit,
    onOpenWebPage: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialSummary: CourseSummary? = null,
    planViewModel: CoursePlanViewModel = metroViewModel(),
) {
    val (loadState, onRetry) =
        rememberCourseDetailsLoadState(
            semester = semester,
            serialNumber = serialNumber,
            initialSummary = initialSummary,
            courseRepository = courseRepository,
        )
    val planUiState by planViewModel.uiState.collectAsStateWithLifecycle()

    val content = loadState as? CourseDetailsLoadState.Content
    val isSelected =
        content?.summary?.let { summary ->
            (planUiState as? CoursePlanUiState.Success)
                ?.plan
                ?.selectedCourses
                ?.containsKey(summary.serialNo)
        } ?: false

    CourseDetailsView(
        state =
        CourseDetailsUiState(
            summary = content?.summary,
            detail = content?.detail,
            isLoading = loadState is CourseDetailsLoadState.Loading || content?.isLoading == true,
            errorMessage =
            when {
                loadState is CourseDetailsLoadState.Unavailable -> "無法載入這門課程。"
                content?.supplementalLoadFailed == true -> "無法載入完整課程資訊。"
                else -> null
            },
            isSelected = isSelected,
            canEditPlan = planUiState is CoursePlanUiState.Success,
        ),
        actions =
        CourseDetailsActions(
            onToggleCourse = {
                content?.summary?.let(planViewModel::toggleCourse)
            },
            onRetry = onRetry,
            onClose = onClose,
            onOpenOfficialPage = onOpenWebPage,
        ),
        modifier =
        modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .heightIn(max = 720.dp),
    )
}

@Composable
private fun rememberCourseDetailsLoadState(
    semester: String,
    serialNumber: String,
    initialSummary: CourseSummary?,
    courseRepository: CourseRepository,
): Pair<CourseDetailsLoadState, () -> Unit> {
    var loadState by remember(semester, serialNumber) {
        mutableStateOf<CourseDetailsLoadState>(
            initialSummary?.let(CourseDetailsLoadState::Content) ?: CourseDetailsLoadState.Loading,
        )
    }
    var retryRequest by remember(semester, serialNumber) { mutableIntStateOf(0) }

    LaunchedEffect(semester, serialNumber, initialSummary, retryRequest) {
        var summary = initialSummary ?: (loadState as? CourseDetailsLoadState.Content)?.summary
        if (summary == null) {
            loadState = CourseDetailsLoadState.Loading
            summary = loadCourseSummary(courseRepository, semester, serialNumber)
        }

        if (summary == null) {
            loadState = CourseDetailsLoadState.Unavailable
            return@LaunchedEffect
        }

        loadState = CourseDetailsLoadState.Content(summary = summary)
        loadState = loadCourseDetails(courseRepository, semester, serialNumber, summary)
    }

    return loadState to { retryRequest++ }
}

private suspend fun loadCourseSummary(
    courseRepository: CourseRepository,
    semester: String,
    serialNumber: String,
): CourseSummary? = try {
    courseRepository
        .fetchCoursesBySerialNo(
            semester = semester,
            serialNos = listOf(CourseSerialNo(serialNumber)),
        ).courses
        .singleOrNull()
} catch (cause: CancellationException) {
    throw cause
} catch (_: Exception) {
    null
}

private suspend fun loadCourseDetails(
    courseRepository: CourseRepository,
    semester: String,
    serialNumber: String,
    summary: CourseSummary,
): CourseDetailsLoadState.Content = try {
    CourseDetailsLoadState.Content(
        summary = summary,
        detail = courseRepository.fetchCourseDetail(semester, serialNumber),
        isLoading = false,
    )
} catch (cause: CancellationException) {
    throw cause
} catch (_: Exception) {
    CourseDetailsLoadState.Content(
        summary = summary,
        isLoading = false,
        supplementalLoadFailed = true,
    )
}

private sealed interface CourseDetailsLoadState {
    data object Loading : CourseDetailsLoadState

    data object Unavailable : CourseDetailsLoadState

    data class Content(
        val summary: CourseSummary,
        val detail: CourseDetail? = null,
        val isLoading: Boolean = true,
        val supplementalLoadFailed: Boolean = false,
    ) : CourseDetailsLoadState
}
