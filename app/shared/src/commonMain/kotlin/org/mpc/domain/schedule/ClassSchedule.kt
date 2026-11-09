package org.mpc.domain.schedule

import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CoursePlan
import org.mpc.domain.model.CourseSummary

data class ClassMeeting(
    val course: CourseSummary,
    val day: CourseDay,
    val firstPeriod: CoursePeriod,
    val lastPeriod: CoursePeriod,
) {
    val startMinute: Int get() = firstPeriod.startMinute
    val endMinute: Int get() = lastPeriod.endMinute
}

/** [daysFromToday] is 0 for later today, 1..7 for following days; 7 is the same weekday next week. */
data class UpcomingMeeting(
    val meeting: ClassMeeting,
    val daysFromToday: Int,
    val inProgress: Boolean,
)

private val weekdays = CourseDay.entries.filter { it != CourseDay.UNKNOWN }.sortedBy { it.order }

fun CoursePlan.meetingsOn(day: CourseDay): List<ClassMeeting> {
    if (day == CourseDay.UNKNOWN) return emptyList()
    return selectedCourses.values
        .flatMap { course -> course.meetingsOn(day) }
        .sortedWith(compareBy<ClassMeeting> { it.startMinute }.thenBy { it.course.serialNumber })
}

fun CoursePlan.nextMeeting(
    day: CourseDay,
    minuteOfDay: Int,
): UpcomingMeeting? {
    if (day == CourseDay.UNKNOWN) return null
    val today = meetingsOn(day)
    val current = today.firstOrNull { minuteOfDay >= it.startMinute && minuteOfDay < it.endMinute }
    val later = today.firstOrNull { it.startMinute > minuteOfDay }
    return when {
        current != null -> UpcomingMeeting(current, daysFromToday = 0, inProgress = true)
        later != null -> UpcomingMeeting(later, daysFromToday = 0, inProgress = false)
        else -> nextMeetingAfter(day)
    }
}

private fun CoursePlan.nextMeetingAfter(day: CourseDay): UpcomingMeeting? {
    val todayIndex = weekdays.indexOf(day)
    return (1..weekdays.size).firstNotNullOfOrNull { offset ->
        meetingsOn(weekdays[(todayIndex + offset) % weekdays.size]).firstOrNull()
            ?.let { UpcomingMeeting(it, daysFromToday = offset, inProgress = false) }
    }
}

/** Every weekly meeting of this course, Monday first; consecutive periods on a day form one meeting. */
fun CourseSummary.meetings(): List<ClassMeeting> = weekdays.flatMap { meetingsOn(it) }

private fun CourseSummary.meetingsOn(day: CourseDay): List<ClassMeeting> {
    val periods = classTimes
        .filter { it.day == day }
        .map { it.period }
        .distinct()
        .sortedBy { it.order }
    val runs = mutableListOf<MutableList<CoursePeriod>>()
    for (period in periods) {
        val run = runs.lastOrNull()
        if (run != null && run.last().order + 1 == period.order) run.add(period) else runs.add(mutableListOf(period))
    }
    return runs.map { ClassMeeting(this, day, it.first(), it.last()) }
}
