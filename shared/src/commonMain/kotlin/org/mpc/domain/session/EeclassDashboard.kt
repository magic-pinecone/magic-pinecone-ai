package org.mpc.domain.session

import org.mpc.domain.html.HtmlElement
import org.mpc.domain.model.EeclassCourse
import org.mpc.domain.model.EeclassCourseNumber

val EECLASS_DASHBOARD: SitePath = SitePath.of("/dashboard")

private val COURSE_PATH = Regex("""/course/(\d+)/?""")

/**
 * The enrolled courses on the ee-class dashboard, in page order. Each course card names its course in a
 * `.fs-caption .fs-label` link; the card's image link repeats the URL without a name and is skipped.
 */
fun eeclassCourses(dashboard: HtmlElement): List<EeclassCourse> = dashboard.select(".fs-caption .fs-label > a[href]")
    .mapNotNull { anchor ->
        val number = eeclassPath(anchor)?.let(COURSE_PATH::matchEntire)?.groupValues?.get(1) ?: return@mapNotNull null
        val name = anchor.text().trim().ifEmpty { return@mapNotNull null }
        EeclassCourse(EeclassCourseNumber(number), name)
    }.distinctBy { it.number }
