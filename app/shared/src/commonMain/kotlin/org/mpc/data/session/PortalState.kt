package org.mpc.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.session.PortalStatus
import org.mpc.domain.session.Renewal
import org.mpc.domain.session.Unavailability
import kotlin.time.Instant

/**
 * Owns what the app knows about Portal: the live status, and the persisted block on automatic sign-in. Every write
 * goes through [lock], so a block and the status shown for it never disagree.
 */
internal class PortalState(private val guardStore: PortalGuardStore) {
    private val lock = Mutex()
    private val _status = MutableStateFlow<PortalStatus>(PortalStatus.Unknown)

    val status: StateFlow<PortalStatus> = _status

    /** Shows a persisted block after a restart, before anything in this process has observed Portal. */
    suspend fun load() {
        if (_status.value != PortalStatus.Unknown) return
        val block = guardStore.read().block ?: return
        lock.withLock {
            if (_status.value == PortalStatus.Unknown) _status.value = PortalStatus.Blocked(block.need)
        }
    }

    suspend fun block(block: PortalBlock) = lock.withLock {
        guardStore.write(guardStore.read().copy(block = block))
        _status.value = PortalStatus.Blocked(block.need)
    }

    /** The user signed in, or tapped to: lift any block. [live] is true when Portal is known to be signed in. */
    suspend fun lift(live: Boolean) = lock.withLock {
        guardStore.write(guardStore.read().copy(block = null))
        _status.value = if (live) PortalStatus.Live else PortalStatus.Unknown
    }

    suspend fun markLive() = lock.withLock { _status.value = PortalStatus.Live }

    /** Portal showed its sign-in page. A block stays visible, because it explains why the app will not sign in. */
    suspend fun markSignedOut() = lock.withLock {
        if (_status.value !is PortalStatus.Blocked) _status.value = PortalStatus.SignedOut
    }

    /** Portal could not be reached. A block stays visible for the same reason as in [markSignedOut]. */
    suspend fun markUnavailable(reason: Unavailability) = lock.withLock {
        if (_status.value !is PortalStatus.Blocked) _status.value = PortalStatus.Unavailable(reason)
    }

    suspend fun automaticSignIn(mode: PortalAuthenticationMode, now: Instant): AutomaticSignIn = automaticSignIn(mode, guardStore.read(), now)

    suspend fun renewal(mode: PortalAuthenticationMode, now: Instant): Renewal = when {
        _status.value == PortalStatus.Live -> Renewal.HANDOFF
        automaticSignIn(mode, now) == AutomaticSignIn.Allowed -> Renewal.PORTAL_SIGN_IN_UNATTENDED
        else -> Renewal.PORTAL_SIGN_IN_BY_USER
    }
}
