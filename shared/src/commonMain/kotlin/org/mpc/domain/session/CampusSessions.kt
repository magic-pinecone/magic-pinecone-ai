package org.mpc.domain.session

import kotlinx.coroutines.flow.StateFlow

/** Session status of one campus site. Derived from the last check or fetch and never persisted. */
sealed interface SiteStatus {
    data object Unknown : SiteStatus

    data object Checking : SiteStatus

    data object SignedIn : SiteStatus

    data class SignedOut(val renewal: Renewal) : SiteStatus

    data class Unavailable(val reason: Unavailability) : SiteStatus
}

/** What getting a signed-out site back costs, so the UI can label its action honestly. */
enum class Renewal {
    /** Portal is signed in; the app can hand off again without signing anyone out. */
    HANDOFF,

    /** The app may sign in to Portal by itself, which ends the user's Portal session in their browser. */
    PORTAL_SIGN_IN_UNATTENDED,

    /** The user signs in to Portal, which ends their Portal session elsewhere. */
    PORTAL_SIGN_IN_BY_USER,
}

/** Portal status. Portal allows one active sign-in per account. */
sealed interface PortalStatus {
    data object Unknown : PortalStatus

    data object Live : PortalStatus

    data object SignedOut : PortalStatus

    /** The app will not sign in to Portal by itself until the user does; see [PortalNeed]. */
    data class Blocked(val need: PortalNeed) : PortalStatus

    data class Unavailable(val reason: Unavailability) : PortalStatus
}

sealed interface SiteResult<out T> {
    data class Page<T>(val value: T) : SiteResult<T>

    /** The session is gone and getting it back needs the user to sign in to Portal. */
    data class NeedsPortal(val need: PortalNeed) : SiteResult<Nothing>

    data class Unavailable(val reason: Unavailability) : SiteResult<Nothing>
}

sealed interface PortalSignIn {
    /** Load this URL in the in-app WebView; the WebView reports the outcome through [CampusSessions.browserPage]. */
    data class OpenBrowser(val url: String) : PortalSignIn
}

/**
 * The one way the app keeps sessions with Portal and campus sites. It hides the shared cookie jar, Portal handoffs,
 * the sign-in mode, the limits on automatic Portal sign-in, and per-site serialization.
 *
 * Invariants:
 * - Cookies live only in the platform WebView store, so the WebView and native requests share them.
 * - A fetch makes at most two handoffs and at most one automatic Portal sign-in.
 * - While [portal] is [PortalStatus.Blocked], the app never signs in to Portal by itself.
 */
interface CampusSessions {
    val portal: StateFlow<PortalStatus>

    fun status(site: CampusSite): StateFlow<SiteStatus>

    /** Checks Portal's session without signing in. */
    suspend fun refreshPortal()

    /**
     * Loads [path] on [site] and parses its HTML. A signed-out site is handed off again from a live Portal session
     * first; the result is [SiteResult.NeedsPortal] only when the next step needs the user.
     */
    suspend fun <T> fetch(site: CampusSite, path: SitePath, parse: (String) -> T): SiteResult<T>

    /**
     * Loads [path] like [fetch], then loads the link [pick] chooses from its HTML and parses that page. The link must
     * be https on [site]. It may carry a one-time token, so it is used once inside this call and never returned.
     */
    suspend fun <T> fetchFollowing(
        site: CampusSite,
        path: SitePath,
        pick: (String) -> String?,
        parse: (String) -> T,
    ): SiteResult<T>

    /** Checks [site]'s session without recovering it. */
    suspend fun refresh(site: CampusSite)

    /** The URL the WebView loads for [url]: the page itself when its site is signed in, otherwise the Portal handoff. Null when no managed site serves it. */
    fun browserEntry(url: String): String?

    /** The WebView reports each page as it starts and finishes loading. Other hosts are ignored. */
    suspend fun browserPage(url: String, loading: Boolean)

    /** A user tap: lifts any block on Portal sign-in and returns where to sign in. */
    suspend fun signInToPortal(): PortalSignIn

    /** Removes [site]'s cookies. Portal stays signed in. */
    suspend fun signOut(site: CampusSite)

    /**
     * Blocks automatic Portal sign-in, then removes Portal's session cookies and every site's cookies. Keeps saved
     * credentials and Portal's long-lived browser cookie, and does not call Portal's server logout.
     */
    suspend fun signOutEverywhere()
}

/** The URL the WebView loads for [path] on [site], as [CampusSessions.browserEntry] describes. */
fun CampusSessions.browserEntry(site: CampusSite, path: SitePath): String = requireNotNull(browserEntry(site.url(path))) { "${site.id} is not a managed site" }
