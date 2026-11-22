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
import androidx.compose.foundation.lazy.LazyListScope
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
import dev.icerock.moko.resources.compose.localized
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.desc.StringDesc
import org.mpc.domain.model.CourseDetail
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.DistributionCondition
import org.mpc.domain.model.PasswordCardType
import org.mpc.presentation.formatting.creditLabel
import org.mpc.presentation.formatting.scheduleDescription
import org.mpc.presentation.theme.MpcShapes
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.translation.TextTranslation
import org.mpc.presentation.translation.TranslateButton
import org.mpc.presentation.translation.TranslationNote
import org.mpc.presentation.translation.rememberTextTranslation
import org.mpc.shared.MR
import org.mpc.shared.coursedetail_add
import org.mpc.shared.coursedetail_basic_info
import org.mpc.shared.coursedetail_books
import org.mpc.shared.coursedetail_close_details
import org.mpc.shared.coursedetail_content
import org.mpc.shared.coursedetail_course_number
import org.mpc.shared.coursedetail_course_type
import org.mpc.shared.coursedetail_credits
import org.mpc.shared.coursedetail_credits_value
import org.mpc.shared.coursedetail_department
import org.mpc.shared.coursedetail_details_title
import org.mpc.shared.coursedetail_distribution_conditions
import org.mpc.shared.coursedetail_empty_syllabus
import org.mpc.shared.coursedetail_enrollment
import org.mpc.shared.coursedetail_enrollment_count
import org.mpc.shared.coursedetail_enrollment_info
import org.mpc.shared.coursedetail_grading_policy
import org.mpc.shared.coursedetail_instructors
import org.mpc.shared.coursedetail_list_separator
import org.mpc.shared.coursedetail_loading_syllabus
import org.mpc.shared.coursedetail_none
import org.mpc.shared.coursedetail_not_provided
import org.mpc.shared.coursedetail_objectives
import org.mpc.shared.coursedetail_official_page
import org.mpc.shared.coursedetail_password
import org.mpc.shared.coursedetail_password_all
import org.mpc.shared.coursedetail_password_none
import org.mpc.shared.coursedetail_password_some
import org.mpc.shared.coursedetail_remove
import org.mpc.shared.coursedetail_retry
import org.mpc.shared.coursedetail_schedule
import org.mpc.shared.coursedetail_serial_number
import org.mpc.shared.coursedetail_syllabus
import org.mpc.shared.coursedetail_teaching_method
import org.mpc.shared.coursedetail_type_elective
import org.mpc.shared.coursedetail_type_required
import org.mpc.shared.coursedetail_type_unknown

@Composable
fun CourseDetailsView(
    state: CourseDetailsUiState,
    actions: CourseDetailsActions,
    modifier: Modifier = Modifier,
) {
    // The syllabus is the text worth translating, so translation waits until it loads.
    val translation = rememberTextTranslation(state.detail?.let { detail -> state.translatableTexts(detail) }.orEmpty())
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
                    text = state.summary?.title?.let { title -> translation?.text(title) ?: title } ?: stringResource(MR.strings.coursedetail_details_title),
                    style = MpcTheme.typography.screenTitle,
                    fontWeight = FontWeight.SemiBold,
                )
                state.summary?.let { course ->
                    Text(
                        text = stringResource(course.courseType.labelResource()),
                        style = MpcTheme.typography.supporting,
                        color = MpcTheme.colors.labelSecondary,
                    )
                }
            }
            IconButton(onClick = actions.onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(MR.strings.coursedetail_close_details),
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
                        title = stringResource(MR.strings.coursedetail_enrollment_info),
                        facts = listOf(
                            stringResource(MR.strings.coursedetail_schedule) to summary.scheduleDescription(),
                            stringResource(MR.strings.coursedetail_enrollment) to stringResource(MR.strings.coursedetail_enrollment_count, summary.admitCnt, summary.limitCnt, summary.waitCnt),
                            stringResource(MR.strings.coursedetail_password) to stringResource(summary.passwordCard.labelResource()),
                        ),
                    )
                }
                if (summary.detailUrl.isNotBlank()) {
                    item(key = "official-course-page") {
                        SheetSection {
                            TextButton(onClick = { actions.onOpenOfficialPage(summary.detailUrl) }) {
                                Text(stringResource(MR.strings.coursedetail_official_page))
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
                                text = stringResource(MR.strings.coursedetail_loading_syllabus),
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
                        Text(message.localized(), color = MpcTheme.colors.destructive)
                        TextButton(onClick = actions.onRetry) {
                            Text(stringResource(MR.strings.coursedetail_retry))
                        }
                    }
                }
            }

            state.detail?.let { detail -> syllabusItems(detail, translation) }
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
            Text(if (state.isSelected) stringResource(MR.strings.coursedetail_remove) else stringResource(MR.strings.coursedetail_add))
        }
    }
}

/** The syllabus sections, in the reader's language while [translation] is switched on. */
private fun LazyListScope.syllabusItems(
    detail: CourseDetail,
    translation: TextTranslation?,
) {
    val translated: (String) -> String = { text -> translation?.text(text) ?: text }
    item(key = "syllabus-heading") {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(MR.strings.coursedetail_syllabus),
                    modifier = Modifier.weight(1f),
                    style = MpcTheme.typography.sectionTitle,
                    fontWeight = FontWeight.SemiBold,
                )
                translation?.let { TranslateButton(it) }
            }
            translation?.let { TranslationNote(it) }
        }
    }
    item(key = "objectives") {
        DetailSection(stringResource(MR.strings.coursedetail_objectives), translated(detail.objectives))
    }
    item(key = "content") {
        DetailSection(stringResource(MR.strings.coursedetail_content), translated(detail.content))
    }
    item(key = "books") {
        DetailSection(stringResource(MR.strings.coursedetail_books), translated(detail.books))
    }
    item(key = "teaching-method") {
        DetailSection(stringResource(MR.strings.coursedetail_teaching_method), translated(detail.teachingMethod))
    }
    item(key = "grading-policy") {
        DetailSection(stringResource(MR.strings.coursedetail_grading_policy), translated(detail.gradingPolicy))
    }
    item(key = "distribution-conditions") {
        DistributionConditionsSection(detail.distributionConditions, translated)
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
                text = stringResource(MR.strings.coursedetail_empty_syllabus),
                color = MpcTheme.colors.labelSecondary,
                style = MpcTheme.typography.supporting,
            )
        }
    }
}

@Composable
private fun CourseFactsSection(course: CourseSummary) {
    CourseFactGroup(
        title = stringResource(MR.strings.coursedetail_basic_info),
        facts = listOf(
            stringResource(MR.strings.coursedetail_credits) to stringResource(MR.strings.coursedetail_credits_value, course.credit.creditLabel()),
            stringResource(MR.strings.coursedetail_course_type) to stringResource(course.courseType.labelResource()),
            stringResource(MR.strings.coursedetail_instructors) to course.teachers.filter { it.isNotBlank() }.joinToString(stringResource(MR.strings.coursedetail_list_separator)).ifBlank { stringResource(MR.strings.coursedetail_not_provided) },
            stringResource(MR.strings.coursedetail_department) to listOf(course.collegeName, course.departmentName)
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(" · ")
                .ifBlank { stringResource(MR.strings.coursedetail_not_provided) },
            stringResource(MR.strings.coursedetail_course_number) to course.classNo,
            stringResource(MR.strings.coursedetail_serial_number) to course.serialNo.value,
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
private fun DistributionConditionsSection(
    conditions: List<DistributionCondition>,
    translated: (String) -> String,
) {
    SheetSection {
        Text(
            text = stringResource(MR.strings.coursedetail_distribution_conditions),
            modifier = Modifier.semantics { heading() },
            style = MpcTheme.typography.headline,
        )
        if (conditions.isEmpty()) {
            Text(
                text = stringResource(MR.strings.coursedetail_none),
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
                    text = translated(condition.rule),
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
    val errorMessage: StringDesc?,
    val isSelected: Boolean,
    val canEditPlan: Boolean = true,
)

data class CourseDetailsActions(
    val onToggleCourse: () -> Unit,
    val onRetry: () -> Unit,
    val onClose: () -> Unit,
    val onOpenOfficialPage: (String) -> Unit,
)

/** The course's own Chinese text: its title, syllabus, and distribution rules. */
private fun CourseDetailsUiState.translatableTexts(detail: CourseDetail): List<String> = listOfNotNull(summary?.title) +
    listOf(detail.objectives, detail.content, detail.books, detail.teachingMethod, detail.gradingPolicy) +
    detail.distributionConditions.map { it.rule }

private fun CourseType.labelResource() = when (this) {
    CourseType.REQUIRED -> MR.strings.coursedetail_type_required
    CourseType.ELECTIVE -> MR.strings.coursedetail_type_elective
    CourseType.UNKNOWN -> MR.strings.coursedetail_type_unknown
}

private fun PasswordCardType.labelResource() = when (this) {
    PasswordCardType.ALL -> MR.strings.coursedetail_password_all
    PasswordCardType.OPTIONAL -> MR.strings.coursedetail_password_some
    PasswordCardType.NONE -> MR.strings.coursedetail_password_none
}
