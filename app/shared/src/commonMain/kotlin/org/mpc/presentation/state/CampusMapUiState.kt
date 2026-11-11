package org.mpc.presentation.state

import org.mpc.domain.model.CampusPlace
import org.mpc.domain.model.FoodPlace
import org.mpc.domain.model.FoodPlaces
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
        /** Restaurants around the campus; null when they did not load, which hides the food chips. */
        val food: FoodPlaces? = null,
        val showFood: Boolean = false,
        /** Shows only the restaurants with a halal certificate. */
        val halalOnly: Boolean = false,
        val selectedFood: FoodPlace? = null,
    ) : CampusMapUiState {
        /** The restaurants on the map: none while food is hidden, and only certified ones when [halalOnly]. */
        val visibleFood: List<FoodPlace> =
            if (showFood) food?.places.orEmpty().filter { !halalOnly || it.halal != null } else emptyList()

        /** The attribution to show: the food data adds its own sources while it is on the map. */
        val shownAttribution: String = if (showFood) food?.attribution?.ifBlank { null } ?: attribution else attribution

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
