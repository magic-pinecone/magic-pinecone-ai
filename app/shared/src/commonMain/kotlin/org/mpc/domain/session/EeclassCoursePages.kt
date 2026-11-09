package org.mpc.domain.session

import org.mpc.domain.html.HtmlElement
import org.mpc.domain.model.EeclassCourseNumber
import org.mpc.domain.model.EeclassHomework
import org.mpc.domain.model.EeclassMaterial

fun eeclassMaterialsPath(course: EeclassCourseNumber): SitePath = SitePath.of("/course/material/${course.value}")

fun eeclassHomeworkPath(course: EeclassCourseNumber): SitePath = SitePath.of("/course/homeworkList/${course.value}")

private val MATERIAL_PATH = Regex("""/media/doc/\d+""")
private val HOMEWORK_PATH = Regex("""/course/homework/\d+""")

// Columns are found by header label, so a reordered or added column cannot shift a field into the wrong one.

fun eeclassMaterials(page: HtmlElement): List<EeclassMaterial> = rows(page, "materialListTable").mapNotNull { row ->
    val link = row.cell("標題")?.firstSiteLink(MATERIAL_PATH) ?: return@mapNotNull null
    EeclassMaterial(title = link.name, author = row.text("發表人"), updated = row.text("更新時間"), path = link.path)
}

fun eeclassHomework(page: HtmlElement): List<EeclassHomework> = rows(page, "homeworkListTable").mapNotNull { row ->
    val link = row.cell("名稱")?.firstSiteLink(HOMEWORK_PATH) ?: return@mapNotNull null
    EeclassHomework(
        title = link.name,
        opens = row.text("開放繳交"),
        due = row.text("期限"),
        score = row.text("分數"),
        path = link.path,
    )
}

internal class TableRow(
    private val headers: List<String>,
    private val cells: List<HtmlElement>,
) {
    fun cell(header: String): HtmlElement? = headers.indexOf(header).takeIf { it >= 0 }?.let(cells::getOrNull)

    fun text(header: String): String = cell(header)?.text()?.trim().orEmpty()
}

/** The rows of ee-class list table [tableId]. An empty list renders one placeholder row without a link, which parsers skip. */
internal fun rows(page: HtmlElement, tableId: String): List<TableRow> {
    val headers = page.select("table#$tableId thead th").map { it.text().trim() }
    return page.select("table#$tableId tbody tr").map { TableRow(headers, it.select("td")) }
}

internal class SiteLink(
    val name: String,
    val path: SitePath,
)

/** The first named link in this element to a page on ee-class whose path matches [pattern]. */
internal fun HtmlElement.firstSiteLink(pattern: Regex): SiteLink? = select("a[href]").firstNotNullOfOrNull { it.siteLink(pattern) }

/** This anchor as a named link to a page on ee-class whose path matches [pattern], or null. */
internal fun HtmlElement.siteLink(pattern: Regex): SiteLink? {
    val path = eeclassPath(this)?.takeIf(pattern::matches) ?: return null
    val name = text().trim().ifEmpty { return null }
    return SitePath.parse(path)?.let { SiteLink(name, it) }
}

/** The path of [anchor]'s target when it is an https page on ee-class, or null. */
internal fun eeclassPath(anchor: HtmlElement): String? {
    val url = anchor.absUrl("href")?.let(::parseHttpsUrl) ?: return null
    return url.encodedPath.takeIf { url.host.equals(CampusSites.Eeclass.host, ignoreCase = true) }
}
