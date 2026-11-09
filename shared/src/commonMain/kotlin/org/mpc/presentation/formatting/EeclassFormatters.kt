package org.mpc.presentation.formatting

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import org.mpc.domain.eeclass.DueHomework
import org.mpc.domain.eeclass.PostedBulletin
import org.mpc.domain.model.CourseDay

private const val DAYS_IN_WEEK = 7

/** "今天 23:59", "明天 09:00", "星期五 23:59", or "11月20日 23:59" once the deadline is a week or more away. */
fun DueHomework.dueLabel(today: LocalDate): String {
    val time = "${due.hour.toString().padStart(2, '0')}:${due.minute.toString().padStart(2, '0')}"
    return "${dayLabel(due.date, today)} $time"
}

/** "今天", "昨天", or "11月6日". */
fun PostedBulletin.postedLabel(today: LocalDate): String = dayLabel(posted, today)

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (today.daysUntil(date)) {
    -1 -> "昨天"
    0 -> "今天"
    1 -> "明天"
    in 2 until DAYS_IN_WEEK -> "星期${CourseDay.fromIsoWeekday(date.dayOfWeek.isoDayNumber).description}"
    else -> "${date.month.number}月${date.day}日"
}
