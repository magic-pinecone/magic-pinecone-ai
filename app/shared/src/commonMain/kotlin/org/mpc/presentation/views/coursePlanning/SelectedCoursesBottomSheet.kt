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
import org.mpc.domain.model.CourseSummary
import org.mpc.presentation.components.AdaptiveList
import org.mpc.presentation.components.AdaptiveListItem
import org.mpc.presentation.formatting.creditLabel
import org.mpc.presentation.formatting.scheduleDescription
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme

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
                    text = "已選課程",
                    modifier = Modifier.semantics { heading() },
                    style = MpcTheme.typography.sectionTitle,
                )
                Text(
                    text = "${courses.size} 門課 · $totalCredits 學分",
                    color = MpcTheme.colors.labelSecondary,
                    style = MpcTheme.typography.supporting,
                )
            }
            IconButton(onClick = onDismissRequest) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "關閉已選課程",
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
            text = "還沒有已選課程",
            style = MpcTheme.typography.headline,
        )
        Text(
            text = "加入的課程會顯示於此。",
            color = MpcTheme.colors.labelSecondary,
            style = MpcTheme.typography.supporting,
        )
    }
}

private fun CourseSummary.toListItem(canEditPlan: Boolean): AdaptiveListItem = AdaptiveListItem(
    id = serialNo.value,
    title = title,
    supportingText =
    buildList {
        add(courseType.description)
        add(teachers.filter { it.isNotBlank() }.joinToString("、").ifBlank { "未提供授課教師" })
        add(scheduleDescription())
    }.joinToString("\n"),
    trailingText = "${credit.creditLabel()} 學分",
    actionLabel = if (canEditPlan) "移除" else null,
    actionAccessibilityLabel = "從已選課程移除 $title",
)
