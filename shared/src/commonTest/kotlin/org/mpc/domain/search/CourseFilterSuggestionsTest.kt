package org.mpc.domain.search

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.PasswordCardType
import org.mpc.domain.model.SeatAvailability
import kotlin.test.assertEquals

private fun course(
    serialNo: String,
    teachers: List<String> = emptyList(),
    departmentName: String = "數學系",
    collegeName: String = "理學院",
    credit: Double = 3.0,
) = CourseSummary(
    serialNo = CourseSerialNo(serialNo),
    classNo = "01",
    title = "課程",
    credit = credit,
    passwordCard = PasswordCardType.NONE,
    teachers = teachers,
    classTimes = emptyList(),
    limitCnt = 50,
    admitCnt = 0,
    waitCnt = 0,
    collegeName = collegeName,
    departmentName = departmentName,
    courseType = CourseType.REQUIRED,
    detailUrl = "",
)

private val catalog = listOf(
    course("10001", teachers = listOf("葉鴻國", "李大明"), departmentName = "數學系", collegeName = "理學院"),
    course("10002", teachers = listOf("葉鴻國"), departmentName = "物理系", collegeName = "理學院", credit = 1.5),
    course("10003", teachers = listOf("黃葉"), departmentName = "中文系", collegeName = "文學院", credit = 2.0),
)

private fun suggest(
    text: String,
    active: Set<CourseFilter> = emptySet(),
    limit: Int = 8,
) = suggestCourseFilters(catalog, text, active, limit)

val courseFilterSuggestionTests by testSuite {
    test("blank text suggests nothing") {
        assertEquals(emptyList(), suggest(""))
        assertEquals(emptyList(), suggest("   "))
    }

    test("a partial teacher name suggests full names from the catalog once") {
        assertEquals(listOf(CourseFilter.Teacher("葉鴻國")), suggest("鴻"))
    }

    test("names starting with the text rank before names containing it") {
        assertEquals(
            listOf(CourseFilter.Teacher("葉鴻國"), CourseFilter.Teacher("黃葉")),
            suggest("葉"),
        )
    }

    test("shorter names rank first among equal matches") {
        val courses = listOf(course("10001", teachers = listOf("王小明明", "王小明")))
        assertEquals(
            listOf(CourseFilter.Teacher("王小明"), CourseFilter.Teacher("王小明明")),
            suggestCourseFilters(courses, "王", emptySet()),
        )
    }

    test("departments and colleges are suggested from the catalog") {
        assertEquals(listOf(CourseFilter.Department("數學系")), suggest("數學"))
        assertEquals(
            listOf(CourseFilter.College("理學院"), CourseFilter.College("文學院")),
            suggest("學院"),
        )
    }

    test("equal matches follow kind order") {
        val courses = listOf(course("10001", teachers = listOf("理學"), departmentName = "理學", collegeName = "理學"))
        assertEquals(
            listOf(CourseFilter.Teacher("理學"), CourseFilter.Department("理學"), CourseFilter.College("理學")),
            suggestCourseFilters(courses, "理學", emptySet()),
        )
    }

    test("a prefix narrows suggestions to its kind") {
        assertEquals(listOf(CourseFilter.Teacher("葉鴻國"), CourseFilter.Teacher("黃葉")), suggest("老師:葉"))
        assertEquals(listOf(CourseFilter.Teacher("葉鴻國"), CourseFilter.Teacher("黃葉")), suggest("教師:葉"))
        assertEquals(listOf(CourseFilter.Department("物理系")), suggest("系所:物理"))
        assertEquals(listOf(CourseFilter.Department("物理系")), suggest("系:物理"))
        assertEquals(listOf(CourseFilter.College("文學院")), suggest("學院:文"))
        assertEquals(emptyList(), suggest("老師:數學"))
    }

    test("a full-width colon works as a prefix separator") {
        assertEquals(listOf(CourseFilter.Teacher("葉鴻國"), CourseFilter.Teacher("黃葉")), suggest("老師：葉"))
    }

    test("a prefix with no value lists that kind") {
        assertEquals(
            listOf(CourseFilter.Type(CourseType.REQUIRED), CourseFilter.Type(CourseType.ELECTIVE)),
            suggest("類型:"),
        )
    }

    test("course types are suggested with and without a prefix") {
        assertEquals(listOf(CourseFilter.Type(CourseType.REQUIRED)), suggest("必修"))
        assertEquals(listOf(CourseFilter.Type(CourseType.ELECTIVE)), suggest("類型:選"))
    }

    test("a credit number suggests catalog credit values only") {
        assertEquals(listOf(CourseFilter.Credits(3.0)), suggest("3學分"))
        assertEquals(listOf(CourseFilter.Credits(3.0)), suggest("3"))
        assertEquals(listOf(CourseFilter.Credits(1.5)), suggest("學分:1.5"))
        assertEquals(emptyList(), suggest("5學分"))
    }

    test("seat phrases map to seat availability") {
        assertEquals(listOf(CourseFilter.Seats(SeatAvailability.OPEN)), suggest("有名額"))
        assertEquals(listOf(CourseFilter.Seats(SeatAvailability.OPEN)), suggest("尚有名額"))
        assertEquals(listOf(CourseFilter.Seats(SeatAvailability.FULL)), suggest("額滿"))
        assertEquals(listOf(CourseFilter.Seats(SeatAvailability.FULL)), suggest("已額滿"))
        assertEquals(
            listOf(CourseFilter.Seats(SeatAvailability.FULL), CourseFilter.Seats(SeatAvailability.OPEN)),
            suggest("名額:"),
        )
    }

    test("active filters are not suggested again") {
        val active = setOf<CourseFilter>(CourseFilter.Teacher("葉鴻國"))
        assertEquals(listOf(CourseFilter.Teacher("黃葉")), suggest("葉", active))
    }

    test("suggestions respect the limit") {
        assertEquals(1, suggest("葉", limit = 1).size)
    }

    test("simplified text finds traditional names") {
        assertEquals(listOf(CourseFilter.Department("數學系")), suggest("数学"))
    }
}
