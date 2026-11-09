package org.mpc.presentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mpc.data.repository.BackendNotConfiguredException
import org.mpc.domain.model.AnnouncementScope
import org.mpc.domain.model.AnnouncementSummary
import org.mpc.domain.model.UserAffiliation
import org.mpc.domain.repository.AnnouncementRepository
import org.mpc.domain.repository.AppSettingsRepository
import org.mpc.domain.repository.BackendEndpointRepository
import org.mpc.presentation.state.AnnouncementFilterSpec
import org.mpc.presentation.state.AnnouncementLoadStatus
import org.mpc.presentation.state.AnnouncementUiState
import org.mpc.presentation.state.buildAnnouncementUiState

private data class AnnouncementFilterInput(
    val scope: AnnouncementScope = AnnouncementScope.AFFILIATION,
    val category: String? = null,
    val searchQuery: String = "",
)

private data class AnnouncementFeedSnapshot(
    val status: AnnouncementLoadStatus = AnnouncementLoadStatus.LOADING,
    val items: List<AnnouncementSummary> = emptyList(),
)

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class AnnouncementViewModel(
    private val announcementRepository: AnnouncementRepository,
    private val settingsRepository: AppSettingsRepository,
    private val backendEndpointRepository: BackendEndpointRepository,
) : ViewModel() {
    private val feedSnapshot = MutableStateFlow(AnnouncementFeedSnapshot())
    private val filterInput = MutableStateFlow(AnnouncementFilterInput())

    val uiState: StateFlow<AnnouncementUiState> = combine(
        feedSnapshot,
        settingsRepository.settings,
        filterInput,
    ) { snapshot, settings, filter ->
        buildAnnouncementUiState(
            status = snapshot.status,
            allAnnouncements = snapshot.items,
            filter = AnnouncementFilterSpec(
                selectedScope = filter.scope,
                selectedAffiliation = settings.userAffiliation,
                selectedDepartment = settings.userDepartment,
                selectedCategory = filter.category,
                searchQuery = filter.searchQuery,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AnnouncementUiState(),
    )

    init {
        viewModelScope.launch {
            backendEndpointRepository.baseUrl
                .distinctUntilChanged()
                .collect {
                    loadAnnouncements(forceRefresh = false)
                }
        }
    }

    fun loadAnnouncements(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            feedSnapshot.value = feedSnapshot.value.copy(status = AnnouncementLoadStatus.LOADING)
            try {
                val feed = announcementRepository.fetchAnnouncements(forceRefresh = forceRefresh)
                feedSnapshot.value = AnnouncementFeedSnapshot(
                    status = AnnouncementLoadStatus.LOADED,
                    items = feed.announcements,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackendNotConfiguredException) {
                feedSnapshot.value = AnnouncementFeedSnapshot(
                    status = AnnouncementLoadStatus.NEEDS_BACKEND,
                    items = emptyList(),
                )
            } catch (_: Exception) {
                feedSnapshot.value = AnnouncementFeedSnapshot(
                    status = AnnouncementLoadStatus.UNAVAILABLE,
                    items = emptyList(),
                )
            }
        }
    }

    fun selectScope(scope: AnnouncementScope) {
        filterInput.value = filterInput.value.copy(scope = scope, category = null)
    }

    fun selectAffiliation(affiliation: UserAffiliation) {
        viewModelScope.launch {
            runCatching { settingsRepository.setUserAffiliation(affiliation) }
        }
    }

    fun selectDepartment(department: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.setUserDepartment(department) }
        }
    }

    fun selectCategory(category: String?) {
        filterInput.value = filterInput.value.copy(category = category)
    }

    fun updateSearchQuery(query: String) {
        filterInput.value = filterInput.value.copy(searchQuery = query)
    }
}
