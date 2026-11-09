package org.mpc.domain.schedule

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import org.mpc.domain.model.AcademicTerm
import org.mpc.domain.model.CoursePlan

/** One dated class; [meeting] gives its course and minutes of the day. */
data class ClassOccurrence(
    val meeting: ClassMeeting,
    val date: LocalDate,
)

/** Every class of this plan on the term's class days, in date and start order. */
fun CoursePlan.occurrencesIn(term: AcademicTerm): List<ClassOccurrence> {
    val meetings = selectedCourses.values.flatMap { it.meetings() }
    return generateSequence(term.firstClassDay) { it.plus(DatePeriod(days = 1)) }
        .takeWhile { it <= term.lastClassDay }
        .flatMap { date ->
            meetings
                .filter { it.day.order == date.dayOfWeek.isoDayNumber }
                .sortedWith(compareBy<ClassMeeting> { it.startMinute }.thenBy { it.course.serialNumber })
                .map { ClassOccurrence(it, date) }
        }
        .toList()
}
