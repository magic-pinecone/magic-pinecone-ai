package org.mpc.domain.schedule

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CourseTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val calculus = scheduleCourse("10001", "微積分", *at(CourseDay.MONDAY, CoursePeriod.ONE, CoursePeriod.TWO), *at(CourseDay.WEDNESDAY, CoursePeriod.THREE))
private val physics = scheduleCourse("10002", "物理", *at(CourseDay.MONDAY, CoursePeriod.FIVE))

val classScheduleTests by testSuite {
    test("period clock times follow the NCU guide") {
        assertEquals(clock(8) to clock(8, 50), CoursePeriod.ONE.startMinute to CoursePeriod.ONE.endMinute)
        assertEquals(clock(12) to clock(12, 50), CoursePeriod.NOON.startMinute to CoursePeriod.NOON.endMinute)
        assertEquals(clock(13) to clock(13, 50), CoursePeriod.FIVE.startMinute to CoursePeriod.FIVE.endMinute)
        assertEquals(clock(18) to clock(18, 50), CoursePeriod.A.startMinute to CoursePeriod.A.endMinute)
        assertEquals(clock(21) to clock(21, 50), CoursePeriod.D.startMinute to CoursePeriod.D.endMinute)
    }

    test("consecutive periods of one course merge into one meeting") {
        val meetings = schedulePlan(calculus).meetingsOn(CourseDay.MONDAY)
        assertEquals(1, meetings.size)
        assertEquals(CoursePeriod.ONE, meetings[0].firstPeriod)
        assertEquals(CoursePeriod.TWO, meetings[0].lastPeriod)
        assertEquals(clock(8), meetings[0].startMinute)
        assertEquals(clock(9, 50), meetings[0].endMinute)
    }

    test("non-consecutive periods stay separate meetings") {
        val split = scheduleCourse("10003", "實驗", *at(CourseDay.TUESDAY, CoursePeriod.ONE, CoursePeriod.THREE))
        val meetings = schedulePlan(split).meetingsOn(CourseDay.TUESDAY)
        assertEquals(listOf(clock(8), clock(10)), meetings.map { it.startMinute })
        assertEquals(listOf(clock(8, 50), clock(10, 50)), meetings.map { it.endMinute })
    }

    test("noon period sits between 4 and 5 and joins both neighbours") {
        val lunch = scheduleCourse("10004", "午間", *at(CourseDay.FRIDAY, CoursePeriod.FIVE, CoursePeriod.NOON, CoursePeriod.FOUR))
        val other = scheduleCourse("10005", "下午", *at(CourseDay.FRIDAY, CoursePeriod.SEVEN))
        val meetings = schedulePlan(other, lunch).meetingsOn(CourseDay.FRIDAY)
        assertEquals(listOf("10004", "10005"), meetings.map { it.course.serialNumber })
        assertEquals(CoursePeriod.FOUR, meetings[0].firstPeriod)
        assertEquals(CoursePeriod.FIVE, meetings[0].lastPeriod)
        assertEquals(clock(11) to clock(13, 50), meetings[0].startMinute to meetings[0].endMinute)
    }

    test("a course lists its weekly meetings Monday first") {
        val meetings = calculus.meetings()
        assertEquals(listOf(CourseDay.MONDAY, CourseDay.WEDNESDAY), meetings.map { it.day })
        assertEquals(listOf(CoursePeriod.ONE to CoursePeriod.TWO, CoursePeriod.THREE to CoursePeriod.THREE), meetings.map { it.firstPeriod to it.lastPeriod })
    }

    test("meetings are sorted by start across courses") {
        val meetings = schedulePlan(physics, calculus).meetingsOn(CourseDay.MONDAY)
        assertEquals(listOf("10001", "10002"), meetings.map { it.course.serialNumber })
    }

    test("unknown day never produces meetings") {
        val unscheduled = scheduleCourse("10006", "彈性", *at(CourseDay.UNKNOWN, CoursePeriod.ONE))
        val plan = schedulePlan(unscheduled)
        assertTrue(plan.meetingsOn(CourseDay.UNKNOWN).isEmpty())
        assertNull(plan.nextMeeting(CourseDay.MONDAY, 0))
        assertNull(plan.nextMeeting(CourseDay.UNKNOWN, 0))
    }

    test("empty plan has no next meeting") {
        assertNull(schedulePlan().nextMeeting(CourseDay.MONDAY, clock(9)))
    }

    test("meeting in progress wins over later ones") {
        val next = schedulePlan(calculus, physics).nextMeeting(CourseDay.MONDAY, clock(9, 30))
        assertEquals("10001", next?.meeting?.course?.serialNumber)
        assertTrue(next!!.inProgress)
        assertEquals(0, next.daysFromToday)
    }

    test("a meeting ends at its last period end") {
        val next = schedulePlan(calculus, physics).nextMeeting(CourseDay.MONDAY, clock(9, 50))
        assertEquals("10002", next?.meeting?.course?.serialNumber)
        assertFalse(next!!.inProgress)
    }

    test("later meeting today is upcoming") {
        val next = schedulePlan(calculus, physics).nextMeeting(CourseDay.MONDAY, clock(11))
        assertEquals("10002", next?.meeting?.course?.serialNumber)
        assertEquals(0, next?.daysFromToday)
    }

    test("wraps to a following day once today is over") {
        val next = schedulePlan(calculus, physics).nextMeeting(CourseDay.MONDAY, clock(15))
        assertEquals(CourseDay.WEDNESDAY, next?.meeting?.day)
        assertEquals(2, next?.daysFromToday)
    }

    test("wraps across the week end") {
        val next = schedulePlan(calculus, physics).nextMeeting(CourseDay.WEDNESDAY, clock(12))
        assertEquals(CourseDay.MONDAY, next?.meeting?.day)
        assertEquals(5, next?.daysFromToday)
        assertEquals(1, schedulePlan(calculus).nextMeeting(CourseDay.SUNDAY, 0)?.daysFromToday)
    }

    test("same weekday next week when nothing else is scheduled") {
        val onlyMonday = schedulePlan(physics)
        val next = onlyMonday.nextMeeting(CourseDay.MONDAY, clock(15))
        assertEquals(7, next?.daysFromToday)
        assertEquals(CourseDay.MONDAY, next?.meeting?.day)
    }

    test("iso weekday maps to course days") {
        assertEquals(CourseDay.MONDAY, CourseDay.fromIsoWeekday(1))
        assertEquals(CourseDay.SUNDAY, CourseDay.fromIsoWeekday(7))
        assertEquals(CourseDay.UNKNOWN, CourseDay.fromIsoWeekday(0))
        assertEquals(CourseDay.UNKNOWN, CourseDay.fromIsoWeekday(8))
    }

    test("course time pairs are unaffected by other days") {
        val mixed = scheduleCourse("10007", "混合", CourseTime(CourseDay.MONDAY, CoursePeriod.ONE), CourseTime(CourseDay.TUESDAY, CoursePeriod.TWO))
        assertEquals(1, schedulePlan(mixed).meetingsOn(CourseDay.TUESDAY).size)
    }
}
