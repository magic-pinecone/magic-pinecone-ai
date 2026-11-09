package org.mpc.presentation.model

import org.mpc.domain.model.EeclassBulletinDetail
import org.mpc.domain.model.EeclassCourse
import org.mpc.domain.model.EeclassCourseContent
import org.mpc.domain.session.SiteResult

enum class EeclassLoadStatus {
    LOADED,

    /** Getting the ee-class session back needs the user to sign in to Portal. */
    NEEDS_PORTAL,
    UNAVAILABLE,
}

/** The enrolled-course list as native screens show it. [courses] is empty unless [status] is [EeclassLoadStatus.LOADED]. */
class EeclassCourses internal constructor(
    val status: EeclassLoadStatus,
    val courses: List<EeclassCourseRow>,
)

class EeclassCourseRow(
    val number: String,
    val name: String,
)

/** One course's lists as native screens show them. The lists are empty unless [status] is [EeclassLoadStatus.LOADED]. */
class EeclassCoursePage internal constructor(
    val status: EeclassLoadStatus,
    val bulletins: List<EeclassBulletinRow> = emptyList(),
    val materials: List<EeclassItemRow> = emptyList(),
    val homework: List<EeclassItemRow> = emptyList(),
)

/** A list row: [detail] joins the row's secondary fields, and [path] is the ee-class page a tap opens. */
class EeclassItemRow(
    val title: String,
    val detail: String,
    val path: String,
)

/** An announcement row. A tap loads announcement [number] with [org.mpc.IosSharedHost]'s bulletin loader. */
class EeclassBulletinRow(
    val number: String,
    val title: String,
    val detail: String,
)

/** One announcement as the native sheet shows it. Its fields are empty unless [status] is [EeclassLoadStatus.LOADED]. */
class EeclassBulletinPage internal constructor(
    val status: EeclassLoadStatus,
    val info: String = "",
    val bodyHtml: String = "",
    val attachments: List<EeclassItemRow> = emptyList(),
)

fun SiteResult<EeclassBulletinDetail>.toEeclassBulletinPage(): EeclassBulletinPage = when (this) {
    is SiteResult.Page -> EeclassBulletinPage(
        status = EeclassLoadStatus.LOADED,
        info = value.info,
        bodyHtml = value.bodyHtml,
        attachments = value.attachments.map { EeclassItemRow(it.name, "", it.path.value) },
    )

    is SiteResult.NeedsPortal -> EeclassBulletinPage(EeclassLoadStatus.NEEDS_PORTAL)

    is SiteResult.Unavailable -> EeclassBulletinPage(EeclassLoadStatus.UNAVAILABLE)
}

fun SiteResult<List<EeclassCourse>>.toEeclassCourses(): EeclassCourses = when (this) {
    is SiteResult.Page -> EeclassCourses(EeclassLoadStatus.LOADED, value.map { EeclassCourseRow(it.number.value, it.name) })
    is SiteResult.NeedsPortal -> EeclassCourses(EeclassLoadStatus.NEEDS_PORTAL, emptyList())
    is SiteResult.Unavailable -> EeclassCourses(EeclassLoadStatus.UNAVAILABLE, emptyList())
}

fun SiteResult<EeclassCourseContent>.toEeclassCoursePage(): EeclassCoursePage = when (this) {
    is SiteResult.Page -> EeclassCoursePage(
        status = EeclassLoadStatus.LOADED,
        bulletins = value.bulletins.map { EeclassBulletinRow(it.number.value, it.title, detail(it.author, it.posted)) },
        materials = value.materials.map { EeclassItemRow(it.title, detail(it.author, it.updated), it.path.value) },
        homework = value.homework.map { homework ->
            EeclassItemRow(
                title = homework.title,
                detail = detail(homework.due.takeIf(String::isNotEmpty)?.let { "期限 $it" }, homework.score),
                path = homework.path.value,
            )
        },
    )

    is SiteResult.NeedsPortal -> EeclassCoursePage(EeclassLoadStatus.NEEDS_PORTAL)

    is SiteResult.Unavailable -> EeclassCoursePage(EeclassLoadStatus.UNAVAILABLE)
}

private fun detail(vararg parts: String?): String = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")
