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
import org.mpc.domain.model.CampusPlaces
import org.mpc.domain.model.GeoPoint
import org.mpc.domain.model.PlaceCategory
import org.mpc.domain.model.PlaceTag
import org.mpc.domain.model.PriceRange
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val BACKEND = "http://10.0.2.2:8000"

private const val LINKS = """
  "map_links": {
    "google_maps": "https://www.google.com/maps/search/?api=1&query=24.9,121.1",
    "google_walking_directions": "https://www.google.com/maps/dir/?api=1&destination=24.9%2C121.1&travelmode=walking",
    "apple_maps": "https://maps.apple.com/?daddr=24.9%2C121.1&dirflg=w"
  }"""

// What the backend answers today, before the food and price fields exist.
private val currentBackendJson =
    """
    {
      "attribution": "© OpenStreetMap contributors, ODbL 1.0",
      "places": [
        {
          "id": "engineering-building-5", "name_zh": "工程五館", "name_en": "Engineering Building 5",
          "code": "E6", "category": "academic", "latitude": 24.967014, "longitude": 121.187598,
          "osm_id": "way/268561940", "aliases": ["工五館"], "note_en": null,
          $LINKS
        }
      ]
    }
    """.trimIndent()

// What it answers once the food fields land, including values this app has never heard of.
private val enrichedBackendJson =
    """
    {
      "attribution": "© OpenStreetMap contributors, ODbL 1.0",
      "places": [
        {
          "id": "pine-cafeteria", "name_zh": "松果餐廳", "name_en": "Pine Cafeteria", "name_vi": "Nhà ăn Thông",
          "code": null, "category": "dining", "latitude": 24.9682, "longitude": 121.192,
          "osm_id": null, "aliases": [],
          "description": {"zh": "學生餐廳", "en": "Student canteen", "vi": "Căng tin sinh viên"},
          "opening_hours": "Mon-Fri 07:00-19:00",
          "price_range_twd": {"min": 60, "max": 120},
          "price_note": {"zh": "套餐", "en": "Set meals"},
          "tags": ["vegetarian", "halal", "teleport"],
          "sources": ["https://example.org/menu"],
          $LINKS
        },
        {
          "id": "mystery", "name_zh": "神秘", "name_en": "Mystery", "category": "hologram",
          "latitude": 24.0, "longitude": 121.0, "aliases": [],
          $LINKS
        }
      ]
    }
    """.trimIndent()

private suspend fun fetch(json: String): CampusPlaces {
    val requestedUrls = mutableListOf<String>()
    val client = HttpClient(
        MockEngine { request ->
            requestedUrls += request.url.toString()
            respond(content = json, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ) {
        installAppDefaults()
    }
    val result = CampusPlacesDataSource(client).getPlaces(BACKEND).toDomain()
    assertEquals(listOf("$BACKEND/api/v1/campus/places"), requestedUrls)
    return result
}

val campusPlacesDataSourceTests by testSuite {
    test("a backend without the food fields still decodes") {
        runTest {
            val result = fetch(currentBackendJson)

            assertEquals("© OpenStreetMap contributors, ODbL 1.0", result.attribution)
            val place = result.places.single()
            assertEquals("工程五館", place.name.zh)
            assertEquals("E6", place.code)
            assertEquals(PlaceCategory.ACADEMIC, place.category)
            assertEquals(GeoPoint(24.967014, 121.187598), place.location)
            assertEquals(listOf("工五館"), place.aliases)
            assertNull(place.name.vi)
            assertNull(place.description)
            assertNull(place.openingHours)
            assertNull(place.priceRange)
            assertEquals(emptySet(), place.tags)
        }
    }

    test("food fields are read and unknown tags and categories do not break decoding") {
        runTest {
            val (food, mystery) = fetch(enrichedBackendJson).places

            assertEquals("Nhà ăn Thông", food.name.vi)
            assertEquals("Student canteen", food.description?.en)
            assertEquals("Mon-Fri 07:00-19:00", food.openingHours)
            assertEquals(PriceRange(60, 120), food.priceRange)
            assertEquals("Set meals", food.priceNote?.en)
            assertNull(food.priceNote?.vi)
            assertEquals(setOf(PlaceTag.VEGETARIAN, PlaceTag.HALAL), food.tags)
            assertEquals(PlaceCategory.DINING, food.category)
            assertEquals(PlaceCategory.LANDMARK, mystery.category)
        }
    }
}
