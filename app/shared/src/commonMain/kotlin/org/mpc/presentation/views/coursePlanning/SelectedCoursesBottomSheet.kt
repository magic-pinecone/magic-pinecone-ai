package org.mpc.presentation.views.coursePlanning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import dev.icerock.moko.resources.compose.pluralStringResource
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.domain.model.CourseSummary
import org.mpc.presentation.components.AdaptiveList
import org.mpc.presentation.components.AdaptiveListItem
import org.mpc.presentation.formatting.creditLabel
import org.mpc.presentation.formatting.creditsLabel
import org.mpc.presentation.formatting.instructorsLabel
import org.mpc.presentation.formatting.scheduleDescription
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.shared.MR
import org.mpc.shared.planning_course_remove
import org.mpc.shared.planning_course_remove_from_selected
import org.mpc.shared.planning_selected_close
import org.mpc.shared.planning_selected_empty_hint
import org.mpc.shared.planning_selected_empty_title
import org.mpc.shared.planning_selected_summary
import org.mpc.shared.planning_selected_title

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SelectedCoursesBottomSheet(
    courses: List<CourseSummary>,
    onDismissRequest: () -> Unit,
    onRemoveCourse: (CourseSummary) -> Unit,
    canEditPlan: Boolean = true,
) {
    val sheetState = rememberAdaptiveSheetState()

    AdaptiveBottomSheet(
        onDismissRequest = onDismissRequest,
        adaptiveSheetState = sheetState,
    ) {
        SelectedCoursesSheetContent(
            courses = courses,
            onDismissRequest = onDismissRequest,
            onRemoveCourse = onRemoveCourse,
            canEditPlan = canEditPlan,
        )
    }
}

@Composable
fun SelectedCoursesSheetContent(
    courses: List<CourseSummary>,
    onDismissRequest: () -> Unit,
    onRemoveCourse: (CourseSummary) -> Unit,
    modifier: Modifier = Modifier,
    listHorizontalPadding: Dp = MpcSpacing.space6,
    canEditPlan: Boolean = true,
) {
    val totalCredits = courses.sumOf { it.credit }.creditLabel()

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = MpcSpacing.space6, top = MpcSpacing.space2, end = MpcSpacing.space4, bottom = MpcSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(MR.strings.planning_selected_title),
                    modifier = Modifier.semantics { heading() },
                    style = MpcTheme.typography.sectionTitle,
                )
                Text(
                    text = pluralStringResource(MR.plurals.planning_selected_summary, courses.size, courses.size, totalCredits),
                    color = MpcTheme.colors.labelSecondary,
                    style = MpcTheme.typography.supporting,
                )
            }
            IconButton(onClick = onDismissRequest) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(MR.strings.planning_selected_close),
                    tint = MpcTheme.colors.labelSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(MpcSpacing.space2))

        if (courses.isEmpty()) {
            EmptySelectedCoursesState()
        } else {
            val coursesById = courses.associateBy { it.serialNo.value }
            AdaptiveList(
                items = courses.map { course -> course.toListItem(canEditPlan) },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = listHorizontalPadding)
                    .padding(bottom = 20.dp)
                    .heightIn(max = 560.dp),
                onItemAction = { id -> coursesById[id]?.let(onRemoveCourse) },
            )
        }
    }
}

@Composable
private fun EmptySelectedCoursesState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MpcSpacing.space6, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
    ) {
        Icon(
            imageVector = Icons.Default.ShoppingCart,
            contentDescription = null,
            tint = MpcTheme.colors.labelSecondary,
        )
        Text(
            text = stringResource(MR.strings.planning_selected_empty_title),
            style = MpcTheme.typography.headline,
        )
        Text(
            text = stringResource(MR.strings.planning_selected_empty_hint),
            color = MpcTheme.colors.labelSecondary,
            style = MpcTheme.typography.supporting,
        )
    }
}

@Composable
private fun CourseSummary.toListItem(canEditPlan: Boolean): AdaptiveListItem = AdaptiveListItem(
    id = serialNo.value,
    title = title,
    supportingText =
    buildList {
        add(courseType.description)
        add(teachers.instructorsLabel())
        add(scheduleDescription())
    }.joinToString("\n"),
    trailingText = credit.creditsLabel(),
    actionLabel = if (canEditPlan) stringResource(MR.strings.planning_course_remove) else null,
    actionAccessibilityLabel = stringResource(MR.strings.planning_course_remove_from_selected, title),
)
