package org.mpc.presentation.views.courseFilters

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CourseTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private val monday1 = CourseTime(CourseDay.MONDAY, CoursePeriod.ONE)
private val monday2 = CourseTime(CourseDay.MONDAY, CoursePeriod.TWO)
private val tuesday1 = CourseTime(CourseDay.TUESDAY, CoursePeriod.ONE)

val slotStrokeTests by testSuite {
    test("a tap toggles the touched slot") {
        assertEquals(setOf(monday1, monday2), SlotStroke(setOf(monday1), monday2).result)
        assertEquals(emptySet(), SlotStroke(setOf(monday1), monday1).result)
    }

    test("a drag that starts on an empty slot adds every slot it crosses") {
        val stroke = SlotStroke(setOf(tuesday1), monday1)
        stroke.paint(monday2)
        stroke.paint(tuesday1)
        assertEquals(setOf(monday1, monday2, tuesday1), stroke.result)
    }

    test("a drag that starts on a selected slot removes every slot it crosses") {
        val stroke = SlotStroke(setOf(monday1, monday2, tuesday1), monday1)
        stroke.paint(monday2)
        assertFalse(stroke.paint(monday2))
        assertEquals(setOf(tuesday1), stroke.result)
    }
}
