package org.mpc.domain.repository

import kotlinx.coroutines.flow.Flow
import org.mpc.domain.model.GovGuides

interface GovGuidesRepository {
    /** The backend the guides are read from, or null when none is configured. Emits again when it changes. */
    val backend: Flow<String?>

    /**
     * Reads the guides from the configured backend, with guides written in another language machine-translated
     * into [language] (a language tag; null for the device language). Null when no backend is configured.
     */
    suspend fun fetchGuides(language: String?): GovGuides?
}
