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
import org.mpc.domain.session.SitePath
import org.mpc.domain.session.SiteResult
import org.mpc.domain.session.SiteStatus
import org.mpc.domain.session.SiteVerdict
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

    suspend fun <T> fetch(path: SitePath, parse: (String) -> T): SiteResult<T> = lock.withLock {
        when (val loaded = load(path)) {
            is Loaded.Page -> SiteResult.Page(parse(loaded.html))
            is Loaded.Stopped -> loaded.result
        }
    }

    suspend fun <T> fetchFollowing(path: SitePath, pick: (String) -> String?, parse: (String) -> T): SiteResult<T> = lock.withLock {
        when (val loaded = load(path)) {
            is Loaded.Stopped -> loaded.result

            is Loaded.Page -> when (val url = pick(loaded.html)?.let { followableUrl(site, it) }) {
                null -> SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE)
                else -> follow(url, parse)
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

    /** Loads [path], recovering the session as [CampusSessions.fetch] describes, and records the status. Holds the lock. */
    private suspend fun load(path: SitePath): Loaded {
        portalState.load()
        var attempt = Attempt(portalState.automaticSignIn(mode(), now()))
        var step: Step = Step.FetchPage
        var html = ""
        while (step !is Step.Finish) {
            val observation = when (step) {
                Step.FetchPage -> observePage(transport, parser, site, path).also { html = it.html }.observation

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
            FetchOutcome.Page -> Loaded.Page(html).also { status.value = SiteStatus.SignedIn }

            is FetchOutcome.NeedsPortal -> Loaded.Stopped(SiteResult.NeedsPortal(outcome.need)).also {
                status.value = SiteStatus.SignedOut(renewal())
            }

            is FetchOutcome.Unavailable -> Loaded.Stopped(SiteResult.Unavailable(outcome.reason)).also {
                status.value = SiteStatus.Unavailable(outcome.reason)
            }
        }
    }

    /** Loads a link picked from a page just loaded. It is not recovered: a signed-out answer makes the next fetch hand off. */
    private suspend fun <T> follow(url: String, parse: (String) -> T): SiteResult<T> = when (val result = transport.get(url)) {
        is TransportResult.Failed -> SiteResult.Unavailable(result.reason)

        is TransportResult.Response -> when {
            site.signals.classify(result.response, parser) == SiteVerdict.SIGNED_OUT -> {
                status.value = SiteStatus.SignedOut(renewal())
                SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE)
            }

            result.response.status == HTTP_STATUS_OK -> SiteResult.Page(parse(result.response.html))

            else -> SiteResult.Unavailable(Unavailability.UNEXPECTED_RESPONSE)
        }
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

    override fun status(site: CampusSite): StateFlow<SiteStatus> = session(site).status

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

    override suspend fun <T> fetch(site: CampusSite, path: SitePath, parse: (String) -> T): SiteResult<T> = session(site).fetch(path, parse)

    override suspend fun <T> fetchFollowing(
        site: CampusSite,
        path: SitePath,
        pick: (String) -> String?,
        parse: (String) -> T,
    ): SiteResult<T> = session(site).fetchFollowing(path, pick, parse)

    override suspend fun refresh(site: CampusSite) = session(site).refresh()

    override fun browserEntry(url: String): String? {
        val host = parseHttpsUrl(url)?.host ?: return null
        val site = sites.firstOrNull { it.host.equals(host, ignoreCase = true) } ?: return null
        return session(site).browserEntry(url)
    }

    override suspend fun browserPage(url: String, loading: Boolean) {
        val site = CampusSites.forUrl(url)?.takeIf { it in sites }
        val portalUrl = parseHttpsUrl(url)?.takeIf(::isPortalUrl)
        when {
            site != null && loading -> session(site).markChecking()

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

    override suspend fun signOut(site: CampusSite) = session(site).signOut()

    override suspend fun signOutEverywhere() {
        // Block first: if the app stops partway, it still cannot sign back in by itself.
        portalState.block(PortalBlock.USER_SIGNED_OUT)
        transport.clearCookies(PORTAL_BASE_URL, keep = setOf(PORTAL_BROWSER_COOKIE))
        sites.forEach { site -> session(site).signOut(Renewal.PORTAL_SIGN_IN_BY_USER) }
    }

    private fun session(site: CampusSite): SiteSession = requireNotNull(sessions[site.id]) { "Unknown campus site" }
}

private sealed interface Loaded {
    class Page(val html: String) : Loaded

    class Stopped(val result: SiteResult<Nothing>) : Loaded
}

/** [link] as an absolute URL when it is https on [site]'s host. A link picked from a page goes nowhere else. */
private fun followableUrl(site: CampusSite, link: String): String? {
    val absolute = if (link.startsWith("/") && !link.startsWith("//")) site.baseUrl + link else link
    val url = parseHttpsUrl(absolute) ?: return null
    return url.toString().takeIf { url.host.equals(site.host, ignoreCase = true) }
}

private class PageObservation(val observation: Observation, val html: String = "")

private suspend fun observePage(transport: SessionTransport, parser: HtmlParser, site: CampusSite, path: SitePath): PageObservation = when (val result = transport.get(site.url(path))) {
    is TransportResult.Failed -> PageObservation(Observation.Failed(result.reason))

    is TransportResult.Response -> when {
        site.signals.classify(result.response, parser) == SiteVerdict.SIGNED_OUT -> PageObservation(Observation.SiteSignedOut)
        result.response.status == HTTP_STATUS_OK -> PageObservation(Observation.PageOk, result.response.html)
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
