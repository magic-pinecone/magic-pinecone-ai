package org.mpc.domain.model

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/** A price per person in New Taiwan dollars. */
data class PriceRange(
    val min: Int,
    val max: Int,
)

data class MapLinks(
    val googleMaps: String,
    val googleWalkingDirections: String,
    val appleMaps: String,
)

enum class PlaceCategory(val wire: String) {
    ACADEMIC("academic"),
    ADMINISTRATION("administration"),
    LIBRARY("library"),
    DORMITORY("dormitory"),
    DINING("dining"),
    SHOP("shop"),
    SPORTS("sports"),
    SERVICE("service"),
    TRANSIT("transit"),
    LANDMARK("landmark"),
    ;

    companion object {
        /** A category this app does not know yet is shown as a landmark rather than dropped. */
        fun fromWire(wire: String): PlaceCategory = entries.firstOrNull { it.wire == wire } ?: LANDMARK
    }
}

enum class PlaceTag(val wire: String) {
    VEGETARIAN("vegetarian"),
    HALAL("halal"),
    OPEN_24H("open_24h"),
    LATE_NIGHT("late_night"),
    CASH_ONLY("cash_only"),
    CARD_ACCEPTED("card_accepted"),
    ENGLISH_MENU("english_menu"),
    ;

    companion object {
        /** Null for a tag this app does not know yet. */
        fun fromWire(wire: String): PlaceTag? = entries.firstOrNull { it.wire == wire }
    }
}

/** A place on the NCU campus map, as served by `GET /api/v1/campus/places`; see docs/api-contract.md. */
data class CampusPlace(
    val id: String,
    val name: LocalizedText,
    /** Building code from the course system, such as "E6"; null for places that hold no classes. */
    val code: String?,
    val category: PlaceCategory,
    val location: GeoPoint,
    val osmId: String?,
    val aliases: List<String>,
    val description: LocalizedText?,
    /** Free text as published, for example "Mon-Fri 07:00-19:00". */
    val openingHours: String?,
    val priceRange: PriceRange?,
    val priceNote: LocalizedText?,
    val tags: Set<PlaceTag>,
    val mapLinks: MapLinks,
    /** The id of the building this place is inside, such as a shop in a dining hall; it shares the host's coordinates. */
    val locatedIn: String? = null,
)

/** The places plus the attribution their coordinates require. */
data class CampusPlaces(
    val attribution: String,
    val places: List<CampusPlace>,
    /** The campus outline, buildings and sports grounds as a GeoJSON FeatureCollection for the map; null when it did not load. */
    val areasGeoJson: String? = null,
    /** Restaurants around the campus; null when they did not load. */
    val food: FoodPlaces? = null,
)

/** True when [query] is part of the Chinese, English or Vietnamese name, the code or an alias, ignoring case. */
fun CampusPlace.matches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    val haystack = listOfNotNull(name.zh, name.en, name.vi, code) + aliases
    return haystack.any { it.contains(needle, ignoreCase = true) }
}
