package org.mpc.domain.model

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals

private val text = LocalizedText(zh = "松果餐廳", en = "Pine Cafeteria", vi = "Nhà ăn Thông")

val localizedTextTests by testSuite {
    test("each app language reads its own text") {
        assertEquals("松果餐廳", text.resolve("zh-TW"))
        assertEquals("Pine Cafeteria", text.resolve("en"))
        assertEquals("Nhà ăn Thông", text.resolve("vi"))
    }

    test("Vietnamese falls back to English, then Chinese") {
        assertEquals("Pine Cafeteria", text.copy(vi = null).resolve("vi"))
        assertEquals("Pine Cafeteria", text.copy(vi = " ").resolve("vi-VN"))
        assertEquals("松果餐廳", text.copy(vi = null, en = "").resolve("vi"))
    }

    test("a language the app does not translate reads English") {
        assertEquals("Pine Cafeteria", text.resolve("fr-FR"))
    }

    test("a place is found by any name, its code or an alias") {
        val place = CampusPlace(
            id = "e6",
            name = LocalizedText("工程五館", "Engineering Building 5"),
            code = "E6",
            category = PlaceCategory.ACADEMIC,
            location = GeoPoint(24.967, 121.187),
            osmId = null,
            aliases = listOf("工五館"),
            description = null,
            openingHours = null,
            priceRange = null,
            priceNote = null,
            tags = emptySet(),
            mapLinks = MapLinks("g", "w", "a"),
        )
        listOf("工程五", "engineering building", "e6", "工五", "  ").forEach { query ->
            assertEquals(true, place.matches(query), query)
        }
        assertEquals(false, place.matches("library"))
    }
}
