package org.mpc.domain.session

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.data.html.JsoupHtmlParser
import org.mpc.domain.model.EeclassCourse
import org.mpc.domain.model.EeclassCourseNumber
import kotlin.test.assertEquals

private fun courses(body: String) = eeclassCourses(JsoupHtmlParser().parse("<html><body>$body</body></html>", "https://ncueeclass.ncu.edu.tw/dashboard"))

private fun card(href: String, name: String) = """
    <li class="col-md-6"><div class="fs-imageEx"><div class="inline clearfix">
      <div class="pull-left"><a class="thumbnail" href="$href"><img src="/cover.jpg"></a></div>
      <div><div class="fs-caption">
        <div class="fs-label"><a href="$href">$name</a></div>
        <div class="fs-hint"><div>教師</div><div></div><div></div><div>學期</div></div>
      </div></div>
    </div></div></li>
""".trimIndent()

private fun course(number: String, name: String) = EeclassCourse(EeclassCourseNumber(number), name)

val eeclassDashboardTests by testSuite {
    test("each course card yields one course in page order, named by its label link") {
        val html = "<ul class='clearfix'>" + card("/course/42511", " 微積分 ") + card("https://ncueeclass.ncu.edu.tw/course/40001/", "線性代數") + "</ul>"

        assertEquals(listOf(course("42511", "微積分"), course("40001", "線性代數")), courses(html))
    }

    test("a course listed twice appears once") {
        assertEquals(listOf(course("42511", "微積分")), courses(card("/course/42511", "微積分") + card("/course/42511", "微積分")))
    }

    test("links outside course labels, to module pages, or to other hosts are not courses") {
        val html = """
            <a href="/course/42511">微積分</a>
            ${card("/course/material/42511", "教材")}
            ${card("https://evil.example/course/42511", "偽造")}
            ${card("http://ncueeclass.ncu.edu.tw/course/42511", "明文")}
            ${card("/course/42511", "   ")}
        """.trimIndent()

        assertEquals(emptyList(), courses(html))
    }
}
