package org.mpc.presentation.state

import dev.icerock.moko.resources.desc.PluralFormatted
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.StringDesc
import org.mpc.shared.MR
import org.mpc.shared.settings_invalid_address
import org.mpc.shared.settings_source_unavailable
import org.mpc.shared.settings_synced
import org.mpc.shared.settings_syncing

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

fun BackendSyncState.message(): StringDesc? = when (this) {
    BackendSyncState.Idle -> null

    BackendSyncState.Syncing -> StringDesc.Resource(MR.strings.settings_syncing)

    is BackendSyncState.Synced -> StringDesc.PluralFormatted(
        MR.plurals.settings_synced,
        courseCount,
        courseCount,
    )

    is BackendSyncState.Failed -> when (error) {
        BackendSyncError.INVALID_ADDRESS -> StringDesc.Resource(MR.strings.settings_invalid_address)
        BackendSyncError.SOURCE_UNAVAILABLE -> StringDesc.Resource(MR.strings.settings_source_unavailable)
    }
}
