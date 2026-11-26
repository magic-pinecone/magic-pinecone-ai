package org.mpc.data.dataSource

import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.mpc.core.installAppDefaults
import org.mpc.data.mapper.dto.toDomain
import org.mpc.domain.model.HalalCertificate
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val foodJson =
    """
    {
      "fetched": "2026-11-11",
      "attribution": "© OpenStreetMap contributors, ODbL 1.0; halal certificates: Tourism Administration, MOTC",
      "places": [
        {
          "id": "halal-1", "name": "芙格漢堡餐車", "latitude": 24.953735, "longitude": 121.2371,
          "kind": "漢堡", "osm_id": null,
          "halal": {"certificates": ["MFR"], "certifier": "巴勒克清真產業有限公司"},
          "address": "桃園市中壢區民有街76號1樓", "phone": "+886-2-82871801",
          "map_links": {"google_maps": "g", "google_walking_directions": "w", "apple_maps": "a"}
        },
        {
          "id": "osm-1", "name": "某咖啡", "latitude": 24.97, "longitude": 121.19, "halal": null,
          "map_links": {"google_maps": "g", "google_walking_directions": "w", "apple_maps": "a"}
        }
      ]
    }
    """.trimIndent()

val foodPlacesDataSourceTests by testSuite {
    test("certified and unchecked restaurants are read, and no halal block means unchecked") {
        runTest {
            val requested = mutableListOf<String>()
            val client = HttpClient(
                MockEngine { request ->
                    requested += request.url.toString()
                    respond(foodJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                installAppDefaults()
            }

            val food = CampusPlacesDataSource(client).getFood("http://10.0.2.2:8000").toDomain()

            assertEquals(listOf("http://10.0.2.2:8000/api/v1/campus/food"), requested)
            assertEquals(true, food.attribution.contains("halal certificates"))
            val (certified, unchecked) = food.places
            assertEquals(HalalCertificate(listOf("MFR"), "巴勒克清真產業有限公司"), certified.halal)
            assertEquals("漢堡", certified.kind)
            assertEquals("+886-2-82871801", certified.phone)
            assertNull(unchecked.halal)
            assertNull(unchecked.address)
        }
    }
}
