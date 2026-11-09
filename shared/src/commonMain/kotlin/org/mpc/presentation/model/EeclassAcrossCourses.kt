package org.mpc.presentation.model

import kotlinx.datetime.LocalDateTime
import org.mpc.domain.eeclass.dueAfter
import org.mpc.domain.eeclass.newestFirst
import org.mpc.domain.model.AcrossCourses
import org.mpc.domain.model.EeclassBulletin
import org.mpc.domain.model.EeclassHomework
import org.mpc.domain.session.SiteResult
import org.mpc.presentation.formatting.dueLabel
import org.mpc.presentation.formatting.postedLabel

/**
 * Rows read across the enrolled courses, as a spoken answer uses them. [rows] is empty unless [status] is
 * [EeclassLoadStatus.LOADED]; [unreadCourses] names the courses whose list did not load.
 */
class EeclassAcrossCourses internal constructor(
    val status: EeclassLoadStatus,
    val rows: List<EeclassCourseRowItem> = emptyList(),
    val unreadCourses: List<String> = emptyList(),
)

/** A row from one course's list: [whenLabel] is the deadline or posting day relative to today. */
class EeclassCourseRowItem(
    val course: String,
    val title: String,
    val whenLabel: String,
)

/** Homework still open at [now], soonest deadline first. */
fun SiteResult<AcrossCourses<EeclassHomework>>.toDueHomework(now: LocalDateTime): EeclassAcrossCourses = toAnswer { homework ->
    homework.dueAfter(now).map { EeclassCourseRowItem(it.course.name, it.homework.title, it.dueLabel(now.date)) }
}

/** Announcements newest first. */
fun SiteResult<AcrossCourses<EeclassBulletin>>.toNewestBulletins(now: LocalDateTime): EeclassAcrossCourses = toAnswer { bulletins ->
    bulletins.newestFirst(now.date).map { EeclassCourseRowItem(it.course.name, it.bulletin.title, it.postedLabel(now.date)) }
}

private inline fun <T> SiteResult<AcrossCourses<T>>.toAnswer(rows: (AcrossCourses<T>) -> List<EeclassCourseRowItem>): EeclassAcrossCourses = when (this) {
    is SiteResult.Page -> EeclassAcrossCourses(EeclassLoadStatus.LOADED, rows(value), value.unread.map { it.name })
    is SiteResult.NeedsPortal -> EeclassAcrossCourses(EeclassLoadStatus.NEEDS_PORTAL)
    is SiteResult.Unavailable -> EeclassAcrossCourses(EeclassLoadStatus.UNAVAILABLE)
}
