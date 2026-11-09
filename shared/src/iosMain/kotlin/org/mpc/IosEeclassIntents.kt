package org.mpc

import kotlinx.datetime.toLocalDateTime
import org.mpc.domain.model.CampusTimeZone
import org.mpc.presentation.model.EeclassAcrossCourses
import org.mpc.presentation.model.toDueHomework
import org.mpc.presentation.model.toNewestBulletins
import kotlin.time.Clock

/** ee-class homework still open now, across the enrolled courses, soonest deadline first. */
@Throws(Exception::class)
suspend fun IosSharedHost.dueEeclassHomework(): EeclassAcrossCourses = appGraph.eeclassCourseRepository
    .fetchHomeworkAcrossCourses()
    .toDueHomework(campusNow())

/** ee-class announcements across the enrolled courses, newest first. */
@Throws(Exception::class)
suspend fun IosSharedHost.newestEeclassBulletins(): EeclassAcrossCourses = appGraph.eeclassCourseRepository
    .fetchBulletinsAcrossCourses()
    .toNewestBulletins(campusNow())

private fun campusNow() = Clock.System.now().toLocalDateTime(CampusTimeZone)
