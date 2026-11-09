package org.mpc.domain.repository

import kotlinx.coroutines.flow.Flow

interface BackendEndpointRepository {
    /** Base URL of the Magic Pinecone backend to read courses from, or null to read the published static catalog. */
    val baseUrl: Flow<String?>

    /** Stores [baseUrl] as given; pass null to go back to the static catalog. */
    suspend fun setBaseUrl(baseUrl: String?)
}
