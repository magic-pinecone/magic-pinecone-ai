package org.mpc.presentation.views.courseDetails

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.repository.CourseRepository
import org.mpc.presentation.viewModel.CoursePlanViewModel

data class CourseDetailsSelection(
    val semester: String,
    val serialNumber: String,
    val course: CourseSummary? = null,
) {
    constructor(semester: String, course: CourseSummary) : this(semester, course.serialNumber, course)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CourseDetailsBottomSheet(
    selection: CourseDetailsSelection,
    courseRepository: CourseRepository,
    onDismissRequest: () -> Unit,
    onOpenWebPage: (String) -> Unit,
    planViewModel: CoursePlanViewModel,
) {
    // Course details include a persistent action footer, so don't hide it in the
    // partial-height state when the sheet first opens.
    val sheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = true)

    AdaptiveBottomSheet(
        onDismissRequest = onDismissRequest,
        adaptiveSheetState = sheetState,
    ) {
        CourseDetailsContent(
            semester = selection.semester,
            serialNumber = selection.serialNumber,
            courseRepository = courseRepository,
            onClose = onDismissRequest,
            onOpenWebPage = onOpenWebPage,
            modifier = Modifier.fillMaxWidth(),
            initialSummary = selection.course,
            planViewModel = planViewModel,
        )
    }
}
