package org.mpc.presentation.formatting

import de.infix.testBalloon.framework.core.testSuite
import dev.icerock.moko.resources.desc.PluralFormatted
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.SeatAvailability
import org.mpc.domain.model.SlotMatch
import org.mpc.shared.MR
import org.mpc.shared.catalog_label_credits
import org.mpc.shared.catalog_label_schedule
import org.mpc.shared.catalog_label_teacher
import org.mpc.shared.catalog_seats_full
import org.mpc.shared.catalog_type_required
import kotlin.test.assertEquals

val courseFilterLabelTests by testSuite {
    test("name filters carry their kind") {
        assertEquals("老師：葉鴻國", CourseFilter.Teacher("葉鴻國").label())
        assertEquals("系所：數學系", CourseFilter.Department("數學系").label())
        assertEquals("學院：理學院", CourseFilter.College("理學院").label())
    }

    test("type and seat filters read as plain words") {
        assertEquals("必修", CourseFilter.Type(CourseType.REQUIRED).label())
        assertEquals("選修", CourseFilter.Type(CourseType.ELECTIVE).label())
        assertEquals("尚有名額", CourseFilter.Seats(SeatAvailability.OPEN).label())
        assertEquals("已額滿", CourseFilter.Seats(SeatAvailability.FULL).label())
    }

    test("credit labels drop a trailing zero") {
        assertEquals("3 學分", CourseFilter.Credits(3.0).label())
        assertEquals("1.5 學分", CourseFilter.Credits(1.5).label())
    }

    test("schedule label counts slots") {
        val slots = setOf(
            CourseTime(CourseDay.MONDAY, CoursePeriod.ONE),
            CourseTime(CourseDay.WEDNESDAY, CoursePeriod.FIVE),
        )
        assertEquals("時段：2 節", CourseFilter.Schedule(slots, SlotMatch.FITS_WITHIN).label())
    }

    test("descs resolve each filter to its own resource") {
        assertEquals(
            StringDesc.ResourceFormatted(MR.strings.catalog_label_teacher, "葉鴻國"),
            CourseFilter.Teacher("葉鴻國").labelDesc(),
        )
        assertEquals(StringDesc.Resource(MR.strings.catalog_type_required), CourseFilter.Type(CourseType.REQUIRED).labelDesc())
        assertEquals(StringDesc.Resource(MR.strings.catalog_seats_full), CourseFilter.Seats(SeatAvailability.FULL).labelDesc())
        assertEquals(
            StringDesc.ResourceFormatted(MR.strings.catalog_label_credits, "1.5"),
            CourseFilter.Credits(1.5).labelDesc(),
        )
        val slots = setOf(CourseTime(CourseDay.MONDAY, CoursePeriod.ONE))
        assertEquals(
            StringDesc.PluralFormatted(MR.plurals.catalog_label_schedule, 1, 1),
            CourseFilter.Schedule(slots, SlotMatch.FITS_WITHIN).labelDesc(),
        )
    }
}
