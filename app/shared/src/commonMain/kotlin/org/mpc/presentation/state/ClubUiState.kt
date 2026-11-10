package org.mpc.presentation.state

import org.mpc.domain.model.Club

sealed interface ClubUiState {
    data object Loading : ClubUiState

    /** Clubs are only served by a backend; the static catalog has none. */
    data object NoBackend : ClubUiState

    data class Loaded(
        /** Category name to its clubs, in the club site's order. */
        val sections: List<Pair<String, List<Club>>>,
    ) : ClubUiState

    data object Failed : ClubUiState
}
