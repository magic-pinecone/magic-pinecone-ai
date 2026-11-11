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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mpc.domain.model.CampusPlaces
import org.mpc.domain.model.PlaceCategory
import org.mpc.domain.repository.CampusPlacesRepository
import org.mpc.presentation.state.CampusMapUiState

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class CampusMapViewModel(
    private val repository: CampusPlacesRepository,
) : ViewModel() {
    private sealed interface Load {
        data object Loading : Load

        data object NeedsBackend : Load

        data object Failed : Load

        data class Done(val places: CampusPlaces) : Load
    }

    private data class Selection(
        val category: PlaceCategory? = null,
        val query: String = "",
        val placeId: String? = null,
        val foodId: String? = null,
        val showFood: Boolean = false,
        val halalOnly: Boolean = false,
    )

    private val load = MutableStateFlow<Load>(Load.Loading)
    private val selection = MutableStateFlow(Selection())

    val uiState: StateFlow<CampusMapUiState> = combine(load, selection) { load, selection ->
        when (load) {
            Load.Loading -> CampusMapUiState.Loading

            Load.NeedsBackend -> CampusMapUiState.NeedsBackend

            Load.Failed -> CampusMapUiState.Failed

            is Load.Done -> CampusMapUiState.Loaded(
                places = load.places.places,
                attribution = load.places.attribution,
                areasGeoJson = load.places.areasGeoJson,
                selectedCategory = selection.category,
                query = selection.query,
                selectedPlace = load.places.places.firstOrNull { it.id == selection.placeId },
                food = load.places.food,
                showFood = selection.showFood,
                halalOnly = selection.halalOnly,
                selectedFood = load.places.food?.places?.firstOrNull { it.id == selection.foodId },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CampusMapUiState.Loading,
    )

    private var loading: Job? = null

    init {
        // Loads at start and again whenever the backend address changes in Settings.
        viewModelScope.launch {
            repository.backend.distinctUntilChanged().collect { refresh() }
        }
    }

    fun refresh() {
        loading?.cancel()
        load.value = Load.Loading
        loading = viewModelScope.launch {
            load.value = runCatching { repository.fetchPlaces() }
                .fold(
                    onSuccess = { places -> places?.let(Load::Done) ?: Load.NeedsBackend },
                    onFailure = { cause ->
                        cause.rethrowIfCancellationOrFatal()
                        Logger.i(cause.toString())
                        Load.Failed
                    },
                )
        }
    }

    /** Shows only [category], or every category when null. */
    fun selectCategory(category: PlaceCategory?) = selection.update { it.copy(category = category) }

    fun search(query: String) = selection.update { it.copy(query = query) }

    /** Opens the place with [id] in the sheet; null closes it. */
    fun selectPlace(id: String?) = selection.update { it.copy(placeId = id, foodId = null) }

    /** Opens the restaurant with [id] in the sheet; null closes it. */
    fun selectFood(id: String?) = selection.update { it.copy(foodId = id, placeId = null) }

    fun setShowFood(show: Boolean) = selection.update { it.copy(showFood = show, halalOnly = it.halalOnly && show, foodId = null) }

    /** Shows only certified restaurants; turning it on also shows the food layer. */
    fun setHalalOnly(halalOnly: Boolean) = selection.update { it.copy(halalOnly = halalOnly, showFood = it.showFood || halalOnly, foodId = null) }
}
