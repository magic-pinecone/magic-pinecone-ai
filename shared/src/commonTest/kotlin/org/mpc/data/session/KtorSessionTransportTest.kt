package org.mpc.data.session

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.mpc.domain.session.CampusSites
import org.mpc.domain.session.SiteFile
import org.mpc.domain.session.SiteHeader
import org.mpc.domain.session.SiteRequest
import org.mpc.domain.session.Unavailability
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PORTAL = "https://portal.ncu.edu.tw"
private const val EECLASS = "https://ncueeclass.ncu.edu.tw"
private const val SSO_LOGIN = "$EECLASS/ssologin?systemId=168&loginName=one-time&sn=1&verifyName=MTIz"

private fun MockRequestHandleScope.redirect(location: String, vararg setCookies: String): HttpResponseData = respond(
    content = "",
    status = HttpStatusCode.Found,
    headers = headersOf(
        HttpHeaders.Location to listOf(location),
        HttpHeaders.SetCookie to setCookies.toList(),
    ),
)

private fun transport(
    jar: PlatformCookieJar,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): Pair<KtorSessionTransport, MutableList<HttpRequestData>> {
    val requests = mutableListOf<HttpRequestData>()
    val client = HttpClient(
        MockEngine { request ->
            requests += request
            handler(request)
        },
    ) {
        expectSuccess = false
        followRedirects = false
    }
    return KtorSessionTransport(client, jar) to requests
}

val ktorSessionTransportTests by testSuite {
    test("a handoff arms Portal, follows each hop by hand, and stores the site session") {
        runTest {
            val jar = InMemoryCookieJar().apply { cookies["portal.ncu.edu.tw"] = mutableMapOf("SESSION" to "portal") }
            val (transport, requests) = transport(jar) { request ->
                when (request.url.toString()) {
                    "$PORTAL/system/ncueeclass" -> respond("<a href='/system/ncueeclass/go'>OK</a>")
                    "$PORTAL/system/ncueeclass/go" -> redirect(SSO_LOGIN)
                    SSO_LOGIN -> redirect("/dashboard", "PHPSESSID=site; path=/; secure; HttpOnly")
                    "$EECLASS/dashboard" -> respond("dashboard")
                    else -> error("unexpected request")
                }
            }

            assertEquals(HandoffResult.Done, transport.handoff(CampusSites.Eeclass))

            assertEquals(
                listOf("$PORTAL/system/ncueeclass", "$PORTAL/system/ncueeclass/go", SSO_LOGIN, "$EECLASS/dashboard"),
                requests.map { it.url.toString() },
            )
            assertEquals("SESSION=portal", requests[1].headers[HttpHeaders.Cookie])
            assertEquals("PHPSESSID=site", requests[3].headers[HttpHeaders.Cookie])
            assertEquals<Map<String, String>?>(mapOf("PHPSESSID" to "site"), jar.cookies["ncueeclass.ncu.edu.tw"])
        }
    }

    test("a handoff reports Portal sign-in and displacement") {
        runTest {
            val (signedOut, _) = transport(InMemoryCookieJar()) { redirect("$PORTAL/login") }
            val (displaced, _) = transport(InMemoryCookieJar()) { redirect("/expired") }

            assertEquals(HandoffResult.PortalSignedOut, signedOut.handoff(CampusSites.Eeclass))
            assertEquals(HandoffResult.PortalDisplaced, displaced.handoff(CampusSites.Eeclass))
        }
    }

    test("a handoff refuses redirects off Portal and the site, and an unarmed fallback home") {
        runTest {
            val (offHost, offHostRequests) = transport(InMemoryCookieJar()) { request ->
                if (request.url.encodedPath.endsWith("/go")) redirect("https://evil.example/steal") else respond("ok")
            }
            val (unarmed, _) = transport(InMemoryCookieJar()) { request ->
                if (request.url.encodedPath.endsWith("/go")) redirect("$PORTAL/") else respond("ok")
            }

            assertEquals(HandoffResult.Failed(Unavailability.UNEXPECTED_RESPONSE), offHost.handoff(CampusSites.Eeclass))
            assertEquals(2, offHostRequests.size)
            assertEquals(HandoffResult.Failed(Unavailability.UNEXPECTED_RESPONSE), unarmed.handoff(CampusSites.Eeclass))
        }
    }

    test("a WebView cookie change during a request makes the response stale and keeps its cookies out") {
        runTest {
            val jar = InMemoryCookieJar().apply { cookies["ncueeclass.ncu.edu.tw"] = mutableMapOf("PHPSESSID" to "before") }
            val (transport, requests) = transport(jar) {
                jar.cookies.getValue("ncueeclass.ncu.edu.tw")["PHPSESSID"] = "after"
                redirect("/index/login", "PHPSESSID=stale; Path=/")
            }

            val result = transport.get("$EECLASS/dashboard")

            assertEquals("PHPSESSID=before", requests.single().headers[HttpHeaders.Cookie])
            assertEquals(TransportResult.Failed(Unavailability.UNEXPECTED_RESPONSE), result)
            assertEquals("after", jar.cookies.getValue("ncueeclass.ncu.edu.tw")["PHPSESSID"])
        }
    }

    test("a WebView cookie change during a POST makes the reply stale and keeps its cookies out") {
        runTest {
            val jar = InMemoryCookieJar().apply { cookies["ncueeclass.ncu.edu.tw"] = mutableMapOf("PHPSESSID" to "before") }
            val (transport, _) = transport(jar) {
                jar.cookies.getValue("ncueeclass.ncu.edu.tw")["PHPSESSID"] = "after"
                respond("{}", headers = headersOf(HttpHeaders.SetCookie, "PHPSESSID=stale; Path=/"))
            }

            val result = transport.send(SiteRequest.Form("$EECLASS/save/", listOf("action" to "temp")))

            assertEquals(TransportResult.Failed(Unavailability.UNEXPECTED_RESPONSE), result)
            assertEquals("after", jar.cookies.getValue("ncueeclass.ncu.edu.tw")["PHPSESSID"])
        }
    }

    test("a form POST sends its fields in order with the allowed headers, as a body that cannot be replayed") {
        runTest {
            val (transport, requests) = transport(InMemoryCookieJar()) { respond("""{"ret":{"status":"true"}}""") }

            val result = transport.send(
                SiteRequest.Form(
                    link = "$EECLASS/homework/editReport/?id=1",
                    fields = listOf("action" to "temp", "title" to "第一次 作業", "anticsrf" to "a+b/c"),
                    headers = mapOf(SiteHeader.X_REQUESTED_WITH to "XMLHttpRequest"),
                ),
            )

            val request = requests.single()
            assertEquals("""{"ret":{"status":"true"}}""", (result as TransportResult.Response).response.html)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("XMLHttpRequest", request.headers["X-Requested-With"])
            assertEquals(ContentType.Application.FormUrlEncoded, request.body.contentType?.withoutParameters())
            assertEquals(
                "action=temp&title=%E7%AC%AC%E4%B8%80%E6%AC%A1+%E4%BD%9C%E6%A5%AD&anticsrf=a%2Bb%2Fc",
                request.body.toByteArray().decodeToString(),
            )
            // OkHttp resends replayable bodies after a dropped connection, which would repeat the write.
            assertIs<OutgoingContent.ReadChannelContent>(request.body)
        }
    }

    test("a POST without fields sends an empty body") {
        runTest {
            val (transport, requests) = transport(InMemoryCookieJar()) { respond("""{"ret":{"status":true,"msg":"ok"}}""") }

            transport.send(SiteRequest.Form("$EECLASS/ajax/sys.pages.homework_report/submit/?reportId=1", emptyList()))

            assertEquals(HttpMethod.Post, requests.single().method)
            assertEquals(0, requests.single().body.toByteArray().size)
        }
    }

    test("a multipart POST sends fields, then each file with its name and type") {
        runTest {
            var body = ""
            val (transport, requests) = transport(InMemoryCookieJar()) { request ->
                body = request.body.toByteArray().decodeToString()
                respond("""{"status":true}""")
            }
            val progress = mutableListOf<Long>()

            transport.send(
                SiteRequest.Multipart(
                    link = "$EECLASS/ajax/sys.modules.mod_fileUpload2/upload/?pageId=report.1",
                    fields = listOf("action" to "submit", "anticsrf" to "abc"),
                    files = listOf("files[]" to SiteFile("報告.pdf", "application/pdf", "%PDF-1".encodeToByteArray())),
                    onProgress = { sent, _ -> progress += sent },
                ),
            )

            assertEquals(ContentType.MultiPart.FormData, requests.single().body.contentType?.withoutParameters())
            assertTrue("Content-Disposition: form-data; name=\"anticsrf\"" in body, body)
            assertTrue("Content-Disposition: form-data; name=\"files[]\"; filename=\"報告.pdf\"" in body, body)
            assertTrue("Content-Type: application/pdf" in body, body)
            assertTrue("%PDF-1" in body, body)
            assertTrue(body.indexOf("anticsrf") < body.indexOf("files[]"), body)
            assertEquals(body.encodeToByteArray().size.toLong(), progress.last())
        }
    }

    test("a download reads a file's bytes, name, and type, and a page as HTML") {
        runTest {
            val (transport, _) = transport(InMemoryCookieJar()) { request ->
                when (request.url.encodedPath) {
                    "/filedownload/5" -> respond(
                        content = byteArrayOf(0x25, 0x50, 0x44, 0x46, -1),
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf("application/pdf"),
                            HttpHeaders.ContentDisposition to listOf("attachment; filename=\"fallback.pdf\"; filename*=UTF-8''%E8%AC%9B%E7%BE%A9.pdf"),
                        ),
                    )

                    else -> respond("<form id='login_form'></form>", headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
                }
            }

            val file = (transport.send(SiteRequest.Download("$EECLASS/filedownload/5")) as TransportResult.Response).response
            val page = (transport.send(SiteRequest.Download("$EECLASS/filedownload/6")) as TransportResult.Response).response

            assertEquals("講義.pdf", file.file?.name)
            assertEquals("application/pdf", file.file?.contentType)
            assertContentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46, -1), file.file?.bytes)
            assertEquals("", file.html)
            assertNull(page.file)
            assertEquals("<form id='login_form'></form>", page.html)
        }
    }

    test("a file name comes from filename* first, then filename") {
        assertEquals("講義 v2.pdf", dispositionFileName("attachment; filename*=UTF-8''%E8%AC%9B%E7%BE%A9%20v2.pdf"))
        assertEquals("notes.pdf", dispositionFileName("attachment; filename=\"notes.pdf\""))
        assertEquals("notes.pdf", dispositionFileName("attachment; filename*=ISO-8859-1''x.pdf; filename=notes.pdf"))
        assertNull(dispositionFileName("attachment"))
    }

    test("a network failure carries only its reason") {
        runTest {
            val (transport, _) = transport(InMemoryCookieJar()) { error("connection reset for $SSO_LOGIN") }

            assertEquals(TransportResult.Failed(Unavailability.NETWORK), transport.get("$EECLASS/dashboard"))
        }
    }

    test("a request without cookies sends no Cookie header") {
        runTest {
            val (transport, requests) = transport(InMemoryCookieJar()) { respond("page") }

            assertIs<TransportResult.Response>(transport.get("$EECLASS/dashboard"))
            assertNull(requests.single().headers[HttpHeaders.Cookie])
        }
    }

    test("overlapping requests do not mistake each other's cookies for a WebView change") {
        runTest {
            val jar = InMemoryCookieJar()
            val (transport, _) = transport(jar) {
                yield()
                redirect("/index/login", "PHPSESSID=fresh; Path=/; Secure; HttpOnly")
            }

            val results = listOf(
                async { transport.get("$EECLASS/dashboard") },
                async { transport.get("$EECLASS/dashboard") },
            ).awaitAll()

            assertEquals(listOf(true, true), results.map { it is TransportResult.Response })
        }
    }

    test("a Portal check reads its home page as live, signed out, or displaced") {
        runTest {
            val (live, liveRequests) = transport(InMemoryCookieJar()) { respond("home") }
            val (signedOut, _) = transport(InMemoryCookieJar()) { redirect("/login") }
            val (displaced, _) = transport(InMemoryCookieJar()) { redirect("$PORTAL/expired") }
            val (elsewhere, _) = transport(InMemoryCookieJar()) { redirect("https://evil.example/") }

            assertEquals(PortalCheck.Live, live.checkPortal())
            assertEquals("$PORTAL/", liveRequests.single().url.toString())
            assertEquals(PortalCheck.SignedOut, signedOut.checkPortal())
            assertEquals(PortalCheck.Displaced, displaced.checkPortal())
            assertEquals(PortalCheck.Failed(Unavailability.UNEXPECTED_RESPONSE), elsewhere.checkPortal())
        }
    }
}
