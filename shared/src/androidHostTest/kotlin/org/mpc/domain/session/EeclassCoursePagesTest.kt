package org.mpc.domain.session

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.data.html.JsoupHtmlParser
import org.mpc.domain.html.HtmlElement
import org.mpc.domain.model.EeclassAttachment
import org.mpc.domain.model.EeclassBulletin
import org.mpc.domain.model.EeclassBulletinNumber
import org.mpc.domain.model.EeclassHomework
import org.mpc.domain.model.EeclassMaterial
import kotlin.test.assertEquals

private fun page(table: String): HtmlElement = JsoupHtmlParser().parse("<html><body>$table</body></html>", "https://ncueeclass.ncu.edu.tw/course/material/40001")

private fun table(id: String, headers: List<String>, vararg rows: List<String>) = buildString {
    append("<table id='$id' class='table custom table-hover'><thead><tr>")
    headers.forEach { append("<th>$it</th>") }
    append("</tr></thead><tbody>")
    rows.forEach { row -> append(row.joinToString("", "<tr>", "</tr>") { "<td>$it</td>" }) }
    append("</tbody></table>")
}

private fun titleCell(link: String) = "<div class='sm-text-overflow'><div class='text-overflow'>$link</div></div>"

private const val EMPTY_ROW = "<td colspan='6'>目前尚無資料</td>"

val eeclassCoursePagesTests by testSuite {
    test("announcements read title, author, and date by column header") {
        val html = table(
            "bulletinMgrTable",
            listOf("項次", "標題", "人氣", "發表人", "公告日期"),
            listOf("1", titleCell("<a class='fs-bulletin-item' href='/dashboard' data-url='/bulletin/content/?id=9'><span class='text'> 期中考 </span></a>"), "12", "王老師", "2026-10-20"),
        )

        assertEquals(listOf(EeclassBulletin(EeclassBulletinNumber("9"), "期中考", "王老師", "2026-10-20")), eeclassBulletins(page(html)))
    }

    test("an announcement's dialog link is found by its number, and a missing one is null") {
        val headers = listOf("項次", "標題", "人氣", "發表人", "公告日期")
        fun row(id: String) = listOf("1", titleCell("<a class='fs-bulletin-item' href='/dashboard' data-url='/bulletin/content/?id=$id&ajaxAuth=t$id'>公告</a>"), "", "", "")
        val list = page(table("bulletinMgrTable", headers, row("8"), row("9")))

        assertEquals("https://ncueeclass.ncu.edu.tw/bulletin/content/?id=9&ajaxAuth=t9", eeclassBulletinDialogLink(list, EeclassBulletinNumber("9")))
        assertEquals(null, eeclassBulletinDialogLink(list, EeclassBulletinNumber("7")))
    }

    test("an announcement dialog reads its summary, body HTML, and named attachment links once each") {
        val dialog = page(
            """
            <div class="modalBox"><div class="app-bulletin-content"><div>
              <div class="modal-iframe-ext2">人氣: 17, 公告日期 10-28, <a href="/course/40001"><span class="text">雲端平台</span></a>, by 林老師</div>
              <div class="fs-text-break-word bulletin-content"><div>請盡快<strong>報名</strong>，<a href="/media/doc/123">簡章</a></div><ul><li>考試一</li></ul></div>
              <p></p>
              <div class="fs-list fs-filelist">
                <a href="/filedownload/5" target="_blank"><span class="fs-iconfont"></span></a>
                <a href="/filedownload/5" target="_blank">報名流程.pdf</a>
                <a href="https://evil.example/filedownload/6">偽造.pdf</a>
              </div>
            </div></div></div>
            """.trimIndent(),
        )

        val detail = eeclassBulletinDetail(dialog)

        assertEquals("人氣: 17, 公告日期 10-28, 雲端平台, by 林老師", detail?.info)
        assertEquals(true, detail?.bodyHtml?.contains("<strong>報名</strong>"))
        assertEquals(true, detail?.bodyHtml?.contains("""href="https://ncueeclass.ncu.edu.tw/media/doc/123""""))
        assertEquals(listOf(EeclassAttachment("報名流程.pdf", SitePath.of("/filedownload/5"))), detail?.attachments)
        assertEquals(null, eeclassBulletinDetail(page("<p>錯誤</p>")))
    }

    test("materials keep their document path and survive a reordered column") {
        val html = table(
            "materialListTable",
            listOf("項次", "更新時間", "標題", "發表人", "人氣", "討論"),
            listOf("1", "3 天前", titleCell("<a href='/media/doc/123'><span class='text'>第一章</span></a>"), "王老師", "5", "0"),
        )

        assertEquals(listOf(EeclassMaterial("第一章", "王老師", "3 天前", SitePath.of("/media/doc/123"))), eeclassMaterials(page(html)))
    }

    test("homework reads opening, deadline, and score") {
        val html = table(
            "homeworkListTable",
            listOf("項次", "名稱", "分組作業", "開放繳交", "期限", "繳交", "分數", "評語"),
            listOf("1", titleCell("<a href='https://ncueeclass.ncu.edu.tw/course/homework/77'><span class='text'>HW 1</span></a>"), "", "10-14 08:00", "10-21 23:59", "", "尚未評分", ""),
        )

        assertEquals(
            listOf(EeclassHomework("HW 1", "10-14 08:00", "10-21 23:59", "尚未評分", SitePath.of("/course/homework/77"))),
            eeclassHomework(page(html)),
        )
    }

    test("an empty list's placeholder row, foreign links, and unexpected paths yield nothing") {
        val headers = listOf("項次", "標題", "發表人", "人氣", "討論", "更新時間")
        val html = "<table id='materialListTable'><thead><tr>${headers.joinToString("") { "<th>$it</th>" }}</tr></thead><tbody><tr>$EMPTY_ROW</tr></tbody></table>" +
            table(
                "materialListTable",
                headers,
                listOf("1", titleCell("<a href='https://evil.example/media/doc/1'>偽造</a>"), "", "", "", ""),
                listOf("2", titleCell("<a href='/course/homework/1'>不是教材</a>"), "", "", "", ""),
            )

        assertEquals(emptyList(), eeclassMaterials(page(html)))
    }

    test("a page without the expected table yields nothing") {
        assertEquals(emptyList(), eeclassHomework(page("<p>維護中</p>")))
    }
}
