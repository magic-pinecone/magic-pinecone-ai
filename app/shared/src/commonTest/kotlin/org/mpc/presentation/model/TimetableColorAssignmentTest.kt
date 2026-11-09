package org.mpc.presentation.model

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.PasswordCardType
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun summary(classNo: String, type: CourseType = CourseType.REQUIRED) = CourseSummary(
    serialNo = CourseSerialNo("01001"),
    classNo = classNo,
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
    courseType = type,
    detailUrl = "",
)

val timetableColorAssignmentTests by testSuite {
    test("common course prefixes override the required or elective flag") {
        val examples = mapOf(
            "CC1001-A" to TimetableCourseCategory.CORE_GENERAL_EDUCATION,
            "GS4719-*" to TimetableCourseCategory.GENERAL_EDUCATION,
            "MN1001" to TimetableCourseCategory.NATIONAL_DEFENSE,
            "CL0201-7" to TimetableCourseCategory.CHINESE,
            "LN1001-A" to TimetableCourseCategory.ENGLISH,
            "HI0001" to TimetableCourseCategory.HISTORY,
            "PE1011-A" to TimetableCourseCategory.PHYSICAL_EDUCATION,
            "SC0003-1" to TimetableCourseCategory.SERVICE_LEARNING,
            "RV0001" to TimetableCourseCategory.RESERVED_TIME,
        )
        examples.forEach { (code, category) ->
            CourseType.entries.forEach { type ->
                assertEquals(category, summary(code, type).timetableCategory(), code)
            }
        }
    }

    test("department courses and malformed codes keep their required elective fallback") {
        listOf("CL1001", "LN2901", "HI5001", "GSOLAB", "", "CS1001").forEach { code ->
            assertEquals(TimetableCourseCategory.REQUIRED, summary(code).timetableCategory(), code)
            assertEquals(TimetableCourseCategory.ELECTIVE, summary(code, CourseType.ELECTIVE).timetableCategory(), code)
            assertEquals(TimetableCourseCategory.UNKNOWN, summary(code, CourseType.UNKNOWN).timetableCategory(), code)
        }
        assertEquals(TimetableCourseCategory.GENERAL_EDUCATION, summary(" gs4719-a ").timetableCategory())
    }

    test("adding removing and reordering courses preserve existing shuffle assignments") {
        val first = assignTimetableColorSlots(emptyMap(), listOf("01002", "01001"))
        val edited = assignTimetableColorSlots(first, listOf("01003", "01002"))
        val restored = assignTimetableColorSlots(edited, listOf("01002", "01001", "01003"))
        assertEquals(first["01001"], restored["01001"])
        assertEquals(first["01002"], restored["01002"])
        assertEquals(edited, restored)
        assertEquals(3, restored.values.distinct().size)
    }

    test("duplicate meetings use one assignment and input order does not affect initial colors") {
        val first = assignTimetableColorSlots(emptyMap(), listOf("01001", "01002", "01001"))
        val reordered = assignTimetableColorSlots(emptyMap(), listOf("01002", "01001"))
        assertEquals(first, reordered)
        assertEquals(2, first.size)
    }

    test("shuffle distributions stay balanced across plan sizes and one thousand semester seeds") {
        val seedCount = 1000
        listOf(1, 6, 11, 22, 33).forEach { courseCount ->
            val serials = (0 until courseCount).map { it.toString().padStart(5, '0') }
            val assignments = assignTimetableColorSlots(emptyMap(), serials)
            val aggregate = IntArray(DEFAULT_TIMETABLE_PALETTE_SIZE)
            repeat(seedCount) { seed ->
                val indices = shuffledTimetablePaletteIndices("synthetic-semester-$seed", DEFAULT_TIMETABLE_PALETTE_SIZE)
                assertEquals(DEFAULT_TIMETABLE_PALETTE_SIZE, indices.distinct().size)
                val distribution = timetableColorDistribution(assignments, serials, indices)
                assertTrue(distribution.max() - distribution.min() <= 1)
                distribution.forEachIndexed { index, count -> aggregate[index] += count }
            }
            val expected = seedCount * courseCount.toDouble() / DEFAULT_TIMETABLE_PALETTE_SIZE
            assertTrue(aggregate.all { kotlin.math.abs(it - expected) <= expected * 0.35 })
            println("timetable-distribution,$courseCount,$seedCount,${aggregate.joinToString(",")}")
        }
    }

    test("new courses fill underused colors after removals without recoloring retained courses") {
        val serials = (0 until 22).map { it.toString().padStart(5, '0') }
        val initial = assignTimetableColorSlots(emptyMap(), serials)
        val retained = serials.filter { initial.getValue(it) % 2 == 0 }
        val replacements = (100 until 100 + 22 - retained.size).map { it.toString().padStart(5, '0') }
        val active = retained + replacements
        val edited = assignTimetableColorSlots(initial, active)
        retained.forEach { assertEquals(initial[it], edited[it]) }
        val counts = timetableColorDistribution(
            edited,
            active + active,
            shuffledTimetablePaletteIndices("115-1", DEFAULT_TIMETABLE_PALETTE_SIZE),
        )
        assertEquals(22, counts.sum())
        assertEquals(List(DEFAULT_TIMETABLE_PALETTE_SIZE) { 2 }, counts)
    }
}
