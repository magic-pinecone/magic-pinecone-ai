package org.mpc.domain.search

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CourseQuery
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.PasswordCardType
import org.mpc.domain.model.SeatAvailability
import org.mpc.domain.model.SlotMatch
import kotlin.test.assertEquals

private fun course(
    serialNo: String,
    title: String,
    teachers: List<String> = emptyList(),
    departmentName: String = "數學系",
    collegeName: String = "理學院",
    courseType: CourseType = CourseType.REQUIRED,
    classTimes: List<CourseTime> = emptyList(),
    classNo: String = "01",
    credit: Double = 3.0,
    limitCnt: Int = 50,
    admitCnt: Int = 0,
) = CourseSummary(
    serialNo = CourseSerialNo(serialNo),
    classNo = classNo,
    title = title,
    credit = credit,
    passwordCard = PasswordCardType.NONE,
    teachers = teachers,
    classTimes = classTimes,
    limitCnt = limitCnt,
    admitCnt = admitCnt,
    waitCnt = 0,
    collegeName = collegeName,
    departmentName = departmentName,
    courseType = courseType,
    detailUrl = "",
)

private fun search(
    courses: List<CourseSummary>,
    query: CourseQuery,
) = searchCourses(courses, query).map { it.serialNo.value }

private fun query(keyword: String = "") = CourseQuery("115-1", keyword)

private fun filtered(vararg filters: CourseFilter) = CourseQuery("115-1", filters = filters.toSet())

private fun slot(
    day: CourseDay,
    period: CoursePeriod,
) = CourseTime(day, period)

val courseSearchTests by testSuite {
    test("serial number hit outranks title hit") {
        val courses = listOf(
            course("10001", "12345 專題"),
            course("12345", "微積分"),
        )
        assertEquals(listOf("12345", "10001"), search(courses, query("12345")))
    }

    test("title prefix outranks title contains") {
        val courses = listOf(
            course("10001", "應用微積分"),
            course("10002", "微積分"),
            course("10003", "微積分二"),
        )
        assertEquals(listOf("10002", "10003", "10001"), search(courses, query("微積分")))
    }

    test("every keyword token must match some field") {
        val courses = listOf(
            course("10001", "微積分", teachers = listOf("李大明")),
            course("10002", "微積分", teachers = listOf("黃小華")),
            course("10003", "線性代數", teachers = listOf("黃小華")),
        )
        assertEquals(listOf("10002"), search(courses, query("微積分 黃")))
    }

    test("full-width queries match half-width text") {
        val courses = listOf(
            course("10001", "台灣史"),
            course("10002", "Calculus"),
            course("10003", "Algebra"),
        )
        assertEquals(listOf("10002"), search(courses, query("ＣＡＬＣＵＬＵＳ")))
        assertEquals(listOf("10002"), search(courses, query("　calculus　")))
    }

    test("full-width serial number matches") {
        val courses = listOf(course("12345", "微積分"), course("10001", "線性代數"))
        assertEquals(listOf("12345"), search(courses, query("１２３４５")))
    }

    test("variant forms of the same character match in both directions") {
        val courses = listOf(
            course("10001", "台灣史"),
            course("10002", "臺灣文學"),
            course("10003", "裡面的世界"),
            course("10004", "行為科學"),
            course("10005", "Calculus"),
        )
        assertEquals(listOf("10001", "10002"), search(courses, query("臺灣")).sorted())
        assertEquals(listOf("10001", "10002"), search(courses, query("台灣")).sorted())
        assertEquals(listOf("10003"), search(courses, query("裏面")))
        assertEquals(listOf("10003"), search(courses, query("裡面")))
        assertEquals(listOf("10004"), search(courses, query("行爲")))
        assertEquals(listOf("10004"), search(courses, query("行為")))
    }

    test("simplified query matches traditional course fields") {
        val courses = listOf(
            course("10001", "微積分", teachers = listOf("陳大明")),
            course("10002", "線性代數"),
        )
        assertEquals(listOf("10001"), search(courses, query("微积分")))
        assertEquals(listOf("10002"), search(courses, query("线性代数")))
        assertEquals(listOf("10001"), search(courses, filtered(CourseFilter.Teacher("陈大明"))))
    }

    test("roman numeral characters match their letter forms") {
        val courses = listOf(
            course("10001", "微積分II"),
            course("10002", "統計學"),
        )
        assertEquals(listOf("10001"), search(courses, query("微積分Ⅱ")))
        val roman = listOf(course("10001", "微積分Ⅱ"), course("10002", "統計學"))
        assertEquals(listOf("10001"), search(roman, query("微積分II")))
    }

    test("teacher department and college filters narrow the catalog") {
        val courses = listOf(
            course("10001", "A", teachers = listOf("李大明"), departmentName = "數學系"),
            course("10002", "B", teachers = listOf("黃小華"), departmentName = "物理系", collegeName = "理學院"),
            course("10003", "C", teachers = listOf("黃小華"), departmentName = "中文系", collegeName = "文學院"),
        )
        assertEquals(listOf("10002", "10003"), search(courses, filtered(CourseFilter.Teacher("黃小華"))))
        assertEquals(listOf("10003"), search(courses, filtered(CourseFilter.College("文學院"))))
        assertEquals(listOf("10002"), search(courses, filtered(CourseFilter.Department("物理系"))))
        assertEquals(
            listOf("10002"),
            search(courses, filtered(CourseFilter.Teacher("黃小華"), CourseFilter.Department("物理系"))),
        )
    }

    test("name filters match whole catalog names only") {
        val courses = listOf(
            course("10001", "A", teachers = listOf("王明"), departmentName = "數學系", collegeName = "理學院"),
            course("10002", "B", teachers = listOf("王明華"), departmentName = "應用數學系", collegeName = "管理學院"),
        )
        assertEquals(listOf("10001"), search(courses, filtered(CourseFilter.Department("數學系"))))
        assertEquals(listOf("10001"), search(courses, filtered(CourseFilter.Teacher("王明"))))
        assertEquals(listOf("10001"), search(courses, filtered(CourseFilter.College("理學院"))))
        assertEquals(emptyList(), search(courses, filtered(CourseFilter.Department("數學"))))
    }

    test("filters of the same kind match either value") {
        val courses = listOf(
            course("10001", "A", teachers = listOf("李大明")),
            course("10002", "B", teachers = listOf("黃小華")),
            course("10003", "C", teachers = listOf("王小美")),
        )
        assertEquals(
            listOf("10001", "10002"),
            search(courses, filtered(CourseFilter.Teacher("李大明"), CourseFilter.Teacher("黃小華"))),
        )
    }

    test("filters of different kinds must all match") {
        val courses = listOf(
            course("10001", "A", teachers = listOf("李大明"), courseType = CourseType.REQUIRED),
            course("10002", "B", teachers = listOf("李大明"), courseType = CourseType.ELECTIVE),
            course("10003", "C", teachers = listOf("黃小華"), courseType = CourseType.ELECTIVE),
        )
        assertEquals(
            listOf("10002"),
            search(courses, filtered(CourseFilter.Teacher("李大明"), CourseFilter.Type(CourseType.ELECTIVE))),
        )
    }

    test("course type and credits filters keep matching courses only") {
        val courses = listOf(
            course("10001", "A", courseType = CourseType.REQUIRED, credit = 3.0),
            course("10002", "B", courseType = CourseType.ELECTIVE, credit = 1.5),
        )
        assertEquals(listOf("10002"), search(courses, filtered(CourseFilter.Type(CourseType.ELECTIVE))))
        assertEquals(listOf("10002"), search(courses, filtered(CourseFilter.Credits(1.5))))
    }

    test("fits within requires every class time inside the slots") {
        val monday1 = slot(CourseDay.MONDAY, CoursePeriod.ONE)
        val monday2 = slot(CourseDay.MONDAY, CoursePeriod.TWO)
        val wednesday5 = slot(CourseDay.WEDNESDAY, CoursePeriod.FIVE)
        val courses = listOf(
            course("10001", "A", classTimes = listOf(monday1, monday2)),
            course("10002", "B", classTimes = listOf(monday1, wednesday5)),
            course("10003", "C", classTimes = listOf(slot(CourseDay.MONDAY, CoursePeriod.FIVE))),
        )
        val free = CourseFilter.Schedule(setOf(monday1, monday2), SlotMatch.FITS_WITHIN)
        assertEquals(listOf("10001"), search(courses, filtered(free)))
        val crossProduct = CourseFilter.Schedule(setOf(monday1, wednesday5), SlotMatch.FITS_WITHIN)
        assertEquals(listOf("10002"), search(courses, filtered(crossProduct)))
    }

    test("overlaps requires one class time inside the slots") {
        val monday1 = slot(CourseDay.MONDAY, CoursePeriod.ONE)
        val wednesday5 = slot(CourseDay.WEDNESDAY, CoursePeriod.FIVE)
        val courses = listOf(
            course("10001", "A", classTimes = listOf(monday1, slot(CourseDay.TUESDAY, CoursePeriod.TWO))),
            course("10002", "B", classTimes = listOf(slot(CourseDay.MONDAY, CoursePeriod.FIVE))),
            course("10003", "C", classTimes = listOf(wednesday5)),
        )
        val filter = CourseFilter.Schedule(setOf(monday1, wednesday5), SlotMatch.OVERLAPS)
        assertEquals(listOf("10001", "10003"), search(courses, filtered(filter)))
    }

    test("courses without usable class times are excluded by schedule filters") {
        val monday1 = slot(CourseDay.MONDAY, CoursePeriod.ONE)
        val courses = listOf(
            course("10001", "no times"),
            course("10002", "unknown day", classTimes = listOf(slot(CourseDay.UNKNOWN, CoursePeriod.ONE))),
            course("10003", "scheduled", classTimes = listOf(monday1)),
        )
        val everything = CourseDay.entries.flatMap { day -> CoursePeriod.entries.map { slot(day, it) } }.toSet()
        assertEquals(
            listOf("10003"),
            search(courses, filtered(CourseFilter.Schedule(setOf(monday1), SlotMatch.FITS_WITHIN))),
        )
        assertEquals(
            listOf("10003"),
            search(courses, filtered(CourseFilter.Schedule(setOf(monday1), SlotMatch.OVERLAPS))),
        )
        assertEquals(
            listOf("10002", "10003"),
            search(courses, filtered(CourseFilter.Schedule(everything, SlotMatch.OVERLAPS))),
        )
        assertEquals(
            listOf("10003"),
            search(courses, filtered(CourseFilter.Schedule(everything - slot(CourseDay.UNKNOWN, CoursePeriod.ONE), SlotMatch.FITS_WITHIN))),
        )
    }

    test("multiple schedule filters match either") {
        val monday1 = slot(CourseDay.MONDAY, CoursePeriod.ONE)
        val tuesday1 = slot(CourseDay.TUESDAY, CoursePeriod.ONE)
        val courses = listOf(
            course("10001", "A", classTimes = listOf(monday1)),
            course("10002", "B", classTimes = listOf(tuesday1)),
        )
        val filters = arrayOf(
            CourseFilter.Schedule(setOf(monday1), SlotMatch.FITS_WITHIN),
            CourseFilter.Schedule(setOf(tuesday1), SlotMatch.FITS_WITHIN),
        )
        assertEquals(listOf("10001", "10002"), search(courses, filtered(*filters)))
    }

    test("seat filters treat a zero limit as uncapped") {
        val courses = listOf(
            course("10001", "capped open", limitCnt = 50, admitCnt = 49),
            course("10002", "capped full", limitCnt = 50, admitCnt = 50),
            course("10003", "over capacity", limitCnt = 50, admitCnt = 60),
            course("10004", "uncapped", limitCnt = 0, admitCnt = 51),
        )
        assertEquals(
            listOf("10001", "10004"),
            search(courses, filtered(CourseFilter.Seats(SeatAvailability.OPEN))),
        )
        assertEquals(
            listOf("10002", "10003"),
            search(courses, filtered(CourseFilter.Seats(SeatAvailability.FULL))),
        )
    }

    test("class number exact match outranks title and prefix matches") {
        val courses = listOf(
            course("10001", "MA1001-C 導論"),
            course("10002", "微積分", classNo = "MA1001-C"),
            course("10003", "微積分", classNo = "MA1001-D"),
        )
        assertEquals(listOf("10002", "10001"), search(courses, query("ma1001-c")))
    }

    test("class number prefix finds every class of the course") {
        val courses = listOf(
            course("10001", "微積分", classNo = "MA1001-A"),
            course("10002", "微積分", classNo = "MA1001-B"),
            course("10003", "MA1001 解析", classNo = "MA2002-A"),
            course("10004", "線性代數", classNo = "MA1002-A"),
        )
        assertEquals(listOf("10001", "10002", "10003"), search(courses, query("MA1001")))
    }

    test("serial number outranks class number") {
        val courses = listOf(
            course("10001", "A", classNo = "12345"),
            course("12345", "B"),
        )
        assertEquals(listOf("12345", "10001"), search(courses, query("12345")))
    }

    test("blank query preserves catalog order") {
        val courses = listOf(course("10003", "C"), course("10001", "A"), course("10002", "B"))
        assertEquals(listOf("10003", "10001", "10002"), search(courses, query("   ")))
    }

    test("keyword matches teacher and department when title does not") {
        val courses = listOf(
            course("10001", "A", teachers = listOf("黃小華")),
            course("10002", "B", departmentName = "黃金系"),
            course("10003", "C"),
        )
        assertEquals(listOf("10001", "10002"), search(courses, query("黃")))
    }

    test("equal scores keep catalog order") {
        val courses = listOf(
            course("10003", "微積分甲"),
            course("10001", "微積分乙"),
            course("10002", "微積分丙"),
        )
        assertEquals(listOf("10003", "10001", "10002"), search(courses, query("微積分")))
    }
}
