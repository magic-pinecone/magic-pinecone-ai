package org.mpc.presentation.formatting

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.schedule.ClassMeeting
import org.mpc.domain.schedule.UpcomingMeeting
import org.mpc.domain.schedule.at
import org.mpc.domain.schedule.scheduleCourse
import kotlin.test.assertEquals

private val course = scheduleCourse("10001", "微積分", *at(CourseDay.MONDAY, CoursePeriod.ONE))

private fun meeting(
    day: CourseDay,
    first: CoursePeriod,
    last: CoursePeriod = first,
) = ClassMeeting(course, day, first, last)

private fun upcoming(
    meeting: ClassMeeting,
    daysFromToday: Int,
    inProgress: Boolean = false,
) = UpcomingMeeting(meeting, daysFromToday, inProgress)

val scheduleFormattersTests by testSuite {
    test("time range uses 24-hour clock with padding") {
        assertEquals("08:00–09:50", meeting(CourseDay.MONDAY, CoursePeriod.ONE, CoursePeriod.TWO).timeRangeLabel())
        assertEquals("12:00–12:50", meeting(CourseDay.MONDAY, CoursePeriod.NOON).timeRangeLabel())
    }

    test("period label shows a range or a single period") {
        assertEquals("第 1–2 節", meeting(CourseDay.MONDAY, CoursePeriod.ONE, CoursePeriod.TWO).periodLabel())
        assertEquals("第 Z 節", meeting(CourseDay.MONDAY, CoursePeriod.NOON).periodLabel())
    }

    test("when label names now, today, tomorrow, weekday and next week") {
        val monday8 = meeting(CourseDay.MONDAY, CoursePeriod.ONE)
        assertEquals("現在", upcoming(monday8, 0, inProgress = true).whenLabel())
        assertEquals("今天 08:00", upcoming(monday8, 0).whenLabel())
        assertEquals("明天 08:00", upcoming(meeting(CourseDay.TUESDAY, CoursePeriod.ONE), 1).whenLabel())
        assertEquals("星期三 10:00", upcoming(meeting(CourseDay.WEDNESDAY, CoursePeriod.THREE), 2).whenLabel())
        assertEquals("下星期一 08:00", upcoming(monday8, 7).whenLabel())
    }
}
