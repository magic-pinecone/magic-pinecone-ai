package org.mpc.presentation.formatting

import de.infix.testBalloon.framework.core.testSuite
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.schedule.ClassMeeting
import org.mpc.domain.schedule.UpcomingMeeting
import org.mpc.domain.schedule.at
import org.mpc.domain.schedule.scheduleCourse
import org.mpc.shared.MR
import org.mpc.shared.planning_period_range
import org.mpc.shared.planning_period_single
import org.mpc.shared.planning_weekday_monday
import org.mpc.shared.planning_weekday_wednesday
import org.mpc.shared.planning_when_next_week
import org.mpc.shared.planning_when_now
import org.mpc.shared.planning_when_today
import org.mpc.shared.planning_when_tomorrow
import org.mpc.shared.planning_when_weekday
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

    test("period desc shows a range or a single period") {
        assertEquals(
            StringDesc.ResourceFormatted(MR.strings.planning_period_range, "1", "2"),
            meeting(CourseDay.MONDAY, CoursePeriod.ONE, CoursePeriod.TWO).periodDesc(),
        )
        assertEquals(
            StringDesc.ResourceFormatted(MR.strings.planning_period_single, "Z"),
            meeting(CourseDay.MONDAY, CoursePeriod.NOON).periodDesc(),
        )
    }

    test("when desc names now, today, tomorrow, weekday and next week") {
        val monday8 = meeting(CourseDay.MONDAY, CoursePeriod.ONE)
        assertEquals(StringDesc.Resource(MR.strings.planning_when_now), upcoming(monday8, 0, inProgress = true).whenDesc())
        assertEquals(StringDesc.ResourceFormatted(MR.strings.planning_when_today, "08:00"), upcoming(monday8, 0).whenDesc())
        assertEquals(
            StringDesc.ResourceFormatted(MR.strings.planning_when_tomorrow, "08:00"),
            upcoming(meeting(CourseDay.TUESDAY, CoursePeriod.ONE), 1).whenDesc(),
        )
        assertEquals(
            StringDesc.ResourceFormatted(
                MR.strings.planning_when_weekday,
                StringDesc.Resource(MR.strings.planning_weekday_wednesday),
                "10:00",
            ),
            upcoming(meeting(CourseDay.WEDNESDAY, CoursePeriod.THREE), 2).whenDesc(),
        )
        assertEquals(
            StringDesc.ResourceFormatted(
                MR.strings.planning_when_next_week,
                StringDesc.Resource(MR.strings.planning_weekday_monday),
                "08:00",
            ),
            upcoming(monday8, 7).whenDesc(),
        )
    }
}
