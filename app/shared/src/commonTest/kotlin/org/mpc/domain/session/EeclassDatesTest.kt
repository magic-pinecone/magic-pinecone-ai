package org.mpc.domain.session

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val november = LocalDate(2026, 11, 9)

val eeclassDatesTests by testSuite {
    test("a full date and a date-time read as written") {
        assertEquals(LocalDateTime(2026, 10, 20, 0, 0), eeclassDateTime("2026-10-20", november))
        assertEquals(LocalDateTime(2025, 12, 30, 23, 59), eeclassDateTime(" 2025-12-30 23:59 ", november))
    }

    test("a date without a year takes the year nearest today") {
        assertEquals(LocalDateTime(2026, 11, 16, 23, 59), eeclassDateTime("11-16 23:59", november))
        assertEquals(LocalDateTime(2027, 1, 5, 12, 0), eeclassDateTime("01-05 12:00", LocalDate(2026, 12, 28)))
        assertEquals(LocalDateTime(2026, 12, 30, 23, 59), eeclassDateTime("12-30 23:59", LocalDate(2027, 1, 3)))
    }

    test("text that is not a date, or an impossible one, is null") {
        assertNull(eeclassDateTime("", november))
        assertNull(eeclassDateTime("3 天前", november))
        assertNull(eeclassDateTime("02-30 10:00", november))
        assertNull(eeclassDateTime("11-16 25:00", november))
    }
}
