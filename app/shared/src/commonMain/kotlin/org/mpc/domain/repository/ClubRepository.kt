package org.mpc.domain.repository

import org.mpc.domain.model.Club

interface ClubRepository {
    /**
     * Every club in the club site's order with its text in [language] (a language tag, or null for the device
     * language), or null when no backend is configured.
     */
    suspend fun fetchClubs(language: String?): List<Club>?
}
