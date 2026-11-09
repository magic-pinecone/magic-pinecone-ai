package org.mpc.presentation

import androidx.compose.runtime.saveable.SaverScope
import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.PasswordCardType
import org.mpc.presentation.views.courseDetails.CourseDetailsSelection
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

val courseDetailsSelectionSaverTests by testSuite {
    test("an open catalog sheet restores its lookup key without saving the summary") {
        // Catalog courses may not be in the selected plan, so restoration must
        // retain enough information to fetch the course independently.
        val course = CourseSummary(
            serialNo = CourseSerialNo("01001"),
            classNo = "PE1011-A",
            title = "大一體育",
            credit = 0.0,
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
        val scope = SaverScope { it is String }
        val saved = with(CourseDetailsSelectionSaver) {
            scope.save(CourseDetailsSelection("115-1", course))
        }
        assertNotNull(saved)
        val restored = CourseDetailsSelectionSaver.restore(saved)

        assertNotNull(restored)
        assertEquals("115-1", restored.semester)
        assertEquals("01001", restored.serialNumber)
        assertNull(restored.course)
    }

    test("a dismissed sheet has no saved selection") {
        val saved = with(CourseDetailsSelectionSaver) {
            SaverScope { it is String }.save(null)
        }
        assertNull(saved)
    }
}
