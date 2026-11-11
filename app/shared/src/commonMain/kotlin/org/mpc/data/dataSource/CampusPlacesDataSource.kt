package org.mpc.data.dataSource

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.mpc.data.dto.CampusPlacesDto
import org.mpc.data.dto.FoodPlacesDto

/** Reads the campus places of a Magic Pinecone backend. */
@Inject
internal class CampusPlacesDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun getPlaces(baseUrl: String): CampusPlacesDto = httpClient
        .get("$baseUrl/api/v1/campus/places") {
            expectSuccess = true
        }.body<CampusPlacesDto>()

    suspend fun getFood(baseUrl: String): FoodPlacesDto = httpClient
        .get("$baseUrl/api/v1/campus/food") {
            expectSuccess = true
        }.body<FoodPlacesDto>()

    /** The campus outline and building shapes as GeoJSON text; the map's source reads it as is. */
    suspend fun getAreasGeoJson(baseUrl: String): String = httpClient
        .get("$baseUrl/api/v1/campus/map.geojson") {
            expectSuccess = true
        }.bodyAsText()
}
