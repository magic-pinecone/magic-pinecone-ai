package org.mpc.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import org.mpc.domain.html.HtmlElement
import org.mpc.domain.html.HtmlParser
import org.mpc.domain.model.EeclassBulletinDetail
import org.mpc.domain.model.EeclassBulletinNumber
import org.mpc.domain.model.EeclassCourse
import org.mpc.domain.model.EeclassCourseContent
import org.mpc.domain.model.EeclassCourseNumber
import org.mpc.domain.repository.EeclassCourseRepository
import org.mpc.domain.session.CampusSessions
import org.mpc.domain.session.CampusSites
import org.mpc.domain.session.EECLASS_DASHBOARD
import org.mpc.domain.session.SitePath
import org.mpc.domain.session.SiteResult
import org.mpc.domain.session.Unavailability
import org.mpc.domain.session.eeclassBulletinDetail
import org.mpc.domain.session.eeclassBulletinDialogLink
import org.mpc.domain.session.eeclassBulletins
import org.mpc.domain.session.eeclassBulletinsPath
import org.mpc.domain.session.eeclassCourses
import org.mpc.domain.session.eeclassHomework
import org.mpc.domain.session.eeclassHomeworkPath
import org.mpc.domain.session.eeclassMaterials
import org.mpc.domain.session.eeclassMaterialsPath

@ContributesBinding(AppScope::class)
@Inject
internal class DefaultEeclassCourseRepository(
    private val campusSessions: CampusSessions,
    private val parser: HtmlParser,
) : EeclassCourseRepository {
    override suspend fun fetchEnrolledCourses(): SiteResult<List<EeclassCourse>> = fetchPage(EECLASS_DASHBOARD, ::eeclassCourses)

    override suspend fun fetchCourseContent(course: EeclassCourseNumber): SiteResult<EeclassCourseContent> = fetchPage(eeclassBulletinsPath(course), ::eeclassBulletins).then { bulletins ->
        fetchPage(eeclassMaterialsPath(course), ::eeclassMaterials).then { materials ->
            fetchPage(eeclassHomeworkPath(course), ::eeclassHomework).then { homework ->
                SiteResult.Page(EeclassCourseContent(bulletins, materials, homework))
            }
        }
    }

    override suspend fun fetchBulletin(course: EeclassCourseNumber, bulletin: EeclassBulletinNumber): SiteResult<EeclassBulletinDetail> {
        val site = CampusSites.Eeclass
        val list = eeclassBulletinsPath(course)
        return campusSessions.fetchFollowing(
            site = site,
            path = list,
            pick = { html -> eeclassBulletinDialogLink(parser.parse(html, site.url(list)), bulletin) },
            parse = { html -> eeclassBulletinDetail(parser.parse(html, site.baseUrl + BULLETIN_DIALOG)) },
        ).then { detail -> detail?.let { SiteResult.Page(it) } ?: SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE) }
    }

    private suspend fun <T> fetchPage(path: SitePath, read: (HtmlElement) -> T): SiteResult<T> {
        val site = CampusSites.Eeclass
        return campusSessions.fetch(site, path) { html -> read(parser.parse(html, site.url(path))) }
    }
}

/** Where ee-class serves announcement dialogs; relative links in a dialog resolve against it. */
private const val BULLETIN_DIALOG = "/bulletin/content/"

/** Continues with [next] on a page; a session or network failure ends the chain as it is. */
private inline fun <T, R> SiteResult<T>.then(next: (T) -> SiteResult<R>): SiteResult<R> = when (this) {
    is SiteResult.Page -> next(value)
    is SiteResult.NeedsPortal -> this
    is SiteResult.Unavailable -> this
}
