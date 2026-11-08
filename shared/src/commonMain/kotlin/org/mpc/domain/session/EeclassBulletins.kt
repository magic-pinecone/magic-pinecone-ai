package org.mpc.domain.session

import org.mpc.domain.html.HtmlElement
import org.mpc.domain.model.EeclassAttachment
import org.mpc.domain.model.EeclassBulletin
import org.mpc.domain.model.EeclassBulletinDetail
import org.mpc.domain.model.EeclassBulletinNumber
import org.mpc.domain.model.EeclassCourseNumber

fun eeclassBulletinsPath(course: EeclassCourseNumber): SitePath = SitePath.of("/course/bulletin/${course.value}")

private val ATTACHMENT_PATH = Regex("""/filedownload/\d+""")
private val DIGITS = Regex("""\d+""")

fun eeclassBulletins(page: HtmlElement): List<EeclassBulletin> = rows(page, "bulletinMgrTable").mapNotNull { row ->
    val anchor = row.cell("標題")?.select("a.fs-bulletin-item")?.firstOrNull() ?: return@mapNotNull null
    val number = dialogBulletinNumber(anchor) ?: return@mapNotNull null
    val title = anchor.text().trim().ifEmpty { return@mapNotNull null }
    EeclassBulletin(number = number, title = title, author = row.text("發表人"), posted = row.text("公告日期"))
}

/**
 * The dialog link for [bulletin] on a course's announcement list, or null when the list no longer shows it. The link
 * carries a one-time token: hand it straight to [CampusSessions.fetchFollowing] and never keep it.
 */
fun eeclassBulletinDialogLink(page: HtmlElement, bulletin: EeclassBulletinNumber): String? = page.select("#bulletinMgrTable a.fs-bulletin-item")
    .firstOrNull { dialogBulletinNumber(it) == bulletin }
    ?.absUrl("data-url")

/** An announcement dialog's summary line, body, and attachments, or null when the page is not one. */
fun eeclassBulletinDetail(page: HtmlElement): EeclassBulletinDetail? {
    val body = page.select(".bulletin-content").firstOrNull() ?: return null
    return EeclassBulletinDetail(
        info = page.select(".modal-iframe-ext2").firstOrNull()?.text()?.trim().orEmpty(),
        bodyHtml = body.htmlWithAbsoluteLinks(),
        attachments = page.select(".fs-filelist").flatMap { list ->
            list.select("a[href]").mapNotNull { anchor -> anchor.siteLink(ATTACHMENT_PATH) }
        }.distinctBy { it.path }.map { EeclassAttachment(it.name, it.path) },
    )
}

private fun dialogBulletinNumber(anchor: HtmlElement): EeclassBulletinNumber? {
    val url = anchor.absUrl("data-url")?.let(::parseHttpsUrl) ?: return null
    if (!url.host.equals(CampusSites.Eeclass.host, ignoreCase = true)) return null
    return url.parameters["id"]?.takeIf { it.matches(DIGITS) }?.let(::EeclassBulletinNumber)
}
