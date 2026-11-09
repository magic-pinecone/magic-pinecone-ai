package org.mpc.domain.eeclass

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.mpc.domain.model.AcrossCourses
import org.mpc.domain.model.EeclassBulletin
import org.mpc.domain.model.EeclassBulletinNumber
import org.mpc.domain.model.EeclassCourse
import org.mpc.domain.model.EeclassCourseItem
import org.mpc.domain.model.EeclassCourseNumber
import org.mpc.domain.model.EeclassHomework
import org.mpc.domain.session.SitePath
import kotlin.test.assertEquals

private val cloud = EeclassCourse(EeclassCourseNumber("40001"), "雲端平台")
private val calculus = EeclassCourse(EeclassCourseNumber("40002"), "微積分")

private fun homework(title: String, due: String) = EeclassHomework(title, "", due, "", SitePath.of("/course/homework/1"))

private fun bulletin(title: String, posted: String) = EeclassBulletin(EeclassBulletinNumber("1"), title, "", posted)

val eeclassAcrossCoursesTests by testSuite {
    test("homework due after now comes soonest first, across courses") {
        val homework = AcrossCourses(
            listOf(
                EeclassCourseItem(cloud, homework("HW 3", "11-20 23:59")),
                EeclassCourseItem(cloud, homework("HW 1", "11-01 23:59")),
                EeclassCourseItem(calculus, homework("習題 5", "11-10 09:00")),
                EeclassCourseItem(calculus, homework("期末報告", "無")),
            ),
            unread = emptyList(),
        )

        assertEquals(
            listOf("習題 5", "HW 3"),
            homework.dueAfter(LocalDateTime(2026, 11, 9, 20, 0)).map { it.homework.title },
        )
    }

    test("a deadline earlier today has passed") {
        val homework = AcrossCourses(listOf(EeclassCourseItem(cloud, homework("HW 2", "11-09 12:00"))), emptyList())
        assertEquals(emptyList(), homework.dueAfter(LocalDateTime(2026, 11, 9, 12, 0)))
    }

    test("announcements come newest first, across courses") {
        val bulletins = AcrossCourses(
            listOf(
                EeclassCourseItem(cloud, bulletin("期中考", "2026-10-20")),
                EeclassCourseItem(calculus, bulletin("停課", "2026-11-06")),
                EeclassCourseItem(cloud, bulletin("補課", "11-08")),
            ),
            unread = emptyList(),
        )

        assertEquals(
            listOf("補課" to LocalDate(2026, 11, 8), "停課" to LocalDate(2026, 11, 6), "期中考" to LocalDate(2026, 10, 20)),
            bulletins.newestFirst(LocalDate(2026, 11, 9)).map { it.bulletin.title to it.posted },
        )
    }
}
