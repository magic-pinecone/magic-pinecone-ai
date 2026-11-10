package org.mpc.presentation.formatting

import dev.icerock.moko.resources.desc.Raw
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc
import org.mpc.domain.model.CourseDay
import org.mpc.domain.schedule.ClassMeeting
import org.mpc.domain.schedule.UpcomingMeeting
import org.mpc.shared.MR
import org.mpc.shared.planning_period_range
import org.mpc.shared.planning_period_single
import org.mpc.shared.planning_weekday_friday
import org.mpc.shared.planning_weekday_monday
import org.mpc.shared.planning_weekday_saturday
import org.mpc.shared.planning_weekday_sunday
import org.mpc.shared.planning_weekday_thursday
import org.mpc.shared.planning_weekday_tuesday
import org.mpc.shared.planning_weekday_wednesday
import org.mpc.shared.planning_when_next_week
import org.mpc.shared.planning_when_now
import org.mpc.shared.planning_when_today
import org.mpc.shared.planning_when_tomorrow
import org.mpc.shared.planning_when_weekday

private const val MINUTES_PER_HOUR = 60
private const val SAME_WEEKDAY_NEXT_WEEK = 7

private fun Int.clockLabel(): String = "${(this / MINUTES_PER_HOUR).toString().padStart(2, '0')}:${(this % MINUTES_PER_HOUR).toString().padStart(2, '0')}"

fun ClassMeeting.timeRangeLabel(): String = "${startMinute.clockLabel()}–${endMinute.clockLabel()}"

fun ClassMeeting.periodDesc(): StringDesc = if (firstPeriod == lastPeriod) {
    StringDesc.ResourceFormatted(MR.strings.planning_period_single, firstPeriod.description)
} else {
    StringDesc.ResourceFormatted(MR.strings.planning_period_range, firstPeriod.description, lastPeriod.description)
}

fun UpcomingMeeting.whenDesc(): StringDesc {
    if (inProgress) return StringDesc.Resource(MR.strings.planning_when_now)
    val time = meeting.startMinute.clockLabel()
    return when (daysFromToday) {
        0 -> StringDesc.ResourceFormatted(MR.strings.planning_when_today, time)

        1 -> StringDesc.ResourceFormatted(MR.strings.planning_when_tomorrow, time)

        SAME_WEEKDAY_NEXT_WEEK -> meeting.day.weekdayDesc()
            ?.let { StringDesc.ResourceFormatted(MR.strings.planning_when_next_week, it, time) }
            ?: StringDesc.Raw(time)

        else -> meeting.day.weekdayDesc()
            ?.let { StringDesc.ResourceFormatted(MR.strings.planning_when_weekday, it, time) }
            ?: StringDesc.Raw(time)
    }
}

private fun CourseDay.weekdayDesc(): StringDesc? = when (this) {
    CourseDay.MONDAY -> StringDesc.Resource(MR.strings.planning_weekday_monday)
    CourseDay.TUESDAY -> StringDesc.Resource(MR.strings.planning_weekday_tuesday)
    CourseDay.WEDNESDAY -> StringDesc.Resource(MR.strings.planning_weekday_wednesday)
    CourseDay.THURSDAY -> StringDesc.Resource(MR.strings.planning_weekday_thursday)
    CourseDay.FRIDAY -> StringDesc.Resource(MR.strings.planning_weekday_friday)
    CourseDay.SATURDAY -> StringDesc.Resource(MR.strings.planning_weekday_saturday)
    CourseDay.SUNDAY -> StringDesc.Resource(MR.strings.planning_weekday_sunday)
    CourseDay.UNKNOWN -> null
}
