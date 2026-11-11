package org.mpc.presentation.state

import org.mpc.domain.model.CampusPlace
import org.mpc.domain.model.PlaceCategory
import org.mpc.domain.model.matches

sealed interface CampusMapUiState {
    data object Loading : CampusMapUiState

    /** No backend address is set, and the places are only served by a backend. */
    data object NeedsBackend : CampusMapUiState

    data object Failed : CampusMapUiState

    data class Loaded(
        val places: List<CampusPlace>,
        /** The attribution the place coordinates require, as the backend words it. */
        val attribution: String,
        /** Null shows every category. */
        val selectedCategory: PlaceCategory? = null,
        val query: String = "",
        val selectedPlace: CampusPlace? = null,
    ) : CampusMapUiState {
        /** The places the map shows: those in [selectedCategory] that match [query]. */
        val visiblePlaces: List<CampusPlace> =
            places.filter { place -> (selectedCategory == null || place.category == selectedCategory) && place.matches(query) }

        /** The categories that have at least one place, in declaration order, for the filter chips. */
        val categories: List<PlaceCategory> = PlaceCategory.entries.filter { category -> places.any { it.category == category } }
    }
}
