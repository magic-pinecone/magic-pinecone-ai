package org.mpc.presentation.formatting

import org.mpc.domain.schedule.ClassMeeting
import org.mpc.domain.schedule.UpcomingMeeting

private const val MINUTES_PER_HOUR = 60
private const val SAME_WEEKDAY_NEXT_WEEK = 7

private fun Int.clockLabel(): String = "${(this / MINUTES_PER_HOUR).toString().padStart(2, '0')}:${(this % MINUTES_PER_HOUR).toString().padStart(2, '0')}"

fun ClassMeeting.timeRangeLabel(): String = "${startMinute.clockLabel()}–${endMinute.clockLabel()}"

fun ClassMeeting.periodLabel(): String = if (firstPeriod == lastPeriod) {
    "第 ${firstPeriod.description} 節"
} else {
    "第 ${firstPeriod.description}–${lastPeriod.description} 節"
}

fun UpcomingMeeting.whenLabel(): String {
    if (inProgress) return "現在"
    val time = meeting.startMinute.clockLabel()
    return when (daysFromToday) {
        0 -> "今天 $time"
        1 -> "明天 $time"
        SAME_WEEKDAY_NEXT_WEEK -> "下星期${meeting.day.description} $time"
        else -> "星期${meeting.day.description} $time"
    }
}
