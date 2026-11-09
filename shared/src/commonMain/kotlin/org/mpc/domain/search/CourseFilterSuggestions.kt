package org.mpc.domain.search

import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.SeatAvailability

private class Candidate(
    val filter: CourseFilter,
    val names: List<String>,
)

private class FilterSource(
    prefixes: List<String>,
    val candidates: (courses: List<CourseSummary>, query: String) -> List<Candidate>,
) {
    val prefixes = prefixes.map(::normalizeForSearch)
}

private fun namesContaining(
    names: Sequence<String>,
    query: String,
    toFilter: (String) -> CourseFilter,
): List<Candidate> = names
    .distinct()
    .filter { normalizeForSearch(it).contains(query) }
    .map { Candidate(toFilter(it), listOf(it)) }
    .toList()

private fun fixed(
    filter: CourseFilter,
    vararg aliases: String,
) = Candidate(filter, aliases.toList())

private fun List<Candidate>.matching(query: String) = filter { candidate ->
    candidate.names.any { normalizeForSearch(it).contains(query) }
}

private fun creditText(credits: Double) = credits.toString().removeSuffix(".0")

private val SOURCES = listOf(
    FilterSource(listOf("老師", "教師")) { courses, query ->
        namesContaining(courses.asSequence().flatMap { it.teachers }, query, CourseFilter::Teacher)
    },
    FilterSource(listOf("系所", "系")) { courses, query ->
        namesContaining(courses.asSequence().map { it.departmentName }, query, CourseFilter::Department)
    },
    FilterSource(listOf("學院")) { courses, query ->
        namesContaining(courses.asSequence().map { it.collegeName }, query, CourseFilter::College)
    },
    FilterSource(listOf("類型")) { _, query ->
        listOf(
            fixed(CourseFilter.Type(CourseType.REQUIRED), CourseType.REQUIRED.description),
            fixed(CourseFilter.Type(CourseType.ELECTIVE), CourseType.ELECTIVE.description),
        ).matching(query)
    },
    FilterSource(listOf("學分")) { courses, query ->
        val wanted = query.removeSuffix(normalizeForSearch("學分")).trim().toDoubleOrNull()
        courses
            .map { it.credit }
            .distinct()
            .filter { query.isEmpty() || it == wanted }
            .map { fixed(CourseFilter.Credits(it), "${creditText(it)}學分", creditText(it)) }
    },
    FilterSource(listOf("名額")) { _, query ->
        listOf(
            fixed(CourseFilter.Seats(SeatAvailability.OPEN), "有名額", "尚有名額"),
            fixed(CourseFilter.Seats(SeatAvailability.FULL), "額滿", "已額滿"),
        ).matching(query)
    },
)

private class ParsedText(
    val sources: List<FilterSource>,
    val query: String,
)

private fun parseText(text: String): ParsedText {
    val normalized = normalizeForSearch(text)
    val separator = normalized.indexOf(':')
    if (separator > 0) {
        val prefix = normalized.substring(0, separator).trim()
        val source = SOURCES.firstOrNull { prefix in it.prefixes }
        if (source != null) return ParsedText(listOf(source), normalized.substring(separator + 1).trim())
    }
    return ParsedText(SOURCES, normalized)
}

fun suggestCourseFilters(
    courses: List<CourseSummary>,
    text: String,
    active: Set<CourseFilter>,
    limit: Int = 8,
): List<CourseFilter> {
    if (text.isBlank()) return emptyList()
    val parsed = parseText(text)

    return parsed.sources
        .flatMapIndexed { kindOrder, source ->
            source.candidates(courses, parsed.query)
                .filter { it.filter !in active }
                .map { candidate -> RankedSuggestion(candidate, parsed.query, kindOrder) }
        }
        .sortedWith(compareBy({ it.startsWithRank }, { it.nameLength }, { it.kindOrder }))
        .map { it.candidate.filter }
        .take(limit)
}

private class RankedSuggestion(
    val candidate: Candidate,
    query: String,
    val kindOrder: Int,
) {
    private val matchingNames = candidate.names.map(::normalizeForSearch).filter { it.contains(query) }
    val startsWithRank = if (matchingNames.any { it.startsWith(query) }) 0 else 1
    val nameLength = matchingNames.minOfOrNull { it.length } ?: Int.MAX_VALUE
}
