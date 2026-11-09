package org.mpc.domain.session

import io.ktor.http.Url
import org.mpc.domain.html.HtmlElement
import org.mpc.domain.html.HtmlParser

private const val EECLASS_BASE_URL = "https://ncueeclass.ncu.edu.tw"
private const val HTTP_STATUS_OK = 200
private const val HTTP_STATUS_UNAUTHORIZED = 401
private val LOGIN_REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

/**
 * ee-class signals: a redirect to `/index/login`, a 401, or `form#login_form` means signed out; a dashboard page
 * with same-origin links to the timetable and latest events means signed in.
 */
internal object EeclassSignals : SiteSignals {
    override fun classify(response: SiteResponse, parser: HtmlParser): SiteVerdict {
        if (response.status in LOGIN_REDIRECT_STATUS_CODES && isLoginLocation(response.location)) {
            return SiteVerdict.SIGNED_OUT
        }
        if (response.status == HTTP_STATUS_UNAUTHORIZED) return SiteVerdict.SIGNED_OUT
        if (response.status != HTTP_STATUS_OK) return SiteVerdict.UNRECOGNIZED

        val root = parser.parse(response.html, "$EECLASS_BASE_URL/")
        if (hasLoginForm(root)) return SiteVerdict.SIGNED_OUT
        val links = dashboardLinks(root)
        return if ("/dashboard/myTimeTable" in links && "/dashboard/latestEvent" in links) {
            SiteVerdict.SIGNED_IN
        } else {
            SiteVerdict.UNRECOGNIZED
        }
    }
}

private fun isLoginLocation(location: String?): Boolean {
    if (location == null) return false
    val url = resolve(location) ?: return false
    return url.protocol.name == "https" && url.port == HTTPS_DEFAULT_PORT &&
        url.host.equals(CampusSites.Eeclass.host, ignoreCase = true) &&
        url.encodedPath == "/index/login"
}

private fun hasLoginForm(root: HtmlElement): Boolean = root.select("form#login_form").isNotEmpty()

private fun dashboardLinks(root: HtmlElement): Set<String> = root.select("a[href]").mapNotNull { anchor ->
    val url = anchor.absUrl("href")?.let(::parseHttpsUrl) ?: return@mapNotNull null
    url.encodedPath.takeIf { url.host.equals(CampusSites.Eeclass.host, ignoreCase = true) }
}.toSet()

private fun resolve(reference: String): Url? = runCatching {
    if (reference.startsWith("/")) Url("$EECLASS_BASE_URL$reference") else Url(reference)
}.getOrNull()
