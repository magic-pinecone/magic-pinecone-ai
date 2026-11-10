package org.mpc.presentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.mpc.domain.repository.GovGuidesRepository
import org.mpc.presentation.state.GovGuidesUiState

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class GovGuidesViewModel(
    private val repository: GovGuidesRepository,
) : ViewModel() {
    private val state = MutableStateFlow<GovGuidesUiState>(GovGuidesUiState.Loading)
    val uiState: StateFlow<GovGuidesUiState> = state.asStateFlow()

    private var loading: Job? = null

    init {
        // Reloads whenever the backend address is set, changed or cleared in Settings.
        viewModelScope.launch {
            repository.backend.distinctUntilChanged().collect { refresh() }
        }
    }

    fun refresh() {
        loading?.cancel()
        state.value = GovGuidesUiState.Loading
        loading = viewModelScope.launch {
            state.value = runCatching { repository.fetchGuides() }
                .fold(
                    onSuccess = { guides -> guides?.let(GovGuidesUiState::Loaded) ?: GovGuidesUiState.NeedsBackend },
                    onFailure = { cause ->
                        cause.rethrowIfCancellationOrFatal()
                        Logger.i(cause.toString())
                        GovGuidesUiState.Failed
                    },
                )
        }
    }
}
