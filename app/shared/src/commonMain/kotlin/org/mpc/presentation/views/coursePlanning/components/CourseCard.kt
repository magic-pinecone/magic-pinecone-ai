package org.mpc.presentation.views.coursePlanning.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.button.AdaptiveButton
import com.mohamedrejeb.calf.ui.button.AdaptiveIconButton
import com.mohamedrejeb.calf.ui.button.LiquidGlassButtonColors
import com.mohamedrejeb.calf.ui.gesture.adaptiveClickable
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.PasswordCardType
import org.mpc.presentation.formatting.creditLabel
import org.mpc.presentation.formatting.scheduleDescription
import org.mpc.presentation.icon.apartment
import org.mpc.presentation.icon.groups
import org.mpc.presentation.icon.key
import org.mpc.presentation.icon.schedule
import org.mpc.presentation.theme.MpcShapes
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme

@Composable
fun CourseCard(
    modifier: Modifier = Modifier,
    courseSummary: CourseSummary,
    isSelected: Boolean,
    onCardClick: () -> Unit = {},
    onButtonClick: () -> Unit = {},
    isCompact: Boolean = false,
    canEditPlan: Boolean = true,
) {
    val cardShape = MpcShapes.card

    Surface(
        modifier =
        modifier.semantics {
            stateDescription = if (isSelected) "已加入課表" else "尚未加入課表"
        }.adaptiveClickable(
            shape = cardShape,
            role = Role.Button,
            onClick = onCardClick,
        ),
        shape = cardShape,
        color =
        if (isSelected) {
            MpcTheme.colors.surfaceSelected
        } else {
            MpcTheme.colors.surface
        },
        border =
        BorderStroke(
            MpcSpacing.hairline,
            if (isSelected) MpcTheme.colors.borderStrong else MpcTheme.colors.separator,
        ),
    ) {
        if (isCompact) {
            CompactCourseCardContent(
                courseSummary = courseSummary,
                isSelected = isSelected,
                canEditPlan = canEditPlan,
                onToggleSelection = onButtonClick,
            )
        } else {
            Column(
                modifier = Modifier.padding(MpcSpacing.card),
                verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = courseSummary.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MpcTheme.typography.headline,
                    )
                    CourseTypeBadge(courseSummary.courseType, isCompact = false)
                }
                Text(
                    text = courseSummary.primaryMetadataText(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MpcTheme.typography.supporting,
                    color = MpcTheme.colors.labelSecondary,
                )

                CourseInfoRail(courseSummary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MpcTheme.colors.accent,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = courseSummary.scheduleDescription(),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MpcTheme.typography.supporting,
                    )
                    Spacer(Modifier.width(MpcSpacing.space2))
                    AdaptiveButton(
                        onClick = onButtonClick,
                        enabled = canEditPlan,
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = courseSummary.selectionActionDescription(isSelected)
                        },
                        colors =
                        if (isSelected) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MpcTheme.colors.destructiveContainer,
                                contentColor = MpcTheme.colors.onDestructiveContainer,
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        liquidGlassColors = if (isSelected) {
                            removeLiquidGlassColors()
                        } else {
                            null
                        },
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text(if (isSelected) "移除" else "加入")
                    }
                }
            }
        }
    }
}

@Composable
internal fun CourseTypeBadge(
    courseType: CourseType,
    isCompact: Boolean,
) {
    if (courseType == CourseType.UNKNOWN) return
    Box(
        modifier =
        Modifier
            .background(
                color = MpcTheme.colors.fillQuiet,
                shape = MpcShapes.badge,
            ).padding(horizontal = if (isCompact) MpcSpacing.space2 else 10.dp, vertical = if (isCompact) 3.dp else 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = courseType.description,
            maxLines = if (isCompact) 1 else 2,
            style = if (isCompact) MpcTheme.typography.micro else MpcTheme.typography.label,
            color = MpcTheme.colors.onFillQuiet,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun CompactCourseSelectionControl(
    isSelected: Boolean,
    canEditPlan: Boolean,
    courseSummary: CourseSummary,
    onClick: () -> Unit,
) {
    AdaptiveIconButton(
        onClick = onClick,
        enabled = canEditPlan,
        modifier = Modifier.size(48.dp).semantics {
            contentDescription = courseSummary.selectionActionDescription(isSelected)
        },
        colors = if (isSelected) {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MpcTheme.colors.destructiveContainer,
                contentColor = MpcTheme.colors.onDestructiveContainer,
            )
        } else {
            IconButtonDefaults.iconButtonColors()
        },
        liquidGlassColors = if (isSelected) {
            removeLiquidGlassColors()
        } else {
            null
        },
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.Remove else Icons.Default.Add,
            contentDescription = null,
        )
    }
}

/** Removing a course is reversible, so 移除 is tonal red rather than a solid warning fill. */
@Composable
private fun removeLiquidGlassColors() = LiquidGlassButtonColors(
    tintColor = MpcTheme.colors.destructiveContainer,
    surfaceColor = MpcTheme.colors.destructiveContainer,
    contentColor = MpcTheme.colors.onDestructiveContainer,
    disabledContentColor = MpcTheme.colors.onDestructiveContainer.copy(alpha = 0.4f),
)

@Composable
private fun CourseInfoRail(courseSummary: CourseSummary) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
    ) {
        CourseInfoBadge(
            imageVector = apartment,
            infoText = courseSummary.departmentText(),
        )
        CourseInfoBadge(
            imageVector = groups,
            infoText = courseSummary.enrollmentText(),
        )
        CourseInfoBadge(
            imageVector = key,
            infoText = courseSummary.passwordText(),
        )
    }
}

@Composable
private fun CourseInfoBadge(
    imageVector: ImageVector,
    infoText: String,
) {
    Row(
        modifier =
        Modifier
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MpcTheme.colors.labelSecondary,
        )
        Spacer(Modifier.width(MpcSpacing.space1))
        Text(
            text = infoText,
            maxLines = 1,
            softWrap = false,
            style = MpcTheme.typography.label,
            color = MpcTheme.colors.labelSecondary,
        )
    }
}

/*
    Some helper function to give the information on the card
 */
private fun CourseSummary.selectionActionDescription(isSelected: Boolean): String = if (isSelected) {
    "從課表移除 $title"
} else {
    "加入課表 $title"
}

private fun CourseSummary.primaryMetadataText(): String {
    val instructorText = teachers.filter { it.isNotBlank() }.joinToString("、").ifBlank { "未提供授課教師" }
    return listOf(classNo, "${credit.creditLabel()} 學分", instructorText)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

private fun CourseSummary.departmentText(): String = "$collegeName / $departmentName"

private fun CourseSummary.enrollmentText(): String = if (waitCnt > 0) {
    "$admitCnt / $limitCnt · 候補 $waitCnt"
} else {
    "$admitCnt / $limitCnt"
}

private fun CourseSummary.passwordText(): String = passwordCard.description

@Composable
@Preview
internal fun PreviewCourseCard(
    courseSummary: CourseSummary =
        CourseSummary(
            serialNo = CourseSerialNo("36019"),
            classNo = "ENA103-*",
            title = "專題討論（III）",
            credit = 0.0,
            passwordCard = PasswordCardType.NONE,
            teachers = listOf("鄭明敏", "林居慶", "林進榮", "林伯勳"),
            classTimes =
            listOf(
                CourseTime(CourseDay.FRIDAY, CoursePeriod.A),
                CourseTime(CourseDay.FRIDAY, CoursePeriod.B),
                CourseTime(CourseDay.FRIDAY, CoursePeriod.C),
            ),
            limitCnt = 0,
            admitCnt = 0,
            waitCnt = 0,
            collegeName = "工學院",
            departmentName = "環境工程研究所碩士班",
            courseType = CourseType.ELECTIVE,
            detailUrl = "https://cis.ncu.edu.tw/Course/main/support/courseDetail.html?crs=36019",
        ),
) {
    MpcTheme {
        CourseCard(
            modifier = Modifier.width(360.dp),
            courseSummary = courseSummary,
            isSelected = false,
        )
        CourseCard(
            modifier = Modifier.width(360.dp),
            courseSummary = courseSummary,
            isSelected = true,
        )
    }
}
