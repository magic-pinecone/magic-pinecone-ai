@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.mpc.presentation.views.courseDetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mpc.domain.model.CourseDetail
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.DistributionCondition
import org.mpc.presentation.formatting.creditLabel
import org.mpc.presentation.formatting.scheduleDescription
import org.mpc.presentation.theme.MpcShapes
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme

@Composable
fun CourseDetailsView(
    state: CourseDetailsUiState,
    actions: CourseDetailsActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .heightIn(max = 720.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = MpcSpacing.space6, top = MpcSpacing.space2, end = MpcSpacing.space4, bottom = MpcSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MpcSpacing.space1),
            ) {
                Text(
                    text = state.summary?.title ?: "課程詳細資訊",
                    style = MpcTheme.typography.screenTitle,
                    fontWeight = FontWeight.SemiBold,
                )
                state.summary?.let { course ->
                    Text(
                        text = course.courseType.description,
                        style = MpcTheme.typography.supporting,
                        color = MpcTheme.colors.labelSecondary,
                    )
                }
            }
            IconButton(onClick = actions.onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "關閉課程詳細資訊",
                    tint = MpcTheme.colors.labelSecondary,
                )
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f, fill = true),
            contentPadding = PaddingValues(horizontal = MpcSpacing.space6, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            state.summary?.let { summary ->
                item(key = "course-facts") {
                    CourseFactsSection(summary)
                }
                item(key = "enrollment-facts") {
                    CourseFactGroup(
                        title = "選課資訊",
                        facts = listOf(
                            "上課時間" to summary.scheduleDescription(),
                            "選課人數" to "${summary.admitCnt} / ${summary.limitCnt} 人 · 候補 ${summary.waitCnt} 人",
                            "選課密碼" to summary.passwordCard.description,
                        ),
                    )
                }
                if (summary.detailUrl.isNotBlank()) {
                    item(key = "official-course-page") {
                        SheetSection {
                            TextButton(onClick = { actions.onOpenOfficialPage(summary.detailUrl) }) {
                                Text("查看官方課程頁面")
                            }
                        }
                    }
                }
            }

            if (state.isLoading) {
                item(key = "loading") {
                    SheetSection {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "正在載入課程大綱…",
                                style = MpcTheme.typography.supporting,
                                color = MpcTheme.colors.labelSecondary,
                            )
                        }
                    }
                }
            }

            state.errorMessage?.let { message ->
                item(key = "error") {
                    SheetSection {
                        Text(message, color = MpcTheme.colors.destructive)
                        TextButton(onClick = actions.onRetry) {
                            Text("重試")
                        }
                    }
                }
            }

            state.detail?.let { detail ->
                item(key = "syllabus-heading") {
                    Text(
                        text = "課程大綱",
                        style = MpcTheme.typography.sectionTitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                item(key = "objectives") {
                    DetailSection("課程目標", detail.objectives)
                }
                item(key = "content") {
                    DetailSection("課程內容", detail.content)
                }
                item(key = "books") {
                    DetailSection("教科書", detail.books)
                }
                item(key = "teaching-method") {
                    DetailSection("教學方式", detail.teachingMethod)
                }
                item(key = "grading-policy") {
                    DetailSection("評量方式", detail.gradingPolicy)
                }
                item(key = "distribution-conditions") {
                    DistributionConditionsSection(detail.distributionConditions)
                }
                if (
                    listOf(
                        detail.objectives,
                        detail.content,
                        detail.books,
                        detail.teachingMethod,
                        detail.gradingPolicy,
                    ).all(String::isBlank) && detail.distributionConditions.isEmpty()
                ) {
                    item(key = "empty-syllabus") {
                        Text(
                            text = "目前沒有課程大綱資料。",
                            color = MpcTheme.colors.labelSecondary,
                            style = MpcTheme.typography.supporting,
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        Button(
            onClick = actions.onToggleCourse,
            enabled = state.summary != null && state.canEditPlan,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MpcSpacing.space6, vertical = MpcSpacing.space4).heightIn(min = 52.dp),
            colors =
            if (state.isSelected) {
                ButtonDefaults.buttonColors(
                    containerColor = MpcTheme.colors.destructiveContainer,
                    contentColor = MpcTheme.colors.onDestructiveContainer,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            Text(if (state.isSelected) "從課表移除" else "加入課表")
        }
    }
}

@Composable
private fun CourseFactsSection(course: CourseSummary) {
    CourseFactGroup(
        title = "基本資訊",
        facts = listOf(
            "學分" to "${course.credit.creditLabel()} 學分",
            "類別" to course.courseType.description,
            "授課教師" to course.teachers.filter { it.isNotBlank() }.joinToString("、").ifBlank { "未提供" },
            "開課單位" to listOf(course.collegeName, course.departmentName)
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(" · ")
                .ifBlank { "未提供" },
            "課號" to course.classNo,
            "課程流水號" to course.serialNo.value,
        ),
    )
}

@Composable
private fun CourseFactGroup(title: String, facts: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MpcTheme.typography.headline,
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            facts.forEachIndexed { index, (label, value) ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ListItemDefaults.segmentedShapes(index, facts.size).shape,
                    color = ListItemDefaults.segmentedColors().containerColor,
                ) {
                    Column(modifier = Modifier.padding(horizontal = MpcSpacing.space4, vertical = MpcSpacing.space3)) {
                        CourseFact(label, value)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSection(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MpcShapes.card,
        color = ListItemDefaults.segmentedColors().containerColor,
    ) {
        Column(
            modifier = Modifier.padding(MpcSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
            content = content,
        )
    }
}

@Composable
private fun CourseFact(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space4),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.widthIn(min = 76.dp),
            style = MpcTheme.typography.supporting,
            color = MpcTheme.colors.labelSecondary,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MpcTheme.typography.supporting,
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: String,
) {
    if (content.isBlank()) return

    SheetSection {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MpcTheme.typography.headline,
        )
        Text(
            text = content,
            style = MpcTheme.typography.supporting,
            color = MpcTheme.colors.labelSecondary,
        )
    }
}

@Composable
private fun DistributionConditionsSection(conditions: List<DistributionCondition>) {
    SheetSection {
        Text(
            text = "分發條件",
            modifier = Modifier.semantics { heading() },
            style = MpcTheme.typography.headline,
        )
        if (conditions.isEmpty()) {
            Text(
                text = "無",
                style = MpcTheme.typography.supporting,
                color = MpcTheme.colors.labelSecondary,
            )
        }
        conditions.sortedBy { it.priority }.forEachIndexed { index, condition ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MpcTheme.typography.supporting,
                    fontFamily = FontFamily.Monospace,
                    color = MpcTheme.colors.labelSecondary,
                )
                Text(
                    text = condition.rule,
                    modifier = Modifier.weight(1f),
                    style = MpcTheme.typography.supporting,
                    color = MpcTheme.colors.labelSecondary,
                )
            }
        }
    }
}

data class CourseDetailsUiState(
    val summary: CourseSummary?,
    val detail: CourseDetail?,
    val isLoading: Boolean,
    val errorMessage: String?,
    val isSelected: Boolean,
    val canEditPlan: Boolean = true,
)

data class CourseDetailsActions(
    val onToggleCourse: () -> Unit,
    val onRetry: () -> Unit,
    val onClose: () -> Unit,
    val onOpenOfficialPage: (String) -> Unit,
)
