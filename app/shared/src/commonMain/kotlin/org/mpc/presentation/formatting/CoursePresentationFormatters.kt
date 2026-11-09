package org.mpc.presentation.formatting

import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime

internal fun Double.creditLabel(): String = toString().removeSuffix(".0")

internal fun CourseSummary.scheduleDescription(): String {
    val scheduleSlots = classTimes
        .filter { time -> time.day != CourseDay.UNKNOWN }
        .distinct()
        .sortedWith(compareBy<CourseTime> { time -> time.day.order }.thenBy { time -> time.period.order })
    if (scheduleSlots.isEmpty()) return "未提供上課時間"

    return scheduleSlots.joinToString("、") { time ->
        "${time.day.code}-${time.period.description}"
    }
}
