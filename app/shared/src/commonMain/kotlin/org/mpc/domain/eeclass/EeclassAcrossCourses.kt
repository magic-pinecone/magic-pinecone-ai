package org.mpc.domain.eeclass

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.mpc.domain.model.AcrossCourses
import org.mpc.domain.model.EeclassBulletin
import org.mpc.domain.model.EeclassCourse
import org.mpc.domain.model.EeclassHomework
import org.mpc.domain.session.eeclassDateTime

data class DueHomework(
    val course: EeclassCourse,
    val homework: EeclassHomework,
    val due: LocalDateTime,
)

data class PostedBulletin(
    val course: EeclassCourse,
    val bulletin: EeclassBulletin,
    val posted: LocalDate,
)

/** Homework whose deadline is after [now], soonest first. Homework without a readable deadline is left out. */
fun AcrossCourses<EeclassHomework>.dueAfter(now: LocalDateTime): List<DueHomework> = items
    .mapNotNull { (course, homework) ->
        eeclassDateTime(homework.due, now.date)?.takeIf { it > now }?.let { DueHomework(course, homework, it) }
    }
    .sortedBy { it.due }

/** Announcements newest first. Announcements without a readable date are left out. */
fun AcrossCourses<EeclassBulletin>.newestFirst(today: LocalDate): List<PostedBulletin> = items
    .mapNotNull { (course, bulletin) ->
        eeclassDateTime(bulletin.posted, today)?.let { PostedBulletin(course, bulletin, it.date) }
    }
    .sortedByDescending { it.posted }
