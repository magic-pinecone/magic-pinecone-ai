package org.mpc.presentation.model

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mpc.domain.model.CampusPlace
import org.mpc.domain.model.GeoPoint
import org.mpc.domain.model.LocalizedText
import org.mpc.domain.model.MapLinks
import org.mpc.domain.model.PlaceCategory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val canteen = CampusPlace(
    id = "pine-canteen",
    name = LocalizedText("松果餐廳", "Pine Canteen", "Nhà ăn Thông"),
    code = null,
    category = PlaceCategory.DINING,
    location = GeoPoint(latitude = 24.9682, longitude = 121.192),
    osmId = null,
    aliases = emptyList(),
    description = null,
    openingHours = null,
    priceRange = null,
    priceNote = null,
    tags = emptySet(),
    mapLinks = MapLinks("g", "w", "a"),
)

private fun features(json: String): JsonArray = Json.parseToJsonElement(json).jsonObject.getValue("features").jsonArray

val campusPlaceGeoJsonTests by testSuite {
    test("each place becomes a point feature with longitude first and its label in the app language") {
        val feature = features(listOf(canteen).toGeoJson("vi")).single().jsonObject

        val coordinates = feature.getValue("geometry").jsonObject.getValue("coordinates").jsonArray
        assertEquals(listOf(121.192, 24.9682), coordinates.map { it.jsonPrimitive.doubleOrNull })
        val properties: JsonObject = feature.getValue("properties").jsonObject
        assertEquals("pine-canteen", properties.getValue("id").jsonPrimitive.content)
        assertEquals("Nhà ăn Thông", properties.getValue("label").jsonPrimitive.content)
        assertEquals("dining", properties.getValue("category").jsonPrimitive.content)
        assertTrue(Regex("#[0-9a-f]{6}").matches(properties.getValue("color").jsonPrimitive.content))
    }

    test("an empty list is an empty feature collection") {
        assertEquals(0, features(emptyList<CampusPlace>().toGeoJson("en")).size)
    }
}
