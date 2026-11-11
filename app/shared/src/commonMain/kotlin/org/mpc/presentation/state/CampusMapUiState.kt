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
        val areasGeoJson: String? = null,
    ) : CampusMapUiState {
        /**
         * The markers on the map: buildings, not the shops inside them. A building stays when it or any
         * place inside it is in [selectedCategory] and matches [query].
         */
        val visiblePlaces: List<CampusPlace> = run {
            val matchingHosts = places
                .filter { place -> (selectedCategory == null || place.category == selectedCategory) && place.matches(query) }
                .mapTo(mutableSetOf()) { it.locatedIn ?: it.id }
            places.filter { it.locatedIn == null && it.id in matchingHosts }
        }

        /** The places inside [host], listed in its sheet. */
        fun tenantsOf(host: CampusPlace): List<CampusPlace> = places.filter { it.locatedIn == host.id }

        /** The categories that have at least one place, in declaration order, for the filter chips. */
        val categories: List<PlaceCategory> = PlaceCategory.entries.filter { category -> places.any { it.category == category } }
    }
}
