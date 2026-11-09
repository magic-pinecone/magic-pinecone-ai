package org.mpc

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CourseQuery
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.schedule.ClassMeeting
import org.mpc.domain.schedule.UpcomingMeeting
import org.mpc.domain.schedule.meetingsOn
import org.mpc.domain.schedule.nextMeeting
import org.mpc.domain.schedule.occurrencesIn
import org.mpc.presentation.formatting.periodLabel
import org.mpc.presentation.state.CourseFilterOptions
import org.mpc.presentation.state.courseFilterOptions

private const val SERIAL_NUMBER_LENGTH = 5

/** Course Catalog lookup for App Intents; [limit] caps the ranked results. */
@Throws(Exception::class)
suspend fun IosSharedHost.findCourses(
    semester: String,
    keyword: String,
    filters: List<CourseFilter>,
    limit: Int,
): List<CourseSummary> = appGraph.courseRepository
    .searchCourses(CourseQuery(semester, keyword, filters.toSet()))
    .courses
    .take(limit.coerceAtLeast(0))

/** The teachers, departments, colleges, and credits the filter sheet offers, so intents ask with the same choices. */
@Throws(Exception::class)
suspend fun IosSharedHost.courseFilterOptions(semester: String): CourseFilterOptions = appGraph.courseRepository
    .searchCourses(CourseQuery(semester))
    .courses
    .courseFilterOptions()

/** The course with this serial number, or null when it is malformed or not in the catalog. */
@Throws(Exception::class)
suspend fun IosSharedHost.course(
    semester: String,
    serialNumber: String,
): CourseSummary? {
    if (serialNumber.length != SERIAL_NUMBER_LENGTH || !serialNumber.all { it in '0'..'9' }) return null
    return appGraph.courseRepository
        .fetchCoursesBySerialNo(semester, listOf(CourseSerialNo(serialNumber)))
        .courses
        .firstOrNull()
}

/** Meetings of the Saved Course Plan on [day], in start order. */
@Throws(Exception::class)
suspend fun IosSharedHost.savedMeetingsOn(
    semester: String,
    day: CourseDay,
): List<ClassMeeting> = appGraph.coursePlanRepository.loadPlan(semester).meetingsOn(day)

@Throws(Exception::class)
suspend fun IosSharedHost.nextSavedMeeting(
    semester: String,
    day: CourseDay,
    minuteOfDay: Int,
): UpcomingMeeting? = appGraph.coursePlanRepository.loadPlan(semester).nextMeeting(day, minuteOfDay)

/** A date as Swift reads it; kotlinx-datetime types do not export to Objective-C usefully. */
data class CalendarDay(
    val year: Int,
    val month: Int,
    val day: Int,
)

/** One class of the Saved Course Plan on a term day. */
data class CalendarClass(
    val course: CourseSummary,
    val date: CalendarDay,
    val startMinute: Int,
    val endMinute: Int,
    val periodLabel: String,
)

/** The term's class days and every Saved Course Plan class on them. */
data class TermCalendar(
    val firstClassDay: CalendarDay,
    val lastClassDay: CalendarDay,
    val classes: List<CalendarClass>,
)

/** The Saved Course Plan laid over this term, or null when the term's class days are not known. */
@Throws(Exception::class)
suspend fun IosSharedHost.savedTermCalendar(semester: String): TermCalendar? {
    val term = appGraph.academicTermRepository.term(semester) ?: return null
    val classes = appGraph.coursePlanRepository.loadPlan(semester).occurrencesIn(term).map {
        CalendarClass(
            course = it.meeting.course,
            date = it.date.toCalendarDay(),
            startMinute = it.meeting.startMinute,
            endMinute = it.meeting.endMinute,
            periodLabel = it.meeting.periodLabel(),
        )
    }
    return TermCalendar(term.firstClassDay.toCalendarDay(), term.lastClassDay.toCalendarDay(), classes)
}

private fun LocalDate.toCalendarDay() = CalendarDay(year, month.number, day)
