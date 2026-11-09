package org.mpc.data.session

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mpc.domain.html.HtmlParser
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.repository.AppSettingsRepository
import org.mpc.domain.session.CampusSessions
import org.mpc.domain.session.CampusSite
import org.mpc.domain.session.CampusSites
import org.mpc.domain.session.PORTAL_BASE_URL
import org.mpc.domain.session.PortalNeed
import org.mpc.domain.session.PortalPage
import org.mpc.domain.session.PortalSignIn
import org.mpc.domain.session.PortalStatus
import org.mpc.domain.session.Renewal
import org.mpc.domain.session.SiteFile
import org.mpc.domain.session.SiteFollowUp
import org.mpc.domain.session.SitePath
import org.mpc.domain.session.SiteRequest
import org.mpc.domain.session.SiteResponse
import org.mpc.domain.session.SiteResult
import org.mpc.domain.session.SiteStatus
import org.mpc.domain.session.SiteVerdict
import org.mpc.domain.session.TransferProgress
import org.mpc.domain.session.Unavailability
import org.mpc.domain.session.isPortalUrl
import org.mpc.domain.session.parseHttpsUrl
import org.mpc.domain.session.portalPage
import kotlin.time.Clock
import kotlin.time.Instant

private const val HTTP_STATUS_OK = 200

/** Portal's long-lived browser cookie. Kept on sign-out so Portal still trusts this browser for TOTP. */
private const val PORTAL_BROWSER_COOKIE = "portal"

@ContributesTo(AppScope::class)
@BindingContainer
internal class CampusSessionsProvider {
    @Provides
    @SingleIn(AppScope::class)
    fun providesCampusSessions(
        settings: AppSettingsRepository,
        guardStore: PortalGuardStore,
        parser: HtmlParser,
    ): CampusSessions = DefaultCampusSessions(
        settings = settings,
        portalState = PortalState(guardStore),
        transport = KtorSessionTransport(createDefaultSessionHttpClient(), createPlatformCookieJar()),
        parser = parser,
    )
}

/**
 * One campus site's session: its lock, status, and page loads. Each site recovers on its own; Portal's status and
 * block in [PortalState] are shared by all of them. The status is written only while holding the lock, except for the
 * WebView's loading signal.
 */
private class SiteSession(
    val site: CampusSite,
    private val settings: AppSettingsRepository,
    private val portalState: PortalState,
    private val transport: SessionTransport,
    private val parser: HtmlParser,
    private val now: () -> Instant,
) {
    private val lock = Mutex()
    val status = MutableStateFlow<SiteStatus>(SiteStatus.Unknown)

    suspend fun <R, T> fetch(request: SiteRequest<R>, read: (R) -> T): SiteResult<T> = lock.withLock {
        when (val loaded = load(request)) {
            is Loaded.Page -> request.reply(loaded.response)?.let { SiteResult.Page(read(it)) }
                ?: SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE)

            is Loaded.Stopped -> loaded.result
        }
    }

    suspend fun <T> fetchThen(path: SitePath, then: suspend SiteFollowUp.(String) -> T?): SiteResult<T> = lock.withLock {
        when (val loaded = load(SiteRequest.Get(site.url(path)))) {
            is Loaded.Stopped -> loaded.result

            is Loaded.Page -> {
                val followUp = FollowUp()
                try {
                    followUp.then(loaded.response.html)?.let { SiteResult.Page(it) } ?: SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE)
                } catch (stopped: FollowUpStopped) {
                    stopped.result
                } finally {
                    followUp.open = false
                }
            }
        }
    }

    suspend fun refresh() {
        lock.withLock {
            portalState.load()
            val previous = status.value
            status.value = SiteStatus.Checking
            try {
                status.value = when (val result = transport.get(site.url(site.probePath))) {
                    is TransportResult.Failed -> SiteStatus.Unavailable(result.reason)

                    is TransportResult.Response -> when (site.signals.classify(result.response, parser)) {
                        SiteVerdict.SIGNED_IN -> SiteStatus.SignedIn
                        SiteVerdict.SIGNED_OUT -> SiteStatus.SignedOut(renewal())
                        SiteVerdict.UNRECOGNIZED -> SiteStatus.Unavailable(Unavailability.UNEXPECTED_RESPONSE)
                    }
                }
            } catch (cancellation: CancellationException) {
                status.value = previous
                throw cancellation
            }
        }
    }

    /** The WebView started loading a page on this site. */
    fun markChecking() {
        status.value = SiteStatus.Checking
    }

    fun browserEntry(url: String): String = if (status.value == SiteStatus.SignedIn) url else site.portalEntryUrl

    /** Removes this site's cookies. [comeback] says what getting back in costs; null asks Portal's current state. */
    suspend fun signOut(comeback: Renewal? = null) {
        lock.withLock {
            transport.clearCookies(site.baseUrl)
            status.value = SiteStatus.SignedOut(comeback ?: renewal())
        }
    }

    private suspend fun load(request: SiteRequest<*>): Loaded {
        portalState.load()
        var attempt = Attempt(portalState.automaticSignIn(mode(), now()))
        var step: Step = Step.FetchPage
        var response: SiteResponse? = null
        while (step !is Step.Finish) {
            val observation = when (step) {
                Step.FetchPage -> observePage(transport, parser, site, request).also { response = it.response }.observation

                Step.Handoff -> observeHandoff(transport, portalState, site)

                // Secure-mode Portal sign-in arrives with the credential vault; until then it needs the user.
                Step.SignInToPortal -> Observation.PortalSignInStopped(PortalNeed.NO_CREDENTIALS)

                is Step.Finish -> error("finished steps exit the loop")
            }
            val (nextAttempt, nextStep) = next(attempt, observation)
            attempt = nextAttempt
            step = nextStep
        }
        return when (val outcome = step.outcome) {
            FetchOutcome.Page -> Loaded.Page(requireNotNull(response)).also { status.value = SiteStatus.SignedIn }

            is FetchOutcome.NeedsPortal -> Loaded.Stopped(SiteResult.NeedsPortal(outcome.need)).also {
                status.value = SiteStatus.SignedOut(renewal())
            }

            is FetchOutcome.Unavailable -> Loaded.Stopped(SiteResult.Unavailable(outcome.reason)).also {
                status.value = SiteStatus.Unavailable(outcome.reason)
            }
        }
    }

    private inner class FollowUp : SiteFollowUp {
        var open = true

        override suspend fun <R> send(request: SiteRequest<R>): R {
            check(open) { "A follow-up ends with its fetchThen call" }
            return when (val reply = sendOnce(request)) {
                is SiteResult.Page -> reply.value
                is SiteResult.NeedsPortal -> throw FollowUpStopped(reply)
                is SiteResult.Unavailable -> throw FollowUpStopped(reply)
            }
        }
    }

    private suspend fun <R> sendOnce(request: SiteRequest<R>): SiteResult<R> {
        val onSite = onSite(request) ?: return SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE)
        return when (val result = transport.send(onSite)) {
            is TransportResult.Failed -> SiteResult.Unavailable(result.reason)

            is TransportResult.Response -> when {
                site.signals.classify(result.response, parser) == SiteVerdict.SIGNED_OUT -> {
                    status.value = SiteStatus.SignedOut(renewal())
                    SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE)
                }

                result.response.status == HTTP_STATUS_OK -> onSite.reply(result.response)?.let { SiteResult.Page(it) }
                    ?: SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE)

                else -> SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE)
            }
        }
    }

    private fun <R> onSite(request: SiteRequest<R>): SiteRequest<R>? {
        val link = request.link
        val absolute = if (link.startsWith("/") && !link.startsWith("//")) site.baseUrl + link else link
        val url = parseHttpsUrl(absolute)?.takeIf { it.host.equals(site.host, ignoreCase = true) } ?: return null
        return request.withLink(url.toString())
    }

    private suspend fun renewal(): Renewal = portalState.renewal(mode(), now())

    private suspend fun mode(): PortalAuthenticationMode = settings.settings.first().portalAuthenticationMode
}

/**
 * State ownership: cookies live in the [SessionTransport]'s jar, each site's lock and status in its [SiteSession], and
 * Portal's status and block in [PortalState].
 */
internal class DefaultCampusSessions(
    settings: AppSettingsRepository,
    private val portalState: PortalState,
    private val transport: SessionTransport,
    parser: HtmlParser,
    private val sites: List<CampusSite> = CampusSites.all,
    now: () -> Instant = { Clock.System.now() },
) : CampusSessions {
    private val sessions = sites.associate { site ->
        site.id to SiteSession(site, settings, portalState, transport, parser, now)
    }

    override val portal: StateFlow<PortalStatus> get() = portalState.status

    override fun status(site: CampusSite): StateFlow<SiteStatus> = site.session.status

    override suspend fun refreshPortal() {
        portalState.load()
        when (val check = transport.checkPortal()) {
            // A live session after a block means the user signed in again in the WebView.
            PortalCheck.Live -> portalState.lift(live = true)

            PortalCheck.SignedOut -> portalState.markSignedOut()

            PortalCheck.Displaced -> portalState.block(PortalBlock.DISPLACED)

            is PortalCheck.Failed -> portalState.markUnavailable(check.reason)
        }
    }

    override suspend fun <T> fetch(site: CampusSite, path: SitePath, parse: (String) -> T): SiteResult<T> = site.session.fetch(SiteRequest.Get(site.url(path)), parse)

    override suspend fun fetchFile(site: CampusSite, path: SitePath, onProgress: TransferProgress?): SiteResult<SiteFile> = site.session.fetch(SiteRequest.Download(site.url(path), onProgress = onProgress)) { it }

    override suspend fun <T> fetchThen(site: CampusSite, path: SitePath, then: suspend SiteFollowUp.(String) -> T?): SiteResult<T> = site.session.fetchThen(path, then)

    override suspend fun refresh(site: CampusSite) = site.session.refresh()

    override fun browserEntry(url: String): String? {
        val host = parseHttpsUrl(url)?.host ?: return null
        val site = sites.firstOrNull { it.host.equals(host, ignoreCase = true) } ?: return null
        return site.session.browserEntry(url)
    }

    override suspend fun browserPage(url: String, loading: Boolean) {
        val site = CampusSites.forUrl(url)?.takeIf { it in sites }
        val portalUrl = parseHttpsUrl(url)?.takeIf(::isPortalUrl)
        when {
            site != null && loading -> site.session.markChecking()

            site != null -> refresh(site)

            portalUrl == null || loading -> Unit

            else -> when (portalPage(portalUrl)) {
                PortalPage.EXPIRED -> portalState.block(PortalBlock.DISPLACED)

                PortalPage.SIGN_IN -> portalState.markSignedOut()

                // A signed-in Portal page means the user signed in in the WebView: their action lifts any block.
                PortalPage.OTHER -> portalState.lift(live = true)
            }
        }
    }

    override suspend fun signInToPortal(): PortalSignIn {
        portalState.lift(live = portalState.status.value == PortalStatus.Live)
        return PortalSignIn.OpenBrowser("$PORTAL_BASE_URL/login")
    }

    override suspend fun signOut(site: CampusSite) = site.session.signOut()

    override suspend fun signOutEverywhere() {
        // Block first: if the app stops partway, it still cannot sign back in by itself.
        portalState.block(PortalBlock.USER_SIGNED_OUT)
        transport.clearCookies(PORTAL_BASE_URL, keep = setOf(PORTAL_BROWSER_COOKIE))
        sites.forEach { site -> site.session.signOut(Renewal.PORTAL_SIGN_IN_BY_USER) }
    }

    private val CampusSite.session: SiteSession get() = requireNotNull(sessions[id]) { "Unknown campus site" }
}

// Not an Exception, so a catch-all in the caller's block does not swallow the stop.
private class FollowUpStopped(val result: SiteResult<Nothing>) : Throwable()

private sealed interface Loaded {
    class Page(val response: SiteResponse) : Loaded

    class Stopped(val result: SiteResult<Nothing>) : Loaded
}

private class PageObservation(val observation: Observation, val response: SiteResponse? = null)

private suspend fun observePage(transport: SessionTransport, parser: HtmlParser, site: CampusSite, request: SiteRequest<*>): PageObservation = when (val result = transport.send(request)) {
    is TransportResult.Failed -> PageObservation(Observation.Failed(result.reason))

    is TransportResult.Response -> when {
        site.signals.classify(result.response, parser) == SiteVerdict.SIGNED_OUT -> PageObservation(Observation.SiteSignedOut)
        result.response.status == HTTP_STATUS_OK -> PageObservation(Observation.PageOk, result.response)
        else -> PageObservation(Observation.Failed(Unavailability.UNEXPECTED_RESPONSE))
    }
}

/** Runs [site]'s handoff and records what it showed about Portal. */
private suspend fun observeHandoff(transport: SessionTransport, portal: PortalState, site: CampusSite): Observation = when (val result = transport.handoff(site)) {
    HandoffResult.Done -> Observation.HandoffDone.also { portal.markLive() }
    HandoffResult.PortalSignedOut -> Observation.PortalSignedOut.also { portal.markSignedOut() }
    HandoffResult.PortalDisplaced -> Observation.PortalDisplaced.also { portal.block(PortalBlock.DISPLACED) }
    is HandoffResult.Failed -> Observation.Failed(result.reason)
}
