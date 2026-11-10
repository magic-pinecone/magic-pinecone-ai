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
import kotlinx.coroutines.flow.combine
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

    /** A language tag for the guides, or null for the device language. */
    private val language = MutableStateFlow<String?>(null)

    init {
        // Reloads whenever the backend address or the language changes in Settings.
        viewModelScope.launch {
            combine(repository.backend.distinctUntilChanged(), language) { _, _ -> }.collect { refresh() }
        }
    }

    /** Shows the guides in [language], a language tag, or the device language when null. */
    fun setLanguage(language: String?) {
        this.language.value = language
    }

    fun refresh() {
        loading?.cancel()
        state.value = GovGuidesUiState.Loading
        val language = language.value
        // Cancelling the previous load keeps a slower answer in the old language from replacing this one.
        loading = viewModelScope.launch {
            state.value = runCatching { repository.fetchGuides(language) }
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
