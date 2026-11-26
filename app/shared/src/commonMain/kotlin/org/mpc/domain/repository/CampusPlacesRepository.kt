package org.mpc.domain.repository

import kotlinx.coroutines.flow.Flow
import org.mpc.domain.model.CampusPlaces

interface CampusPlacesRepository {
    /** The backend the places are read from, or null when none is configured. Emits again when it changes. */
    val backend: Flow<String?>

    /** Reads the places from the configured backend. Returns null when no backend is configured: this data has no static fallback. */
    suspend fun fetchPlaces(): CampusPlaces?
}
