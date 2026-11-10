package org.mpc.presentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.mpc.domain.repository.SourcedRulesRepository
import org.mpc.presentation.state.SourcedRulesUiState

/** Loads one set of [org.mpc.domain.model.SourcedRules]. A screen for a rule set subclasses it with its own repository. */
open class SourcedRulesViewModel(
    private val repository: SourcedRulesRepository,
) : ViewModel() {
    private val state = MutableStateFlow<SourcedRulesUiState>(SourcedRulesUiState.Loading)
    val uiState: StateFlow<SourcedRulesUiState> = state.asStateFlow()

    private var loading: Job? = null

    init {
        // Loads at start and again whenever the backend address changes in Settings, so a screen
        // that said "needs a backend" picks up the new one without a restart.
        viewModelScope.launch {
            repository.backend.distinctUntilChanged().collect { refresh() }
        }
    }

    fun refresh() {
        loading?.cancel()
        state.value = SourcedRulesUiState.Loading
        loading = viewModelScope.launch {
            state.value = runCatching { repository.fetchRules() }
                .fold(
                    onSuccess = { rules -> rules?.let(SourcedRulesUiState::Loaded) ?: SourcedRulesUiState.NeedsBackend },
                    onFailure = { cause ->
                        cause.rethrowIfCancellationOrFatal()
                        Logger.i(cause.toString())
                        SourcedRulesUiState.Failed
                    },
                )
        }
    }
}
