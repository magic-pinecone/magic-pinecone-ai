package org.mpc.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class FoodPlacesTest {
    private val places =
        parseFoodPlaces(
            """
            {"fetched": "2026-11-11", "center": [24.9682, 121.1955],
             "halal": [{"name": "Halal Burger", "lat": 24.953735, "lon": 121.2371, "kind": "漢堡",
                        "certificates": ["MFR"], "certifier": "X", "address": "中壢區民有街76號", "phone": "+886-2-1"}],
             "nearby": [{"name": "Far Noodles", "lat": 24.9782, "lon": 121.1955, "kind": "noodle"},
                        {"name": "Near Cafe", "lat": 24.9683, "lon": 121.1955, "kind": "cafe"}]}
            """,
        )

    @Test
    fun listsNearestFirstAndSearchesNameKindAndAddress() {
        assertEquals(listOf("Near Cafe", "Far Noodles"), places.matching(places.nearby, " ").map { it.name })
        assertEquals(listOf("Far Noodles"), places.matching(places.nearby, "NOODLE").map { it.name })
        assertEquals(listOf("Halal Burger"), places.matching(places.halal, "民有街").map { it.name })
        assertEquals(emptyList(), places.matching(places.halal, "cafe"))
    }

    @Test
    fun measuresDistanceFromCampus() {
        // One hundredth of a degree north is about 1.1 km; the halal place is about 4.5 km away.
        assertEquals("1.1 km", distanceText(places.metersTo(places.nearby[0])))
        assertEquals("11 m", distanceText(places.metersTo(places.nearby[1])))
        assertEquals("4.5 km", distanceText(places.metersTo(places.halal[0])))
    }
}
