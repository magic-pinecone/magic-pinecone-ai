package org.mpc.domain.repository

import org.mpc.domain.model.EeclassBulletinDetail
import org.mpc.domain.model.EeclassBulletinNumber
import org.mpc.domain.model.EeclassCourse
import org.mpc.domain.model.EeclassCourseContent
import org.mpc.domain.model.EeclassCourseNumber
import org.mpc.domain.session.SiteResult

interface EeclassCourseRepository {
    /** The student's enrolled courses from the ee-class dashboard, recovering the session when it can. */
    suspend fun fetchEnrolledCourses(): SiteResult<List<EeclassCourse>>

    /** The first page of [course]'s announcements, materials, and homework; fails if any one list fails. */
    suspend fun fetchCourseContent(course: EeclassCourseNumber): SiteResult<EeclassCourseContent>

    /** One announcement's dialog, read through [course]'s announcement list, which holds the dialog's token. */
    suspend fun fetchBulletin(course: EeclassCourseNumber, bulletin: EeclassBulletinNumber): SiteResult<EeclassBulletinDetail>
}
