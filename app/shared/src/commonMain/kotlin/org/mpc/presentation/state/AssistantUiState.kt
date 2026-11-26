package org.mpc.presentation.state

import org.mpc.domain.model.AssistantAnswer

sealed interface AssistantUiState {
    /** Nothing has been asked yet. */
    data object Idle : AssistantUiState

    data object Asking : AssistantUiState

    /** No backend address is set, and only a backend can answer. */
    data object NeedsBackend : AssistantUiState

    data object Failed : AssistantUiState

    data class Answered(val answer: AssistantAnswer) : AssistantUiState
}
