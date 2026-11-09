package org.mpc.domain.search

import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CourseQuery
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.SeatAvailability
import org.mpc.domain.model.SlotMatch

private enum class MatchTier(
    val score: Int,
    val matches: (course: NormalizedCourse, token: String) -> Boolean,
) {
    SERIAL_NUMBER(80, { course, token -> course.serialNo == token }),
    CLASS_NO_EXACT(70, { course, token -> course.classNo == token }),
    TITLE_EQUALS(60, { course, token -> course.title == token }),
    CLASS_NO_PREFIX(50, { course, token -> course.classNo.startsWith(token) }),
    TITLE_STARTS_WITH(40, { course, token -> course.title.startsWith(token) }),
    TITLE_CONTAINS(30, { course, token -> course.title.contains(token) }),
    TEACHER_CONTAINS(20, { course, token -> course.teachers.any { it.contains(token) } }),
    DEPARTMENT_CONTAINS(10, { course, token -> course.department.any { it.contains(token) } }),
}

private class NormalizedCourse(course: CourseSummary) {
    val serialNo = normalizeForSearch(course.serialNo.value)
    val classNo = normalizeForSearch(course.classNo)
    val title = normalizeForSearch(course.title)
    val teachers = course.teachers.map(::normalizeForSearch)
    val department = listOf(course.departmentName, course.collegeName).map(::normalizeForSearch)
}

fun normalizeForSearch(text: String): String = foldTraditionalToSimplified(normalizeCompatibility(text).lowercase())
    .trim()
    .replace(WHITESPACE, " ")

private val WHITESPACE = Regex("\\s+")

fun searchCourses(
    courses: List<CourseSummary>,
    query: CourseQuery,
): List<CourseSummary> {
    val candidates = courses.filter { it.satisfiesFilters(query.filters) }
    val tokens = normalizeForSearch(query.keyword).split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return candidates

    return candidates
        .mapNotNull { course ->
            val normalized = NormalizedCourse(course)
            val scores = tokens.map { token -> scoreToken(normalized, token) ?: return@mapNotNull null }
            course to scores.sum()
        }
        .sortedByDescending { (_, score) -> score }
        .map { (course, _) -> course }
}

private fun scoreToken(
    course: NormalizedCourse,
    token: String,
): Int? = MatchTier.entries.firstOrNull { it.matches(course, token) }?.score

private fun CourseSummary.satisfiesFilters(filters: Set<CourseFilter>): Boolean = filters
    .groupBy { it::class }
    .values
    .all { sameKind -> sameKind.any { matches(it) } }

private fun CourseSummary.matches(filter: CourseFilter): Boolean = when (filter) {
    is CourseFilter.Teacher -> teachers.any { sameName(it, filter.name) }
    is CourseFilter.Department -> sameName(departmentName, filter.name)
    is CourseFilter.College -> sameName(collegeName, filter.name)
    is CourseFilter.Type -> courseType == filter.type
    is CourseFilter.Credits -> credit == filter.credits
    is CourseFilter.Seats -> hasOpenSeats() == (filter.availability == SeatAvailability.OPEN)
    is CourseFilter.Schedule -> matchesSchedule(filter)
}

private fun sameName(
    catalogName: String,
    filterName: String,
): Boolean = normalizeForSearch(catalogName) == normalizeForSearch(filterName)

// A limitCnt of 0 means the catalog publishes no enrollment cap, so such a course is never full.
private fun CourseSummary.hasOpenSeats(): Boolean = limitCnt == 0 || admitCnt < limitCnt

private fun CourseSummary.matchesSchedule(filter: CourseFilter.Schedule): Boolean = when (filter.match) {
    SlotMatch.FITS_WITHIN -> classTimes.isNotEmpty() &&
        classTimes.none { it.day == CourseDay.UNKNOWN } &&
        filter.slots.containsAll(classTimes)

    SlotMatch.OVERLAPS -> classTimes.any { it in filter.slots }
}
