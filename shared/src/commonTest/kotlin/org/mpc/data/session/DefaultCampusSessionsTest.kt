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
import org.mpc.domain.session.SitePath
import org.mpc.domain.session.SiteResponse
import org.mpc.domain.session.SiteResult
import org.mpc.domain.session.SiteSignals
import org.mpc.domain.session.SiteStatus
import org.mpc.domain.session.SiteVerdict
import org.mpc.domain.session.Unavailability
import kotlin.test.assertEquals
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

    override suspend fun get(url: String): TransportResult {
        events += "get $url"
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

    test("following loads the picked same-site link from the recovered page once") {
        runTest {
            val list = SiteResponse(status = 200, location = null, html = "list")
            val detail = SiteResponse(status = 200, location = null, html = "detail")
            val transport = ScriptedTransport(
                pages = ArrayDeque(listOf(page(SIGNED_OUT_REDIRECT), page(list), page(detail))),
                handoffs = ArrayDeque(listOf(HandoffResult.Done)),
            )

            val result = sessions(transport).fetchFollowing(
                site = TEST_SITE,
                path = TIMETABLE,
                pick = { html -> "/content/?id=1&ajaxAuth=token".takeIf { html == "list" } },
                parse = { html -> html },
            )

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

    test("following refuses links off the site or without https, and a missing link") {
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

                val result = sessions(transport).fetchFollowing(TEST_SITE, TIMETABLE, pick = { link }, parse = { it })

                assertEquals(SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE), result, "link $link")
                assertEquals(listOf("get https://ncueeclass.ncu.edu.tw/dashboard/myTimeTable"), transport.events, "link $link")
            }
        }
    }

    test("a followed link that answers signed out marks the site signed out without recovering") {
        runTest {
            val transport = ScriptedTransport(pages = ArrayDeque(listOf(page(SIGNED_IN_DASHBOARD), page(SIGNED_OUT_REDIRECT))))
            val sessions = sessions(transport)

            val result = sessions.fetchFollowing(TEST_SITE, TIMETABLE, pick = { "/content/" }, parse = { it })

            assertEquals(SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE), result)
            assertEquals(0, transport.events.count { it.startsWith("handoff") })
            assertEquals(SiteStatus.SignedOut(Renewal.PORTAL_SIGN_IN_BY_USER), sessions.status(TEST_SITE).value)
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
