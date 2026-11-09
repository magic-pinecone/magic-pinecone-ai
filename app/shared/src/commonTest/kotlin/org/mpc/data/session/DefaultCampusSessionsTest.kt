package org.mpc.data.session

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.mpc.domain.html.HtmlElement
import org.mpc.domain.html.HtmlParser
import org.mpc.domain.session.CampusSite
import org.mpc.domain.session.PortalNeed
import org.mpc.domain.session.PortalSignIn
import org.mpc.domain.session.PortalStatus
import org.mpc.domain.session.Renewal
import org.mpc.domain.session.SiteFile
import org.mpc.domain.session.SiteFollowUp
import org.mpc.domain.session.SitePath
import org.mpc.domain.session.SiteRequest
import org.mpc.domain.session.SiteResponse
import org.mpc.domain.session.SiteResult
import org.mpc.domain.session.SiteSignals
import org.mpc.domain.session.SiteStatus
import org.mpc.domain.session.SiteVerdict
import org.mpc.domain.session.Unavailability
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

private val SIGNED_IN_DASHBOARD = SiteResponse(
    status = 200,
    location = null,
    html = "dashboard",
)
private val SIGNED_OUT_REDIRECT = SiteResponse(status = 302, location = "/index/login", html = "")
private val TIMETABLE = SitePath.of("/dashboard/myTimeTable")

/** Reads status and location only, so these tests never depend on a site's HTML. */
private val STATUS_SIGNALS = SiteSignals { response, _ ->
    when (response.status) {
        200 -> SiteVerdict.SIGNED_IN
        302 -> SiteVerdict.SIGNED_OUT
        else -> SiteVerdict.UNRECOGNIZED
    }
}

private val TEST_SITE = CampusSite(
    id = "test",
    displayName = "Test",
    host = "ncueeclass.ncu.edu.tw",
    portalSystemId = "ncueeclass",
    probePath = SitePath.of("/dashboard"),
    signals = STATUS_SIGNALS,
)

private object UnusedHtmlParser : HtmlParser {
    override fun parse(html: String, baseUrl: String): HtmlElement = fail("Session logic must not parse HTML")
}

/** Answers each request from a script and records what the session layer asked for, in order. */
private class ScriptedTransport(
    private val pages: ArrayDeque<TransportResult> = ArrayDeque(),
    private val handoffs: ArrayDeque<HandoffResult> = ArrayDeque(),
    /** Requests from this one on wait for [gate]. */
    private val gatedFromRequest: Int = Int.MAX_VALUE,
    private val gate: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    private val portalChecks: ArrayDeque<PortalCheck> = ArrayDeque(),
) : SessionTransport {
    val events = mutableListOf<String>()
    private var requests = 0

    override suspend fun send(request: SiteRequest<*>): TransportResult {
        events += describe(request)
        if (++requests >= gatedFromRequest) gate.await()
        return pages.removeFirst()
    }

    override suspend fun checkPortal(): PortalCheck {
        events += "check portal"
        return portalChecks.removeFirst()
    }

    override suspend fun handoff(site: CampusSite): HandoffResult {
        events += "handoff ${site.id}"
        return handoffs.removeFirst()
    }

    override suspend fun clearCookies(url: String, keep: Set<String>) {
        events += "clear $url keep=$keep"
    }
}

private fun describe(request: SiteRequest<*>): String = when (request) {
    is SiteRequest.Get -> "get ${request.link}"
    is SiteRequest.Download -> "download ${request.link}"
    is SiteRequest.Form -> "post ${request.link} form ${request.fields}"
    is SiteRequest.Multipart -> "post ${request.link} multipart ${request.fields} files ${request.files.map { (field, file) -> "$field=${file.name}" }}"
}

private fun sessions(
    transport: SessionTransport,
    guardStore: PortalGuardStore = InMemoryPortalGuardStore(),
) = DefaultCampusSessions(
    settings = FakeAppSettingsRepository(),
    portalState = PortalState(guardStore),
    transport = transport,
    parser = UnusedHtmlParser,
    sites = listOf(TEST_SITE),
)

private fun page(response: SiteResponse) = TransportResult.Response(response)

val defaultCampusSessionsTests by testSuite {
    test("a signed-out site hands off from live Portal and returns the parsed page") {
        runTest {
            val transport = ScriptedTransport(
                pages = ArrayDeque(listOf(page(SIGNED_OUT_REDIRECT), page(SIGNED_IN_DASHBOARD))),
                handoffs = ArrayDeque(listOf(HandoffResult.Done)),
            )
            val sessions = sessions(transport)

            val result = sessions.fetch(TEST_SITE, TIMETABLE) { html -> html.length }

            assertEquals(SiteResult.Page(SIGNED_IN_DASHBOARD.html.length), result)
            assertEquals(1, transport.events.count { it.startsWith("handoff") })
            assertEquals(SiteStatus.SignedIn, sessions.status(TEST_SITE).value)
            assertEquals(PortalStatus.Live, sessions.portal.value)
        }
    }

    test("manual mode stops at the user when Portal is signed out") {
        runTest {
            val transport = ScriptedTransport(
                pages = ArrayDeque(listOf(page(SIGNED_OUT_REDIRECT))),
                handoffs = ArrayDeque(listOf(HandoffResult.PortalSignedOut)),
            )
            val sessions = sessions(transport)

            val result = sessions.fetch(TEST_SITE, TIMETABLE) { it }

            assertEquals(SiteResult.NeedsPortal(PortalNeed.MANUAL_SIGN_IN), result)
            assertEquals(
                SiteStatus.SignedOut(Renewal.PORTAL_SIGN_IN_BY_USER),
                sessions.status(TEST_SITE).value,
            )
        }
    }

    test("a displaced Portal stays blocked across a restart until the user signs in") {
        runTest {
            val guardStore = InMemoryPortalGuardStore()
            val first = sessions(
                ScriptedTransport(
                    pages = ArrayDeque(listOf(page(SIGNED_OUT_REDIRECT))),
                    handoffs = ArrayDeque(listOf(HandoffResult.PortalDisplaced)),
                ),
                guardStore,
            )
            assertEquals(SiteResult.NeedsPortal(PortalNeed.DISPLACED), first.fetch(TEST_SITE, TIMETABLE) { it })

            val restarted = sessions(ScriptedTransport(pages = ArrayDeque(listOf(page(SIGNED_OUT_REDIRECT)))), guardStore)
            restarted.refresh(TEST_SITE)
            assertEquals(PortalStatus.Blocked(PortalNeed.DISPLACED), restarted.portal.value)

            assertEquals(PortalSignIn.OpenBrowser("https://portal.ncu.edu.tw/login"), restarted.signInToPortal())
            assertEquals(PortalGuard(), guardStore.guard)
        }
    }

    test("the WebView reports Portal displacement and a later signed-in Portal page lifts it") {
        runTest {
            val guardStore = InMemoryPortalGuardStore()
            val sessions = sessions(ScriptedTransport(), guardStore)

            sessions.browserPage("https://portal.ncu.edu.tw/expired", loading = false)
            assertEquals(PortalStatus.Blocked(PortalNeed.DISPLACED), sessions.portal.value)

            sessions.browserPage("https://portal.ncu.edu.tw/login/set", loading = false)
            assertEquals(PortalStatus.Blocked(PortalNeed.DISPLACED), sessions.portal.value)

            sessions.browserPage("https://portal.ncu.edu.tw/", loading = false)
            assertEquals(PortalStatus.Live, sessions.portal.value)
            assertEquals(null, guardStore.guard.block)
        }
    }

    test("signing out everywhere blocks first, keeps Portal's browser cookie, and clears every site") {
        runTest {
            val guardStore = InMemoryPortalGuardStore()
            val transport = ScriptedTransport()
            val sessions = sessions(transport, guardStore)

            sessions.signOutEverywhere()

            assertEquals(PortalBlock.USER_SIGNED_OUT, guardStore.writes.first().block)
            assertEquals(
                listOf(
                    "clear https://portal.ncu.edu.tw keep=[portal]",
                    "clear https://ncueeclass.ncu.edu.tw keep=[]",
                ),
                transport.events,
            )
            assertEquals(PortalStatus.Blocked(PortalNeed.USER_SIGNED_OUT), sessions.portal.value)
        }
    }

    test("cancelling a check restores the previous site status") {
        runTest {
            val transport = ScriptedTransport(
                pages = ArrayDeque(listOf(page(SIGNED_IN_DASHBOARD), page(SIGNED_OUT_REDIRECT))),
                gatedFromRequest = 2,
                gate = CompletableDeferred(),
            )
            val sessions = sessions(transport)
            sessions.refresh(TEST_SITE)

            val job = launch { sessions.refresh(TEST_SITE) }
            yield()
            assertEquals(SiteStatus.Checking, sessions.status(TEST_SITE).value)
            job.cancelAndJoin()

            assertEquals(SiteStatus.SignedIn, sessions.status(TEST_SITE).value)
        }
    }

    test("checking Portal reports its status and a live session lifts a block") {
        runTest {
            val guardStore = InMemoryPortalGuardStore(PortalGuard(block = PortalBlock.DISPLACED))
            val transport = ScriptedTransport(
                portalChecks = ArrayDeque(
                    listOf(
                        PortalCheck.SignedOut,
                        PortalCheck.Failed(Unavailability.NETWORK),
                        PortalCheck.Live,
                        PortalCheck.Failed(Unavailability.NETWORK),
                    ),
                ),
            )
            val sessions = sessions(transport, guardStore)

            sessions.refreshPortal()
            assertEquals(PortalStatus.Blocked(PortalNeed.DISPLACED), sessions.portal.value)
            sessions.refreshPortal()
            assertEquals(PortalStatus.Blocked(PortalNeed.DISPLACED), sessions.portal.value)

            sessions.refreshPortal()
            assertEquals(PortalStatus.Live, sessions.portal.value)
            assertEquals(null, guardStore.guard.block)

            sessions.refreshPortal()
            assertEquals(PortalStatus.Unavailable(Unavailability.NETWORK), sessions.portal.value)
        }
    }

    test("a follow-up loads the link picked from the recovered page once") {
        runTest {
            val list = SiteResponse(status = 200, location = null, html = "list")
            val detail = SiteResponse(status = 200, location = null, html = "detail")
            val transport = ScriptedTransport(
                pages = ArrayDeque(listOf(page(SIGNED_OUT_REDIRECT), page(list), page(detail))),
                handoffs = ArrayDeque(listOf(HandoffResult.Done)),
            )

            val result = sessions(transport).fetchThen(TEST_SITE, TIMETABLE) { html ->
                if (html == "list") send(SiteRequest.Get("/content/?id=1&ajaxAuth=token")) else null
            }

            assertEquals(SiteResult.Page("detail"), result)
            assertEquals(
                listOf(
                    "get https://ncueeclass.ncu.edu.tw/dashboard/myTimeTable",
                    "handoff test",
                    "get https://ncueeclass.ncu.edu.tw/dashboard/myTimeTable",
                    "get https://ncueeclass.ncu.edu.tw/content/?id=1&ajaxAuth=token",
                ),
                transport.events,
            )
        }
    }

    test("a form POST carries a token read from the page a follow-up loaded") {
        runTest {
            val report = SiteResponse(status = 200, location = null, html = "/homework/editReport/?id=1&ajaxAuth=token")
            val editor = SiteResponse(status = 200, location = null, html = "anticsrf=abc")
            val saved = SiteResponse(status = 200, location = null, html = """{"ret":{"status":"true"}}""")
            val transport = ScriptedTransport(pages = ArrayDeque(listOf(page(report), page(editor), page(saved))))

            val result = sessions(transport).fetchThen(TEST_SITE, TIMETABLE) { editorLink ->
                val anticsrf = send(SiteRequest.Get(editorLink)).substringAfter('=')
                send(SiteRequest.Form(editorLink, listOf("action" to "temp", "anticsrf" to anticsrf)))
            }

            assertEquals(SiteResult.Page(saved.html), result)
            assertEquals(
                listOf(
                    "get https://ncueeclass.ncu.edu.tw/dashboard/myTimeTable",
                    "get https://ncueeclass.ncu.edu.tw/homework/editReport/?id=1&ajaxAuth=token",
                    "post https://ncueeclass.ncu.edu.tw/homework/editReport/?id=1&ajaxAuth=token form [(action, temp), (anticsrf, abc)]",
                ),
                transport.events,
            )
        }
    }

    test("a multipart POST sends its fields and files to the site") {
        runTest {
            val uploaded = SiteResponse(status = 200, location = null, html = """{"status":true}""")
            val transport = ScriptedTransport(pages = ArrayDeque(listOf(page(SIGNED_IN_DASHBOARD), page(uploaded))))
            val file = SiteFile("報告.pdf", "application/pdf", byteArrayOf(1, 2, 3))

            val result = sessions(transport).fetchThen(TEST_SITE, TIMETABLE) {
                send(SiteRequest.Multipart("/ajax/upload/?ajaxAuth=token", listOf("anticsrf" to "abc"), listOf("files[]" to file)))
            }

            assertEquals(SiteResult.Page(uploaded.html), result)
            assertEquals(
                "post https://ncueeclass.ncu.edu.tw/ajax/upload/?ajaxAuth=token multipart [(anticsrf, abc)] files [files[]=報告.pdf]",
                transport.events.last(),
            )
        }
    }

    test("a failed POST ends the call without sending it again or sending the rest") {
        runTest {
            val transport = ScriptedTransport(
                pages = ArrayDeque(listOf(page(SIGNED_IN_DASHBOARD), TransportResult.Failed(Unavailability.UNEXPECTED_RESPONSE))),
            )
            val sessions = sessions(transport)

            val result = sessions.fetchThen(TEST_SITE, TIMETABLE) {
                send(SiteRequest.Form("/save/", listOf("a" to "1")))
                send(SiteRequest.Get("/after/"))
            }

            assertEquals(SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE), result)
            assertEquals(
                listOf("get https://ncueeclass.ncu.edu.tw/dashboard/myTimeTable", "post https://ncueeclass.ncu.edu.tw/save/ form [(a, 1)]"),
                transport.events,
            )
            assertEquals(SiteStatus.SignedIn, sessions.status(TEST_SITE).value)
        }
    }

    test("a download recovers the session and returns the named file") {
        runTest {
            val file = SiteFile("講義.pdf", "application/pdf", byteArrayOf(9))
            val transport = ScriptedTransport(
                pages = ArrayDeque(listOf(page(SIGNED_OUT_REDIRECT), page(SiteResponse(200, null, "", file)))),
                handoffs = ArrayDeque(listOf(HandoffResult.Done)),
            )

            val result = sessions(transport).fetchFile(TEST_SITE, SitePath.of("/filedownload/5"))

            assertEquals("講義.pdf", (result as SiteResult.Page).value.name)
            assertEquals(
                listOf(
                    "download https://ncueeclass.ncu.edu.tw/filedownload/5",
                    "handoff test",
                    "download https://ncueeclass.ncu.edu.tw/filedownload/5",
                ),
                transport.events,
            )
        }
    }

    test("a download answered with a page instead of a file is unavailable") {
        runTest {
            val transport = ScriptedTransport(pages = ArrayDeque(listOf(page(SiteResponse(200, null, "<html>error</html>")))))

            val result = sessions(transport).fetchFile(TEST_SITE, SitePath.of("/filedownload/5"))

            assertEquals(SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE), result)
        }
    }

    test("a follow-up refuses links off the site or without https, and a page without the link") {
        runTest {
            val refused = listOf(
                "https://evil.example/content/?ajaxAuth=token",
                "//evil.example/content/",
                "http://ncueeclass.ncu.edu.tw/content/",
                "https://ncueeclass.ncu.edu.tw:8443/content/",
                null,
            )
            refused.forEach { link ->
                val transport = ScriptedTransport(pages = ArrayDeque(listOf(page(SIGNED_IN_DASHBOARD))))

                val result = sessions(transport).fetchThen(TEST_SITE, TIMETABLE) {
                    link?.let { refusedLink -> send(SiteRequest.Form(refusedLink, listOf("anticsrf" to "abc"))) }
                }

                assertEquals(SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE), result, "link $link")
                assertEquals(listOf("get https://ncueeclass.ncu.edu.tw/dashboard/myTimeTable"), transport.events, "link $link")
            }
        }
    }

    test("a follow-up that answers signed out marks the site signed out without recovering") {
        runTest {
            val transport = ScriptedTransport(pages = ArrayDeque(listOf(page(SIGNED_IN_DASHBOARD), page(SIGNED_OUT_REDIRECT))))
            val sessions = sessions(transport)

            val result = sessions.fetchThen(TEST_SITE, TIMETABLE) { send(SiteRequest.Get("/content/")) }

            assertEquals(SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE), result)
            assertEquals(0, transport.events.count { it.startsWith("handoff") })
            assertEquals(SiteStatus.SignedOut(Renewal.PORTAL_SIGN_IN_BY_USER), sessions.status(TEST_SITE).value)
        }
    }

    test("a follow-up kept past its call cannot send") {
        runTest {
            val transport = ScriptedTransport(pages = ArrayDeque(listOf(page(SIGNED_IN_DASHBOARD))))
            var kept: SiteFollowUp? = null

            sessions(transport).fetchThen(TEST_SITE, TIMETABLE) { kept = this }

            assertFailsWith<IllegalStateException> { kept?.send(SiteRequest.Get("/content/?ajaxAuth=token")) }
            assertEquals(listOf("get https://ncueeclass.ncu.edu.tw/dashboard/myTimeTable"), transport.events)
        }
    }

    test("a link opens in the browser only on a managed site, through Portal until the site is signed in") {
        runTest {
            val sessions = sessions(ScriptedTransport(pages = ArrayDeque(listOf(page(SIGNED_IN_DASHBOARD)))))
            val link = "https://ncueeclass.ncu.edu.tw/media/doc/1?page=2"

            assertEquals(TEST_SITE.portalEntryUrl, sessions.browserEntry(link))
            sessions.fetch(TEST_SITE, TIMETABLE) { it }
            assertEquals(link, sessions.browserEntry(link))
            assertEquals(null, sessions.browserEntry("https://example.com/"))
            assertEquals(null, sessions.browserEntry("http://ncueeclass.ncu.edu.tw/media/doc/1"))
        }
    }
}
