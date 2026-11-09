package org.mpc.presentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mpc.domain.model.normalizeBackendBaseUrl
import org.mpc.domain.repository.BackendEndpointRepository
import org.mpc.domain.repository.CourseRepository
import org.mpc.presentation.state.BackendEndpointUiState
import org.mpc.presentation.state.BackendSyncError
import org.mpc.presentation.state.BackendSyncState

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class BackendEndpointViewModel(
    private val backendEndpoint: BackendEndpointRepository,
    private val courseRepository: CourseRepository,
) : ViewModel() {
    // TODO: update semester so it's not hard coded
    private val semester = "115-1"

    private val sync = MutableStateFlow<BackendSyncState>(BackendSyncState.Idle)

    val uiState: StateFlow<BackendEndpointUiState> = combine(backendEndpoint.baseUrl, sync) { baseUrl, syncState ->
        BackendEndpointUiState(baseUrl = baseUrl, sync = syncState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = BackendEndpointUiState(),
    )

    /**
     * Switches the course source to the backend at [address], or to the static catalog when it is
     * blank, and reloads the catalog from it. A source that cannot deliver the catalog is not kept.
     */
    fun useSource(address: String) {
        if (sync.value == BackendSyncState.Syncing) return

        val target = if (address.isBlank()) {
            null
        } else {
            normalizeBackendBaseUrl(address) ?: run {
                sync.value = BackendSyncState.Failed(BackendSyncError.INVALID_ADDRESS)
                return
            }
        }

        sync.value = BackendSyncState.Syncing
        viewModelScope.launch {
            val previous = backendEndpoint.baseUrl.first()
            runCatching {
                backendEndpoint.setBaseUrl(target)
                courseRepository.refreshCatalog(semester)
            }.onSuccess { catalog ->
                sync.value = BackendSyncState.Synced(courseCount = catalog.courses.size)
            }.onFailure { cause ->
                cause.rethrowIfCancellationOrFatal()
                Logger.i(cause.toString())
                runCatching { backendEndpoint.setBaseUrl(previous) }
                    .onFailure { it.rethrowIfCancellationOrFatal() }
                sync.value = BackendSyncState.Failed(BackendSyncError.SOURCE_UNAVAILABLE)
            }
        }
    }
}
