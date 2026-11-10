package org.mpc.presentation.formatting

import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime
import org.mpc.shared.MR
import org.mpc.shared.planning_credits_value
import org.mpc.shared.planning_instructors_not_provided
import org.mpc.shared.planning_list_separator
import org.mpc.shared.planning_schedule_not_provided

internal fun Double.creditLabel(): String = toString().removeSuffix(".0")

@Composable
internal fun Double.creditsLabel(): String = stringResource(MR.strings.planning_credits_value, creditLabel())

@Composable
internal fun List<String>.instructorsLabel(): String {
    val separator = stringResource(MR.strings.planning_list_separator)
    return filter { it.isNotBlank() }
        .joinToString(separator)
        .ifBlank { stringResource(MR.strings.planning_instructors_not_provided) }
}

@Composable
internal fun CourseSummary.scheduleDescription(): String {
    val scheduleSlots = classTimes
        .filter { time -> time.day != CourseDay.UNKNOWN }
        .distinct()
        .sortedWith(compareBy<CourseTime> { time -> time.day.order }.thenBy { time -> time.period.order })
    if (scheduleSlots.isEmpty()) return stringResource(MR.strings.planning_schedule_not_provided)

    return scheduleSlots.joinToString(stringResource(MR.strings.planning_list_separator)) { time ->
        "${time.day.code}-${time.period.description}"
    }
}
