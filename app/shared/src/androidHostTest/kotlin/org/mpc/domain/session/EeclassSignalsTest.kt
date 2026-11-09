package org.mpc.domain.session

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.data.html.JsoupHtmlParser
import kotlin.test.assertEquals

private fun signals(status: Int, location: String? = null, html: String = "") = EeclassSignals.classify(SiteResponse(status, location, html), JsoupHtmlParser())

private fun signedInPage(vararg extra: String) = "<html><body>" + extra.joinToString("") + "</body></html>"

val eeclassSignalsTests by testSuite {
    test("ee-class signed in requires same-origin dashboard links and no login form") {
        val body = """
            <html><body>
              <a href='/dashboard/myTimeTable'>課表</a>
              <a class='latest' href="https://ncueeclass.ncu.edu.tw/dashboard/latestEvent">活動</a>
            </body></html>
        """.trimIndent()

        assertEquals(SiteVerdict.SIGNED_IN, signals(200, html = body))
    }

    test("ee-class login redirects and login forms mean signed out") {
        assertEquals(SiteVerdict.SIGNED_OUT, signals(302, "/index/login?next=%2Fdashboard"))
        assertEquals(SiteVerdict.SIGNED_OUT, signals(200, html = "<form id='login_form'><input name='password'></form>"))
        assertEquals(SiteVerdict.SIGNED_OUT, signals(401))
        assertEquals(SiteVerdict.UNRECOGNIZED, signals(302, "https://ncueeclass.ncu.edu.tw:8443/index/login"))
        assertEquals(SiteVerdict.UNRECOGNIZED, signals(503, html = "unavailable"))
        assertEquals(SiteVerdict.UNRECOGNIZED, signals(403, html = "forbidden"))
    }

    test("ee-class markers in comments, scripts, and foreign links do not sign in") {
        val body = """
            <!-- <a href='/dashboard/myTimeTable'></a> -->
            <script>const x = "<a href='/dashboard/latestEvent'>";</script>
            <a href='https://evil.example/dashboard/myTimeTable'>課表</a>
            <a href='https://evil.example/dashboard/latestEvent'>活動</a>
            <a href='https://ncueeclass.ncu.edu.tw:8443/dashboard/myTimeTable'>課表</a>
            <a href='https://ncueeclass.ncu.edu.tw:8443/dashboard/latestEvent'>活動</a>
        """.trimIndent()

        assertEquals(SiteVerdict.UNRECOGNIZED, signals(200, html = body))
    }

    test("a login form inside a comment or script string is not a login form") {
        val links = "<a href='/dashboard/myTimeTable'></a><a href='/dashboard/latestEvent'></a>"

        assertEquals(
            SiteVerdict.SIGNED_IN,
            signals(200, html = signedInPage("<!-- <form id='login_form'></form> -->", links)),
        )
        assertEquals(
            SiteVerdict.SIGNED_IN,
            signals(200, html = signedInPage("<script>const f = \"<form id='login_form'>\";</script>", links)),
        )
    }

    test("a dashboard link inside a script string does not count") {
        val html = signedInPage(
            "<a href='/dashboard/myTimeTable'></a>",
            "<script>document.write(\"<a href='/dashboard/latestEvent'>\");</script>",
        )

        assertEquals(SiteVerdict.UNRECOGNIZED, signals(200, html = html))
    }

    test("protocol-relative and absolute off-host links do not count") {
        val html = signedInPage(
            "<a href='//evil.example/dashboard/myTimeTable'></a>",
            "<a href='//evil.example/dashboard/latestEvent'></a>",
            "<a href='http://ncueeclass.ncu.edu.tw/dashboard/myTimeTable'></a>",
            "<a href='https://evil.example/dashboard/latestEvent'></a>",
        )

        assertEquals(SiteVerdict.UNRECOGNIZED, signals(200, html = html))
    }

    test("a protocol-relative link to the ee-class host counts") {
        val html = signedInPage(
            "<a href='//ncueeclass.ncu.edu.tw/dashboard/myTimeTable'></a>",
            "<a href='/dashboard/latestEvent'></a>",
        )

        assertEquals(SiteVerdict.SIGNED_IN, signals(200, html = html))
    }
}
