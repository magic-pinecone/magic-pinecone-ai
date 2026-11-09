package org.mpc.presentation.formatting

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.SeatAvailability
import org.mpc.domain.model.SlotMatch
import kotlin.test.assertEquals

private val monday1 = CourseTime(CourseDay.MONDAY, CoursePeriod.ONE)
private val wednesday5 = CourseTime(CourseDay.WEDNESDAY, CoursePeriod.FIVE)

val courseFilterKeyTests by testSuite {
    test("distinct filters get distinct keys") {
        val filters = listOf(
            CourseFilter.Teacher("數學系"),
            CourseFilter.Department("數學系"),
            CourseFilter.College("數學系"),
            CourseFilter.Type(CourseType.REQUIRED),
            CourseFilter.Type(CourseType.ELECTIVE),
            CourseFilter.Credits(3.0),
            CourseFilter.Credits(1.5),
            CourseFilter.Seats(SeatAvailability.OPEN),
            CourseFilter.Seats(SeatAvailability.FULL),
            CourseFilter.Schedule(setOf(monday1), SlotMatch.FITS_WITHIN),
            CourseFilter.Schedule(setOf(monday1), SlotMatch.OVERLAPS),
            CourseFilter.Schedule(setOf(monday1, wednesday5), SlotMatch.FITS_WITHIN),
            CourseFilter.Schedule(emptySet(), SlotMatch.FITS_WITHIN),
        )
        assertEquals(filters.size, filters.map { it.stableKey }.toSet().size)
    }

    test("equal filters share a key") {
        assertEquals(CourseFilter.Credits(3.0).stableKey, CourseFilter.Credits(3.0).stableKey)
        assertEquals(
            CourseFilter.Schedule(linkedSetOf(monday1, wednesday5), SlotMatch.FITS_WITHIN).stableKey,
            CourseFilter.Schedule(linkedSetOf(wednesday5, monday1), SlotMatch.FITS_WITHIN).stableKey,
        )
    }
}
