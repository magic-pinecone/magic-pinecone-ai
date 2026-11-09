package org.mpc.presentation.views.coursePlanning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.mpc.domain.model.CourseResult
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.views.coursePlanning.components.CourseCard

@Composable
fun CourseSearchResultSuccessView(
    modifier: Modifier,
    courseResult: CourseResult,
    selectedCourseSerialNumbers: Set<CourseSerialNo>,
    onCourseClick: (CourseSummary) -> Unit,
    onCourseToggle: (CourseSummary) -> Unit,
    isCompact: Boolean = false,
    canEditPlan: Boolean = true,
) {
    if (courseResult.courses.isEmpty()) {
        Box(
            modifier = modifier.padding(MpcSpacing.space6),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
            ) {
                Text("找不到符合的課程", style = MpcTheme.typography.headline)
                Text(
                    "試試其他關鍵字，或清除搜尋條件查看全部課程。",
                    style = MpcTheme.typography.supporting,
                    color = MpcTheme.colors.labelSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    // A new result starts at its count instead of keeping the previous result's scroll offset.
    val listState = rememberSaveable(courseResult, saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(MpcSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
    ) {
        item(key = "result-count") {
            Text(
                text = "共 ${courseResult.courses.size} 門課程",
                style = MpcTheme.typography.supporting,
                color = MpcTheme.colors.labelSecondary,
            )
        }
        items(
            items = courseResult.courses,
            key = { course -> course.serialNo.value },
        ) { course ->
            CourseCard(
                modifier = Modifier.fillMaxWidth(),
                courseSummary = course,
                isSelected = course.serialNo in selectedCourseSerialNumbers,
                isCompact = isCompact,
                canEditPlan = canEditPlan,
                onCardClick = { onCourseClick(course) },
                onButtonClick = { onCourseToggle(course) },
            )
        }
    }
}
