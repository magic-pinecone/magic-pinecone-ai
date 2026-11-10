package org.mpc.data.session

/** Keeps [PortalGuard] across launches, so a restart cannot lift a block on automatic Portal sign-in. */
internal interface PortalGuardStore {
    suspend fun read(): PortalGuard

    suspend fun write(guard: PortalGuard)
}
