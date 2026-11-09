package org.mpc.domain.schedule

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.datetime.LocalDate
import org.mpc.domain.model.AcademicTerm
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePeriod
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 2026-09-07 is a Monday.
private val twoWeeks = AcademicTerm("115-1", LocalDate(2026, 9, 7), LocalDate(2026, 9, 18))

val classOccurrencesTests by testSuite {
    test("a weekly meeting occurs on each matching day of the term") {
        val calculus = scheduleCourse("10001", "微積分", *at(CourseDay.MONDAY, CoursePeriod.ONE, CoursePeriod.TWO))
        val occurrences = schedulePlan(calculus).occurrencesIn(twoWeeks)
        assertEquals(listOf(LocalDate(2026, 9, 7), LocalDate(2026, 9, 14)), occurrences.map { it.date })
        assertEquals(clock(8) to clock(9, 50), occurrences[0].meeting.startMinute to occurrences[0].meeting.endMinute)
    }

    test("occurrences sort by date, then start time") {
        val late = scheduleCourse("10002", "物理", *at(CourseDay.TUESDAY, CoursePeriod.FIVE))
        val early = scheduleCourse("10003", "化學", *at(CourseDay.TUESDAY, CoursePeriod.ONE), *at(CourseDay.FRIDAY, CoursePeriod.ONE))
        val occurrences = schedulePlan(late, early).occurrencesIn(twoWeeks)
        assertEquals(
            listOf("09-08 化學", "09-08 物理", "09-11 化學", "09-15 化學", "09-15 物理", "09-18 化學"),
            occurrences.map { "${it.date.toString().drop(5)} ${it.meeting.course.title}" },
        )
    }

    test("the term's last day is included and courses without a weekday never occur") {
        val friday = scheduleCourse("10004", "實驗", *at(CourseDay.FRIDAY, CoursePeriod.THREE))
        val unscheduled = scheduleCourse("10005", "專題", *at(CourseDay.UNKNOWN, CoursePeriod.ONE))
        val occurrences = schedulePlan(friday, unscheduled).occurrencesIn(twoWeeks)
        assertEquals(LocalDate(2026, 9, 18), occurrences.last().date)
        assertTrue(occurrences.none { it.meeting.course.title == "專題" })
    }
}
