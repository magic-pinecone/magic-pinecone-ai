package org.mpc.data.session

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.content.ProgressListener
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.decodeURLPart
import io.ktor.http.parameters
import io.ktor.http.quote
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mpc.domain.session.CampusSite
import org.mpc.domain.session.PORTAL_BASE_URL
import org.mpc.domain.session.PortalPage
import org.mpc.domain.session.SiteFile
import org.mpc.domain.session.SiteRequest
import org.mpc.domain.session.SiteResponse
import org.mpc.domain.session.Unavailability
import org.mpc.domain.session.isPortalUrl
import org.mpc.domain.session.parseHttpsUrl
import org.mpc.domain.session.portalPage

internal sealed interface TransportResult {
    data class Response(val response: SiteResponse) : TransportResult

    data class Failed(val reason: Unavailability) : TransportResult
}

internal sealed interface HandoffResult {
    data object Done : HandoffResult

    /** Portal sent the handoff to its sign-in page. */
    data object PortalSignedOut : HandoffResult

    /** Portal sent the handoff to `/expired`: another sign-in replaced this one. */
    data object PortalDisplaced : HandoffResult

    data class Failed(val reason: Unavailability) : HandoffResult
}

internal sealed interface PortalCheck {
    data object Live : PortalCheck

    data object SignedOut : PortalCheck

    /** Another sign-in replaced this one. */
    data object Displaced : PortalCheck

    data class Failed(val reason: Unavailability) : PortalCheck
}

/** HTTP for campus sessions over the shared [PlatformCookieJar]. The only client that carries campus cookies. */
internal interface SessionTransport {
    suspend fun send(request: SiteRequest<*>): TransportResult

    /** Reads Portal's home page without following redirects. Checking never signs anyone in or out. */
    suspend fun checkPortal(): PortalCheck

    /** Runs [site]'s Portal handoff, following each redirect by hand so the one-time sign-in URL never leaves. */
    suspend fun handoff(site: CampusSite): HandoffResult

    suspend fun clearCookies(url: String, keep: Set<String> = emptySet())
}

private const val REQUEST_TIMEOUT_MILLIS = 15_000L
private const val TRANSFER_TIMEOUT_MILLIS = 300_000L
private const val TRANSFER_IDLE_TIMEOUT_MILLIS = 60_000L
private const val CONNECT_TIMEOUT_MILLIS = 10_000L
private const val MAX_HANDOFF_HOPS = 6
private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
private const val HTTP_STATUS_OK = 200

/** A Ktor client whose engine keeps no cookies of its own, so [PlatformCookieJar] stays the only jar. */
internal expect fun createSessionHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient

internal fun createDefaultSessionHttpClient(): HttpClient = createSessionHttpClient {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
    }
}

internal class KtorSessionTransport(
    private val httpClient: HttpClient,
    private val cookieJar: PlatformCookieJar,
) : SessionTransport {
    // Requests run one at a time, so only the WebView can change the jar while one is in flight. Its cookies are
    // newer, so such a response is stale: report it unavailable and keep its Set-Cookie lines out of the jar.
    private val requestLock = Mutex()

    override suspend fun send(request: SiteRequest<*>): TransportResult = requestLock.withLock {
        val url = request.link
        try {
            val cookiesBeforeRequest = cookieJar.cookieHeader(url)
            val response = httpClient.request(url) {
                cookiesBeforeRequest?.let { header(HttpHeaders.Cookie, it.value) }
                request.headers.forEach { (name, value) -> header(name.wireName, value) }
                describe(request)
            }
            val file = if (request is SiteRequest.Download) siteFile(response) else null
            val body = if (file == null) response.bodyAsText() else ""
            if (cookieJar.cookieHeader(url) != cookiesBeforeRequest) {
                TransportResult.Failed(Unavailability.UNEXPECTED_RESPONSE)
            } else {
                val setCookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
                if (setCookies.isNotEmpty()) cookieJar.storeSetCookies(url, setCookies)
                TransportResult.Response(
                    SiteResponse(
                        status = response.status.value,
                        location = response.headers[HttpHeaders.Location],
                        html = body,
                        file = file,
                    ),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Ktor exception messages include URLs; only the reason crosses this boundary.
            TransportResult.Failed(Unavailability.NETWORK)
        }
    }

    override suspend fun handoff(site: CampusSite): HandoffResult {
        var hop: Hop = Hop.Next(Url(site.portalEntryUrl))
        repeat(MAX_HANDOFF_HOPS) {
            val current = hop
            if (current is Hop.Next) hop = hop(site, current.url)
        }
        return (hop as? Hop.End)?.result ?: HandoffResult.Failed(Unavailability.UNEXPECTED_RESPONSE)
    }

    override suspend fun checkPortal(): PortalCheck = when (val result = get(PORTAL_HOME.toString())) {
        is TransportResult.Failed -> PortalCheck.Failed(result.reason)
        is TransportResult.Response -> portalCheck(result.response)
    }

    override suspend fun clearCookies(url: String, keep: Set<String>) {
        cookieJar.clear(url, keep)
    }

    private suspend fun hop(site: CampusSite, url: Url): Hop = when (val result = get(url.toString())) {
        is TransportResult.Failed -> Hop.End(HandoffResult.Failed(result.reason))
        is TransportResult.Response -> nextHop(site, url, result.response)
    }
}

internal suspend fun SessionTransport.get(url: String): TransportResult = send(SiteRequest.Get(url))

private fun HttpRequestBuilder.describe(request: SiteRequest<*>) {
    when (request) {
        is SiteRequest.Get -> method = HttpMethod.Get

        is SiteRequest.Download -> {
            method = HttpMethod.Get
            transferTimeout()
            onDownload(request.onProgress?.let { progress -> ProgressListener { sent, total -> progress(sent, total) } })
        }

        is SiteRequest.Form -> {
            method = HttpMethod.Post
            setBody(OneShot(FormDataContent(parameters { request.fields.forEach { (name, value) -> append(name, value) } })))
        }

        is SiteRequest.Multipart -> {
            method = HttpMethod.Post
            transferTimeout()
            onUpload(request.onProgress?.let { progress -> ProgressListener { sent, total -> progress(sent, total) } })
            setBody(
                MultiPartFormDataContent(
                    formData {
                        request.fields.forEach { (name, value) -> append(name, value) }
                        request.files.forEach { (name, file) -> append(name, file.bytes, fileHeaders(name, file)) }
                    },
                ),
            )
        }
    }
}

private fun HttpRequestBuilder.transferTimeout() = timeout {
    requestTimeoutMillis = TRANSFER_TIMEOUT_MILLIS
    socketTimeoutMillis = TRANSFER_IDLE_TIMEOUT_MILLIS
}

/**
 * [content] as a stream. OkHttp resends a replayable body after a dropped connection, which would repeat a write the
 * site may already have applied; it never replays a stream. Multipart bodies are streams already.
 */
private class OneShot(private val content: OutgoingContent.ByteArrayContent) : OutgoingContent.ReadChannelContent() {
    override val contentType: ContentType? get() = content.contentType
    override val contentLength: Long? get() = content.contentLength

    override fun readFrom(): ByteReadChannel = ByteReadChannel(content.bytes())
}

private fun fileHeaders(field: String, file: SiteFile): Headers = Headers.build {
    append(HttpHeaders.ContentType, file.contentType ?: ContentType.Application.OctetStream.toString())
    append(HttpHeaders.ContentDisposition, "filename=${(file.name ?: field).quote()}")
}

private suspend fun siteFile(response: HttpResponse): SiteFile? {
    val contentType = response.contentType()
    if (contentType?.match(ContentType.Text.Html) == true) return null
    return SiteFile(
        name = response.headers[HttpHeaders.ContentDisposition]?.let(::dispositionFileName),
        contentType = contentType?.withoutParameters()?.toString(),
        bytes = response.bodyAsBytes(),
    )
}

internal fun dispositionFileName(value: String): String? {
    val disposition = runCatching { ContentDisposition.parse(value) }.getOrNull() ?: return null
    val extended = disposition.parameter("filename*")
        ?.takeIf { it.startsWith("UTF-8''", ignoreCase = true) }
        ?.let { runCatching { it.substringAfter("''").decodeURLPart() }.getOrNull() }
    return (extended ?: disposition.parameter(ContentDisposition.Parameters.FileName))?.takeIf { it.isNotBlank() }
}

private val PORTAL_HOME = Url("$PORTAL_BASE_URL/")

private fun portalCheck(response: SiteResponse): PortalCheck {
    val redirect = if (response.status in REDIRECT_STATUS_CODES) resolve(PORTAL_HOME, response.location) else null
    return when {
        response.status == HTTP_STATUS_OK -> PortalCheck.Live

        redirect == null || !isPortalUrl(redirect) -> PortalCheck.Failed(Unavailability.UNEXPECTED_RESPONSE)

        else -> when (portalPage(redirect)) {
            PortalPage.SIGN_IN -> PortalCheck.SignedOut
            PortalPage.EXPIRED -> PortalCheck.Displaced
            PortalPage.OTHER -> PortalCheck.Failed(Unavailability.UNEXPECTED_RESPONSE)
        }
    }
}

private sealed interface Hop {
    data class Next(val url: Url) : Hop

    data class End(val result: HandoffResult) : Hop
}

/**
 * One step of a handoff: the armed Portal entry continues to `/go`; redirects continue only to Portal or the site;
 * Portal's sign-in and expired pages end it; a page on the site means the site session is in place.
 */
private fun nextHop(site: CampusSite, url: Url, response: SiteResponse): Hop {
    val redirect = if (response.status in REDIRECT_STATUS_CODES) resolve(url, response.location) else null
    val onSite = { target: Url -> target.host.equals(site.host, ignoreCase = true) }
    return when {
        response.status == HTTP_STATUS_OK && url.toString() == site.portalEntryUrl ->
            Hop.Next(Url("${site.portalEntryUrl}/go"))

        response.status == HTTP_STATUS_OK && onSite(url) -> Hop.End(HandoffResult.Done)

        redirect == null -> Hop.End(HandoffResult.Failed(Unavailability.UNEXPECTED_RESPONSE))

        isPortalUrl(redirect) -> Hop.End(
            when (portalPage(redirect)) {
                PortalPage.SIGN_IN -> HandoffResult.PortalSignedOut

                PortalPage.EXPIRED -> HandoffResult.PortalDisplaced

                // `/go` without an armed handoff falls back to Portal's home page.
                PortalPage.OTHER -> HandoffResult.Failed(Unavailability.UNEXPECTED_RESPONSE)
            },
        )

        onSite(redirect) -> Hop.Next(redirect)

        else -> Hop.End(HandoffResult.Failed(Unavailability.UNEXPECTED_RESPONSE))
    }
}

// Resolve by hand: Ktor's URLBuilder.takeFrom keeps the base query for an absolute-path reference, which would
// copy the one-time ssologin parameters onto the next request. Campus redirects are absolute URLs or paths.
private fun resolve(base: Url, location: String?): Url? = when {
    location == null -> null
    location.startsWith("/") && !location.startsWith("//") -> parseHttpsUrl("https://${base.host}$location")
    else -> parseHttpsUrl(location)
}
