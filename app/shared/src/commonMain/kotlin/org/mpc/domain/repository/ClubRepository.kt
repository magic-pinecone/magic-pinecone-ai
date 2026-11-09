package org.mpc.domain.repository

import org.mpc.domain.model.Club

interface ClubRepository {
    /** Every club in the club site's order, or null when no backend is configured. */
    suspend fun fetchClubs(): List<Club>?
}
