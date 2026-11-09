package org.mpc.domain.session

import io.ktor.http.Url
import org.mpc.domain.html.HtmlParser
import kotlin.jvm.JvmInline

const val PORTAL_HOST = "portal.ncu.edu.tw"
const val PORTAL_BASE_URL = "https://$PORTAL_HOST"
internal const val HTTPS_DEFAULT_PORT = 443
private const val CAMPUS_DOMAIN = "ncu.edu.tw"

/**
 * A path on a campus site: starts with "/", plain segments only, and an optional query such as `?page=2&pageSize=20`.
 * Query keys come from a short list of paging and filter keys, so a path never carries a token and is safe to keep.
 */
@JvmInline
value class SitePath private constructor(val value: String) {
    val query: List<Pair<String, String>>
        get() = value.substringAfter('?', "").takeIf { it.isNotEmpty() }?.split('&')?.map { pair ->
            pair.substringBefore('=') to pair.substringAfter('=')
        }.orEmpty()

    fun withQuery(vararg pairs: Pair<String, String>): SitePath {
        val added = pairs.joinToString("&") { (key, value) -> "$key=$value" }
        return of(value + (if (query.isEmpty()) "?" else "&") + added)
    }

    companion object {
        fun parse(raw: String): SitePath? {
            val path = raw.substringBefore('?')
            val validPath = path.startsWith("/") && path.drop(1).split('/').all { segment -> segment.matches(PATH_SEGMENT) }
            val validQuery = '?' !in raw || raw.substringAfter('?').split('&').all(::isPlainPair)
            return if (validPath && validQuery) SitePath(raw) else null
        }

        fun of(literal: String): SitePath = requireNotNull(parse(literal)) { "Invalid site path" }
    }
}

private val PATH_SEGMENT = Regex("[A-Za-z0-9_-]+")
private val QUERY_VALUE = Regex("[A-Za-z0-9_.~-]*")

private val TOKEN_FREE_QUERY_KEYS = setOf("page", "pageSize", "size", "category", "condition")

private fun isPlainPair(pair: String): Boolean = '=' in pair && pair.substringBefore('=') in TOKEN_FREE_QUERY_KEYS && pair.substringAfter('=').matches(QUERY_VALUE)

/**
 * What a campus site answered: status, raw `Location`, and body. No other headers, so classifiers never see cookies.
 * A [SiteRequest.Download] that is not HTML arrives as [file] with [html] empty, so no classifier parses its bytes.
 */
class SiteResponse(
    val status: Int,
    val location: String?,
    val html: String,
    val file: SiteFile? = null,
)

/** A file's bytes, named from its `Content-Disposition` and typed from its `Content-Type`; null when the site sent none. */
class SiteFile(
    val name: String?,
    val contentType: String?,
    val bytes: ByteArray,
)

/** The request headers a caller may set. The session sets cookies and the body's type itself. */
enum class SiteHeader(val wireName: String) {
    ACCEPT("Accept"),
    REFERER("Referer"),
    X_REQUESTED_WITH("X-Requested-With"),
}

/** Bytes moved so far and the total when the site announced one. */
typealias TransferProgress = (sent: Long, total: Long?) -> Unit

/**
 * A request to a campus site, answered with what [R] names. Its [link] may carry tokens, so build it inside
 * [CampusSessions.fetchThen] and let it go nowhere else.
 */
sealed class SiteRequest<out R> {
    /** An absolute URL or a root-relative path; it must resolve to https on the requested site. */
    abstract val link: String
    abstract val headers: Map<SiteHeader, String>

    internal abstract fun reply(response: SiteResponse): R?

    internal abstract fun withLink(url: String): SiteRequest<R>

    final override fun toString(): String = "SiteRequest"

    class Get(
        override val link: String,
        override val headers: Map<SiteHeader, String> = emptyMap(),
    ) : SiteRequest<String>() {
        override fun reply(response: SiteResponse): String = response.html

        override fun withLink(url: String) = Get(url, headers)
    }

    /** A POST of [fields] as `application/x-www-form-urlencoded`, in order. */
    class Form(
        override val link: String,
        val fields: List<Pair<String, String>>,
        override val headers: Map<SiteHeader, String> = emptyMap(),
    ) : SiteRequest<String>() {
        override fun reply(response: SiteResponse): String = response.html

        override fun withLink(url: String) = Form(url, fields, headers)
    }

    /** A `multipart/form-data` POST of [fields], then [files] keyed by form field name. */
    class Multipart(
        override val link: String,
        val fields: List<Pair<String, String>>,
        val files: List<Pair<String, SiteFile>>,
        override val headers: Map<SiteHeader, String> = emptyMap(),
        val onProgress: TransferProgress? = null,
    ) : SiteRequest<String>() {
        override fun reply(response: SiteResponse): String = response.html

        override fun withLink(url: String) = Multipart(url, fields, files, headers, onProgress)
    }

    /** A GET read as bytes. */
    class Download(
        override val link: String,
        override val headers: Map<SiteHeader, String> = emptyMap(),
        val onProgress: TransferProgress? = null,
    ) : SiteRequest<SiteFile>() {
        override fun reply(response: SiteResponse): SiteFile? = response.file

        override fun withLink(url: String) = Download(url, headers, onProgress)
    }
}

enum class SiteVerdict {
    SIGNED_IN,
    SIGNED_OUT,
    UNRECOGNIZED,
}

/** Reads a site's own signed-in and signed-out signals. The only code a new site writes. */
fun interface SiteSignals {
    fun classify(response: SiteResponse, parser: HtmlParser): SiteVerdict
}

/**
 * A campus site whose session comes from a Portal handoff: `GET /system/{portalSystemId}` arms it and
 * `/system/{portalSystemId}/go` redirects to the site with a fresh session.
 */
class CampusSite(
    val id: String,
    val displayName: String,
    val host: String,
    val portalSystemId: String,
    /** A page whose response proves a live session; [signals] must read [SiteVerdict.SIGNED_IN] there. */
    val probePath: SitePath,
    val signals: SiteSignals,
) {
    val baseUrl: String = "https://$host"
    val portalEntryUrl: String = "$PORTAL_BASE_URL/system/$portalSystemId"

    fun url(path: SitePath): String = baseUrl + path.value
}

/** The one list of campus sites the app keeps sessions for. */
object CampusSites {
    val Eeclass = CampusSite(
        id = "ncueeclass",
        displayName = "新 ee-class",
        host = "ncueeclass.ncu.edu.tw",
        portalSystemId = "ncueeclass",
        probePath = SitePath.of("/dashboard"),
        signals = EeclassSignals,
    )

    val all: List<CampusSite> = listOf(Eeclass)

    /** The managed site serving [url], or null for Portal, other hosts, and non-https or non-443 URLs. */
    fun forUrl(url: String): CampusSite? {
        val parsed = parseHttpsUrl(url) ?: return null
        return all.firstOrNull { it.host.equals(parsed.host, ignoreCase = true) }
    }

    /** JavaScript runs only on https campus hosts; Portal sign-in needs it for reCAPTCHA. */
    fun allowsJavascript(url: String): Boolean {
        val host = parseHttpsUrl(url)?.host?.lowercase() ?: return false
        return host == CAMPUS_DOMAIN || host.endsWith(".$CAMPUS_DOMAIN")
    }
}

internal fun parseHttpsUrl(value: String): Url? = runCatching { Url(value) }
    .getOrNull()
    ?.takeIf { it.protocol.name == "https" && it.port == HTTPS_DEFAULT_PORT }

internal fun isPortalUrl(url: Url): Boolean = url.host.equals(PORTAL_HOST, ignoreCase = true)

internal enum class PortalPage {
    /** Portal's sign-in flow, including steps such as `/login/set`. */
    SIGN_IN,

    /** Another sign-in replaced this session. */
    EXPIRED,

    /** Any other Portal page. The app opens only Portal pages that need a session, so one loading means Portal is signed in. */
    OTHER,
}

internal fun portalPage(url: Url): PortalPage = when {
    url.encodedPath == "/expired" -> PortalPage.EXPIRED
    url.encodedPath == "/login" || url.encodedPath.startsWith("/login/") -> PortalPage.SIGN_IN
    else -> PortalPage.OTHER
}

/**
 * The `/go` URL that continues Portal's "about to open" confirmation at [url], or null for any other page.
 * Loading the confirmation arms the launch; any consent the target asks for afterwards stays with the user.
 */
fun portalLaunchUrl(url: String): String? {
    val parsed = parseHttpsUrl(url)?.takeIf(::isPortalUrl) ?: return null
    return if (parsed.encodedPath.matches(PORTAL_LAUNCH_PATH)) "$PORTAL_BASE_URL${parsed.encodedPath}/go" else null
}

private val PORTAL_LAUNCH_PATH = Regex("/system/[A-Za-z0-9_-]+")
