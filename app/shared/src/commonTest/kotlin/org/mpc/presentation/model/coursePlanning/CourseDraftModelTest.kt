package org.mpc.presentation.model.coursePlanning

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CoursePlan
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.PasswordCardType
import org.mpc.domain.model.addCourse
import org.mpc.domain.model.removeCourse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SEMESTER = "115-1"

private data class CoursePlanFixture(
    val emptyPlan: CoursePlan,
    val freshmanPhysicalEducation: CourseSummary,
    val serviceLearning: CourseSummary,
)

private fun coursePlanFixture() = CoursePlanFixture(
    emptyPlan =
    CoursePlan(
        semester = SEMESTER,
        selectedCourses = emptyMap(),
    ),
    freshmanPhysicalEducation =
    CourseSummary(
        serialNo = CourseSerialNo("01001"),
        classNo = "PE1011-A",
        title = "大一體育",
        credit = 0.0,
        passwordCard = PasswordCardType.OPTIONAL,
        teachers = listOf("沈淑貞"),
        classTimes =
        listOf(
            CourseTime(CourseDay.MONDAY, CoursePeriod.THREE),
            CourseTime(CourseDay.MONDAY, CoursePeriod.FOUR),
        ),
        limitCnt = 0,
        admitCnt = 0,
        waitCnt = 0,
        collegeName = "中心、處室",
        departmentName = "體育室",
        courseType = CourseType.REQUIRED,
        detailUrl = "https://cis.ncu.edu.tw/Course/main/support/courseDetail.html?crs=01001",
    ),
    serviceLearning =
    CourseSummary(
        serialNo = CourseSerialNo("08025"),
        classNo = "SC0003-1",
        title = "服務學習課程",
        credit = 0.0,
        passwordCard = PasswordCardType.OPTIONAL,
        teachers = listOf("孫致文", "李元皓", "王矞慈"),
        classTimes =
        listOf(
            CourseTime(CourseDay.MONDAY, CoursePeriod.A),
            CourseTime(CourseDay.MONDAY, CoursePeriod.B),
        ),
        limitCnt = 0,
        admitCnt = 0,
        waitCnt = 0,
        collegeName = "中心、處室",
        departmentName = "學務處-服務學習發展中心",
        courseType = CourseType.REQUIRED,
        detailUrl = "https://cis.ncu.edu.tw/Course/main/support/courseDetail.html?crs=08025",
    ),
)

val courseDraftModelTests by testSuite {
    testFixture {
        coursePlanFixture()
    } asContextForEach {
        test("adding a course selects it by serial number") {
            val updatedPlan = emptyPlan.addCourse(freshmanPhysicalEducation)

            assertEquals(
                expected = setOf(freshmanPhysicalEducation.serialNo),
                actual = updatedPlan.selectedCourses.keys,
            )
            assertTrue(emptyPlan.selectedCourses.isEmpty())
        }

        test("adding two courses keeps both selections") {
            val updatedPlan =
                emptyPlan
                    .addCourse(freshmanPhysicalEducation)
                    .addCourse(serviceLearning)

            assertEquals(
                setOf(freshmanPhysicalEducation.serialNo, serviceLearning.serialNo),
                updatedPlan.selectedCourses.keys,
            )

            assertEquals(
                setOf(freshmanPhysicalEducation, serviceLearning),
                updatedPlan.selectedCourses.values.toSet(),
            )
        }

        test("removing a course keeps the other selection") {
            val selectedPlan =
                emptyPlan
                    .addCourse(freshmanPhysicalEducation)
                    .addCourse(serviceLearning)

            val updatedPlan = selectedPlan.removeCourse(freshmanPhysicalEducation)

            assertEquals(
                mapOf(serviceLearning.serialNo to serviceLearning),
                updatedPlan.selectedCourses,
            )
        }
    }
}
