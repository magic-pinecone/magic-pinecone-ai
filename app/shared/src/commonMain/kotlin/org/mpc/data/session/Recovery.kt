package org.mpc.data.session

import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.session.PortalNeed
import org.mpc.domain.session.Unavailability
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal val AUTOMATIC_PORTAL_SIGN_IN_COOLDOWN: Duration = 30.minutes

/** A block on automatic Portal sign-in that only a user tap clears. Persisted so a restart cannot lift it. */
internal enum class PortalBlock(val need: PortalNeed) {
    DISPLACED(PortalNeed.DISPLACED),
    USER_SIGNED_OUT(PortalNeed.USER_SIGNED_OUT),
    NEEDS_PORTAL_PAGE(PortalNeed.NEEDS_PORTAL_PAGE),
}

/** Persisted facts that limit automatic Portal sign-in. Holds no secret. */
internal data class PortalGuard(
    val block: PortalBlock? = null,
    val lastAutomaticSignInAt: Instant? = null,
)

internal sealed interface AutomaticSignIn {
    data object Allowed : AutomaticSignIn

    data class Refused(val need: PortalNeed) : AutomaticSignIn
}

/**
 * Whether the app may sign in to Portal without a tap. MANUAL_PORTAL never may; SECURE_CREDENTIALS may unless a
 * block is set or the last automatic sign-in was within [cooldown].
 */
internal fun automaticSignIn(
    mode: PortalAuthenticationMode,
    guard: PortalGuard,
    now: Instant,
    cooldown: Duration = AUTOMATIC_PORTAL_SIGN_IN_COOLDOWN,
): AutomaticSignIn {
    val last = guard.lastAutomaticSignInAt
    return when {
        mode == PortalAuthenticationMode.MANUAL_PORTAL -> AutomaticSignIn.Refused(PortalNeed.MANUAL_SIGN_IN)
        guard.block != null -> AutomaticSignIn.Refused(guard.block.need)
        last != null && now - last < cooldown -> AutomaticSignIn.Refused(PortalNeed.COOLDOWN)
        else -> AutomaticSignIn.Allowed
    }
}

/** What one step of a site fetch observed. */
internal sealed interface Observation {
    data object PageOk : Observation

    data object SiteSignedOut : Observation

    data object HandoffDone : Observation

    /** The handoff landed on Portal's sign-in page. */
    data object PortalSignedOut : Observation

    /** The handoff landed on Portal `/expired`. The caller persists [PortalBlock.DISPLACED]. */
    data object PortalDisplaced : Observation

    data object PortalSignInDone : Observation

    data class PortalSignInStopped(val need: PortalNeed) : Observation

    data class Failed(val reason: Unavailability) : Observation
}

internal sealed interface FetchOutcome {
    data object Page : FetchOutcome

    data class NeedsPortal(val need: PortalNeed) : FetchOutcome

    data class Unavailable(val reason: Unavailability) : FetchOutcome
}

internal sealed interface Step {
    data object FetchPage : Step

    data object Handoff : Step

    data object SignInToPortal : Step

    data class Finish(val outcome: FetchOutcome) : Step
}

/**
 * Budget and permissions for one fetch. Each Portal sign-in buys one more handoff, so a fetch makes at most two
 * handoffs and at most one Portal sign-in.
 */
internal data class Attempt(
    val automaticSignIn: AutomaticSignIn,
    val handoffs: Int = 0,
    val portalSignIns: Int = 0,
)

private const val MAX_AUTOMATIC_PORTAL_SIGN_INS = 1

/** The whole recovery policy: given what a step observed, choose the next step. Every path finishes. */
internal fun next(attempt: Attempt, observed: Observation): Pair<Attempt, Step> = when (observed) {
    Observation.PageOk -> attempt to Step.Finish(FetchOutcome.Page)

    Observation.SiteSignedOut, Observation.PortalSignInDone -> handoffOrGiveUp(attempt)

    Observation.HandoffDone -> attempt to Step.FetchPage

    Observation.PortalSignedOut -> when (val permission = attempt.automaticSignIn) {
        is AutomaticSignIn.Refused -> attempt to Step.Finish(FetchOutcome.NeedsPortal(permission.need))

        AutomaticSignIn.Allowed ->
            if (attempt.portalSignIns < MAX_AUTOMATIC_PORTAL_SIGN_INS) {
                attempt.copy(portalSignIns = attempt.portalSignIns + 1) to Step.SignInToPortal
            } else {
                attempt to Step.Finish(FetchOutcome.Unavailable(Unavailability.UNEXPECTED_RESPONSE))
            }
    }

    Observation.PortalDisplaced -> attempt to Step.Finish(FetchOutcome.NeedsPortal(PortalNeed.DISPLACED))

    is Observation.PortalSignInStopped -> attempt to Step.Finish(FetchOutcome.NeedsPortal(observed.need))

    is Observation.Failed -> attempt to Step.Finish(FetchOutcome.Unavailable(observed.reason))
}

private fun handoffOrGiveUp(attempt: Attempt): Pair<Attempt, Step> = if (attempt.handoffs < 1 + attempt.portalSignIns) {
    attempt.copy(handoffs = attempt.handoffs + 1) to Step.Handoff
} else {
    // A handoff already ran and the site still reads signed out: the handoff did not stick.
    attempt to Step.Finish(FetchOutcome.Unavailable(Unavailability.UNEXPECTED_RESPONSE))
}
