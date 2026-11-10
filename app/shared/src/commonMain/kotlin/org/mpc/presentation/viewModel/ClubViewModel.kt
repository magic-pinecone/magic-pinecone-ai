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
import kotlinx.coroutines.launch
import org.mpc.domain.repository.ClubRepository
import org.mpc.presentation.state.ClubUiState

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class ClubViewModel(
    private val clubRepository: ClubRepository,
) : ViewModel() {
    private val state = MutableStateFlow<ClubUiState>(ClubUiState.Loading)
    val uiState: StateFlow<ClubUiState> = state.asStateFlow()

    private var language: String? = null
    private var loadJob: Job? = null

    init {
        load()
    }

    /** Shows the clubs in [language], a language tag or null for the device language. */
    fun setLanguage(language: String?) {
        if (language == this.language) return
        this.language = language
        load()
    }

    fun load() {
        state.value = ClubUiState.Loading
        val language = language
        // A slower response for the previous language must not overwrite this one.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            state.value = runCatching { clubRepository.fetchClubs(language) }
                .fold(
                    onSuccess = { clubs ->
                        if (clubs == null) {
                            ClubUiState.NoBackend
                        } else {
                            // groupBy keeps first-seen order, which is the backend's category order.
                            ClubUiState.Loaded(clubs.groupBy { it.categoryName }.toList())
                        }
                    },
                    onFailure = { cause ->
                        cause.rethrowIfCancellationOrFatal()
                        Logger.i(cause.toString())
                        ClubUiState.Failed
                    },
                )
        }
    }
}
