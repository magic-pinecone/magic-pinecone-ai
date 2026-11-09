package org.mpc.presentation.formatting

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.mpc.domain.eeclass.DueHomework
import org.mpc.domain.eeclass.PostedBulletin
import org.mpc.domain.model.EeclassBulletin
import org.mpc.domain.model.EeclassBulletinNumber
import org.mpc.domain.model.EeclassCourse
import org.mpc.domain.model.EeclassCourseNumber
import org.mpc.domain.model.EeclassHomework
import org.mpc.domain.session.SitePath
import kotlin.test.assertEquals

// 2026-11-09 is a Monday.
private val monday = LocalDate(2026, 11, 9)
private val course = EeclassCourse(EeclassCourseNumber("40001"), "雲端平台")

private fun due(at: LocalDateTime) = DueHomework(course, EeclassHomework("HW", "", "", "", SitePath.of("/course/homework/1")), at)

private fun posted(on: LocalDate) = PostedBulletin(course, EeclassBulletin(EeclassBulletinNumber("1"), "公告", "", ""), on)

val eeclassFormattersTests by testSuite {
    test("deadlines read relative to today within a week, then by date") {
        assertEquals("今天 23:59", due(LocalDateTime(2026, 11, 9, 23, 59)).dueLabel(monday))
        assertEquals("明天 09:00", due(LocalDateTime(2026, 11, 10, 9, 0)).dueLabel(monday))
        assertEquals("星期五 23:59", due(LocalDateTime(2026, 11, 13, 23, 59)).dueLabel(monday))
        assertEquals("11月16日 08:05", due(LocalDateTime(2026, 11, 16, 8, 5)).dueLabel(monday))
    }

    test("announcements read as today, yesterday, or a date") {
        assertEquals("今天", posted(monday).postedLabel(monday))
        assertEquals("昨天", posted(LocalDate(2026, 11, 8)).postedLabel(monday))
        assertEquals("11月6日", posted(LocalDate(2026, 11, 6)).postedLabel(monday))
    }
}
