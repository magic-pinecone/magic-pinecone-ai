package org.mpc.data.session

import de.infix.testBalloon.framework.core.testSuite
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.session.PortalNeed
import org.mpc.domain.session.Unavailability
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Plays [observations] in order from a fresh fetch and returns every step the policy chose. */
private fun play(permission: AutomaticSignIn, vararg observations: Observation): List<Step> {
    var attempt = Attempt(permission)
    val steps = mutableListOf<Step>(Step.FetchPage)
    for (observation in observations) {
        check(steps.last() !is Step.Finish) { "observation after the fetch finished" }
        val (nextAttempt, step) = next(attempt, observation)
        attempt = nextAttempt
        steps += step
    }
    return steps
}

private val NOW = Instant.parse("2026-10-26T14:00:00Z")

val recoveryTests by testSuite {
    test("a signed-in page finishes without a handoff") {
        assertEquals(
            listOf(Step.FetchPage, Step.Finish(FetchOutcome.Page)),
            play(AutomaticSignIn.Allowed, Observation.PageOk),
        )
    }

    test("a signed-out site hands off from live Portal before any Portal sign-in") {
        assertEquals(
            listOf(Step.FetchPage, Step.Handoff, Step.FetchPage, Step.Finish(FetchOutcome.Page)),
            play(
                AutomaticSignIn.Allowed,
                Observation.SiteSignedOut,
                Observation.HandoffDone,
                Observation.PageOk,
            ),
        )
    }

    test("secure mode signs in to Portal once, then hands off again") {
        assertEquals(
            listOf(
                Step.FetchPage,
                Step.Handoff,
                Step.SignInToPortal,
                Step.Handoff,
                Step.FetchPage,
                Step.Finish(FetchOutcome.Page),
            ),
            play(
                AutomaticSignIn.Allowed,
                Observation.SiteSignedOut,
                Observation.PortalSignedOut,
                Observation.PortalSignInDone,
                Observation.HandoffDone,
                Observation.PageOk,
            ),
        )
    }

    test("a refused automatic sign-in stops at the user with its reason") {
        val steps = play(
            AutomaticSignIn.Refused(PortalNeed.MANUAL_SIGN_IN),
            Observation.SiteSignedOut,
            Observation.PortalSignedOut,
        )
        assertEquals(Step.Finish(FetchOutcome.NeedsPortal(PortalNeed.MANUAL_SIGN_IN)), steps.last())
    }

    test("a displaced Portal never triggers an automatic sign-in") {
        val steps = play(AutomaticSignIn.Allowed, Observation.SiteSignedOut, Observation.PortalDisplaced)
        assertEquals(Step.Finish(FetchOutcome.NeedsPortal(PortalNeed.DISPLACED)), steps.last())
    }

    test("a second Portal sign-out after signing in gives up instead of signing in again") {
        val steps = play(
            AutomaticSignIn.Allowed,
            Observation.SiteSignedOut,
            Observation.PortalSignedOut,
            Observation.PortalSignInDone,
            Observation.PortalSignedOut,
        )
        assertEquals(1, steps.count { it == Step.SignInToPortal })
        assertEquals(Step.Finish(FetchOutcome.Unavailable(Unavailability.UNEXPECTED_RESPONSE)), steps.last())
    }

    test("a handoff that does not stick gives up instead of looping") {
        val steps = play(
            AutomaticSignIn.Allowed,
            Observation.SiteSignedOut,
            Observation.HandoffDone,
            Observation.SiteSignedOut,
        )
        assertEquals(1, steps.count { it == Step.Handoff })
        assertEquals(Step.Finish(FetchOutcome.Unavailable(Unavailability.UNEXPECTED_RESPONSE)), steps.last())
    }

    test("a Portal page step and a failure finish with their reasons") {
        assertEquals(
            Step.Finish(FetchOutcome.NeedsPortal(PortalNeed.NEEDS_PORTAL_PAGE)),
            play(
                AutomaticSignIn.Allowed,
                Observation.SiteSignedOut,
                Observation.PortalSignedOut,
                Observation.PortalSignInStopped(PortalNeed.NEEDS_PORTAL_PAGE),
            ).last(),
        )
        assertEquals(
            Step.Finish(FetchOutcome.Unavailable(Unavailability.NETWORK)),
            play(AutomaticSignIn.Allowed, Observation.Failed(Unavailability.NETWORK)).last(),
        )
    }

    test("manual mode never signs in to Portal by itself") {
        assertEquals(
            AutomaticSignIn.Refused(PortalNeed.MANUAL_SIGN_IN),
            automaticSignIn(PortalAuthenticationMode.MANUAL_PORTAL, PortalGuard(), NOW),
        )
    }

    test("secure mode is refused while blocked and during the cooldown") {
        val secure = PortalAuthenticationMode.SECURE_CREDENTIALS
        assertEquals(AutomaticSignIn.Allowed, automaticSignIn(secure, PortalGuard(), NOW))
        assertEquals(
            AutomaticSignIn.Refused(PortalNeed.DISPLACED),
            automaticSignIn(secure, PortalGuard(block = PortalBlock.DISPLACED), NOW),
        )
        assertEquals(
            AutomaticSignIn.Refused(PortalNeed.COOLDOWN),
            automaticSignIn(secure, PortalGuard(lastAutomaticSignInAt = NOW - 5.minutes), NOW),
        )
        assertEquals(
            AutomaticSignIn.Allowed,
            automaticSignIn(secure, PortalGuard(lastAutomaticSignInAt = NOW - 31.minutes), NOW),
        )
    }
}
