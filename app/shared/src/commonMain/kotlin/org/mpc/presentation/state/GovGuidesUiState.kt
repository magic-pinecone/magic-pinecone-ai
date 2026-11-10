package org.mpc.presentation.state

import org.mpc.domain.model.GovGuides

sealed interface GovGuidesUiState {
    data object Loading : GovGuidesUiState

    /** No backend address is set, and the guides are only served by a backend. */
    data object NeedsBackend : GovGuidesUiState

    data object Failed : GovGuidesUiState

    data class Loaded(val guides: GovGuides) : GovGuidesUiState
}
