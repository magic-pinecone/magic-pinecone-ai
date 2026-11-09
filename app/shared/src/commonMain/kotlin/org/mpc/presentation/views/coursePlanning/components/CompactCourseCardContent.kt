package org.mpc.presentation.views.coursePlanning.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mpc.domain.model.CourseSummary
import org.mpc.presentation.formatting.creditLabel
import org.mpc.presentation.formatting.scheduleDescription
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme

@Composable
internal fun CompactCourseCardContent(
    courseSummary: CourseSummary,
    isSelected: Boolean,
    canEditPlan: Boolean,
    onToggleSelection: () -> Unit,
) {
    val accessibilityTextSize = LocalDensity.current.fontScale >= 1.3f
    Row(
        modifier = Modifier.fillMaxWidth().padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CourseTypeBadge(courseSummary.courseType, isCompact = true)
                Text(
                    text = courseSummary.title,
                    modifier = Modifier.weight(1f),
                    maxLines = if (accessibilityTextSize) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MpcTheme.typography.supporting,
                    fontWeight = FontWeight.Medium,
                )
            }
            CompactCourseMetadata(courseSummary, accessibilityTextSize)
        }
        CompactCourseSelectionControl(isSelected, canEditPlan, courseSummary, onToggleSelection)
    }
}

@Composable
private fun CompactCourseMetadata(course: CourseSummary, accessibilityTextSize: Boolean) {
    val teachers = course.teachers
        .filter { it.isNotBlank() }
        .joinToString("、")
        .ifBlank { "未提供授課教師" }
    val credits = "${course.credit.creditLabel()} 學分"
    val metadata: @Composable (String, Modifier) -> Unit = { text, modifier ->
        Text(
            text = text,
            modifier = modifier,
            maxLines = if (accessibilityTextSize) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            style = MpcTheme.typography.caption,
            color = MpcTheme.colors.labelSecondary,
        )
    }
    if (accessibilityTextSize) {
        Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space1)) {
            metadata(course.scheduleDescription(), Modifier)
            metadata(teachers, Modifier)
            metadata(credits, Modifier)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            metadata(course.scheduleDescription(), Modifier.weight(1f, fill = false))
            metadata(teachers, Modifier.weight(1f))
            metadata(credits, Modifier)
        }
    }
}
