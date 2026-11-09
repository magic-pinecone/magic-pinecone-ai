package org.mpc.presentation.state

data class BackendEndpointUiState(
    /** The backend courses are read from, or null while the static catalog is in use. */
    val baseUrl: String? = null,
    val sync: BackendSyncState = BackendSyncState.Idle,
)

sealed interface BackendSyncState {
    data object Idle : BackendSyncState

    data object Syncing : BackendSyncState

    data class Synced(
        val courseCount: Int,
    ) : BackendSyncState

    data class Failed(
        val error: BackendSyncError,
    ) : BackendSyncState
}

enum class BackendSyncError {
    INVALID_ADDRESS,

    /** The source could not deliver the catalog; the previous source is still in use. */
    SOURCE_UNAVAILABLE,
}
