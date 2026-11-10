package org.mpc.presentation.state

import org.mpc.domain.model.SourcedRules

sealed interface SourcedRulesUiState {
    data object Loading : SourcedRulesUiState

    /** No backend address is set, and these rules are only served by a backend. */
    data object NeedsBackend : SourcedRulesUiState

    data object Failed : SourcedRulesUiState

    data class Loaded(val rules: SourcedRules) : SourcedRulesUiState
}
