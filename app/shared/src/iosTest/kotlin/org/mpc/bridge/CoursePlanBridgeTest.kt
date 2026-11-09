package org.mpc.bridge

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.PasswordCardType
import kotlin.test.assertEquals

val coursePlanBridgeTests by testSuite {
    test("plan snapshot replays the latest value and suppresses equal updates") {
        runTest {
            val bridge = CoursePlanBridge()
            val selectedCourse = courseSummary("01001")
            val otherCourse = courseSummary("08025")
            val received = mutableListOf<CoursePlanSnapshot>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                bridge.planSnapshot.collect { received.add(it) }
            }

            val selectedAndEditable = CoursePlanSnapshot(listOf(selectedCourse), canEditPlan = true)
            bridge.updateSelectedCourses(listOf(selectedCourse), canEditPlan = false)
            testScheduler.runCurrent()
            bridge.updateSelectedCourses(listOf(selectedCourse), canEditPlan = false)
            testScheduler.runCurrent()
            bridge.updateSelectedCourses(listOf(selectedCourse), canEditPlan = true)
            testScheduler.runCurrent()

            assertEquals(
                listOf(
                    CoursePlanSnapshot(),
                    CoursePlanSnapshot(listOf(selectedCourse), canEditPlan = false),
                    selectedAndEditable,
                ),
                received,
            )

            collector.cancelAndJoin()
            bridge.updateSelectedCourses(listOf(otherCourse), canEditPlan = false)
            testScheduler.runCurrent()

            val restartedCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                bridge.planSnapshot.collect { received.add(it) }
            }
            assertEquals(
                listOf(
                    CoursePlanSnapshot(),
                    CoursePlanSnapshot(listOf(selectedCourse), canEditPlan = false),
                    selectedAndEditable,
                    CoursePlanSnapshot(listOf(otherCourse), canEditPlan = false),
                ),
                received,
            )
            restartedCollector.cancelAndJoin()
        }
    }

    test("queued save and toggle requests are delivered in order") {
        runTest {
            val bridge = CoursePlanBridge()
            val firstCourse = courseSummary("01001")
            val secondCourse = courseSummary("08025")

            bridge.requestSave()
            bridge.requestToggleCourse(firstCourse)
            bridge.requestSave()
            bridge.requestToggleCourse(secondCourse)

            assertEquals(Unit, bridge.saveRequests.first())
            assertEquals(Unit, bridge.saveRequests.first())
            assertEquals(firstCourse, bridge.toggleRequests.first())
            assertEquals(secondCourse, bridge.toggleRequests.first())
        }
    }
}

private fun courseSummary(serialNumber: String) = CourseSummary(
    serialNo = CourseSerialNo(serialNumber),
    classNo = "CS1001-A",
    title = "課程",
    credit = 2.0,
    passwordCard = PasswordCardType.NONE,
    teachers = emptyList(),
    classTimes = emptyList(),
    limitCnt = 0,
    admitCnt = 0,
    waitCnt = 0,
    collegeName = "",
    departmentName = "",
    courseType = CourseType.REQUIRED,
    detailUrl = "",
)
