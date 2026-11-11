package org.mpc.data.dataSource

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import org.mpc.data.dto.CampusPlacesDto

/** Reads the campus places of a Magic Pinecone backend. */
@Inject
internal class CampusPlacesDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun getPlaces(baseUrl: String): CampusPlacesDto = httpClient
        .get("$baseUrl/api/v1/campus/places") {
            expectSuccess = true
        }.body<CampusPlacesDto>()
}
