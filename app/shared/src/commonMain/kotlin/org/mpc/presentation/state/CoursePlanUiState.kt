package org.mpc.presentation.state

import org.mpc.domain.model.CoursePlan

sealed interface CoursePlanUiState {
    data object Loading : CoursePlanUiState

    data class Success(
        val plan: CoursePlan,
    ) : CoursePlanUiState

    data class Failure(
        val cause: Throwable,
    ) : CoursePlanUiState
}
