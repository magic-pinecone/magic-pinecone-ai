package org.mpc.presentation.model

import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseType
import kotlin.random.Random

/**
 * Display categories inferred from NCU course-code prefixes, not graduation eligibility.
 * CC/GS conventions: https://course.ncu.edu.tw/static/file/14/1014/img/371/crs_info.pdf
 * Other prefixes mirror the course-search common-category choices.
 */
enum class TimetableCourseCategory(val label: String) {
    REQUIRED("必修"),
    ELECTIVE("選修"),
    CORE_GENERAL_EDUCATION("核心通識 CC"),
    GENERAL_EDUCATION("通識選修 GS"),
    NATIONAL_DEFENSE("國防通識 MN"),
    CHINESE("共同國文 CL0"),
    ENGLISH("共同英文 LN1"),
    HISTORY("共同歷史 HI0"),
    PHYSICAL_EDUCATION("體育 PE"),
    SERVICE_LEARNING("服務學習 SC"),
    RESERVED_TIME("預留時段 RV"),
    UNKNOWN("其他"),
}

/** Specific common categories take precedence over the general required/elective flag. */
internal fun CourseSummary.timetableCategory(): TimetableCourseCategory {
    val code = classNo.trim().uppercase().substringBefore('-')
    val matched = CommonCoursePrefixes.firstOrNull { (prefix, _) ->
        code.startsWith(prefix) && code.getOrNull(prefix.length)?.isDigit() == true
    }
    return matched?.second ?: when (courseType) {
        CourseType.REQUIRED -> TimetableCourseCategory.REQUIRED
        CourseType.ELECTIVE -> TimetableCourseCategory.ELECTIVE
        CourseType.UNKNOWN -> TimetableCourseCategory.UNKNOWN
    }
}

// CC01–CC04 are search categories; summaries do not carry their subject-area
// metadata. Keep CC together instead of guessing the subcategory from a title.
private val CommonCoursePrefixes = listOf(
    "CC" to TimetableCourseCategory.CORE_GENERAL_EDUCATION,
    "GS" to TimetableCourseCategory.GENERAL_EDUCATION,
    "MN" to TimetableCourseCategory.NATIONAL_DEFENSE,
    "CL0" to TimetableCourseCategory.CHINESE,
    "LN1" to TimetableCourseCategory.ENGLISH,
    "HI0" to TimetableCourseCategory.HISTORY,
    "PE" to TimetableCourseCategory.PHYSICAL_EDUCATION,
    "SC" to TimetableCourseCategory.SERVICE_LEARNING,
    "RV" to TimetableCourseCategory.RESERVED_TIME,
)

/** Retain prior assignments and allocate new courses to the least-used live color slot. */
internal fun assignTimetableColorSlots(
    previous: Map<String, Int>,
    serialNumbers: Collection<String>,
    paletteSize: Int = DEFAULT_TIMETABLE_PALETTE_SIZE,
): Map<String, Int> {
    require(paletteSize > 0)
    val assignments = previous.mapValues { (_, slot) -> slot % paletteSize }.toMutableMap()
    val activeCourses = serialNumbers.distinct().sorted()
    val liveCounts = IntArray(paletteSize)
    val historicalCounts = IntArray(paletteSize)
    assignments.values.forEach { historicalCounts[it]++ }
    activeCourses.forEach { serial -> assignments[serial]?.let { liveCounts[it]++ } }
    activeCourses.filter { it !in assignments }.forEach { serial ->
        val slot = (0 until paletteSize).minWith(
            compareBy<Int> { liveCounts[it] }.thenBy { historicalCounts[it] }.thenBy { it },
        )
        assignments[serial] = slot
        liveCounts[slot]++
        historicalCounts[slot]++
    }
    return assignments
}

/** Shuffle hues once per semester, independently of plan ordering and recompositions. */
internal fun shuffledTimetablePaletteIndices(semester: String, paletteSize: Int): List<Int> {
    require(paletteSize > 0)
    return (0 until paletteSize).shuffled(Random(semester.hashCode()))
}

/** Count active courses per resolved palette color, excluding removed courses and repeat meetings. */
internal fun timetableColorDistribution(
    assignments: Map<String, Int>,
    activeSerialNumbers: Collection<String>,
    shuffledIndices: List<Int>,
): List<Int> {
    val counts = IntArray(shuffledIndices.size)
    activeSerialNumbers.distinct().forEach { serial ->
        assignments[serial]?.let { slot -> counts[shuffledIndices[slot % shuffledIndices.size]]++ }
    }
    return counts.toList()
}

internal const val DEFAULT_TIMETABLE_PALETTE_SIZE = 11
