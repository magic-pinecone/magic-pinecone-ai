package org.mpc.domain.model

/** A halal certificate held by a restaurant, such as "MFR" issued by [certifier]. */
data class HalalCertificate(
    val certificates: List<String>,
    val certifier: String,
)

/**
 * A restaurant or cafe around the campus, from `GET /api/v1/campus/food`. Unlike a [CampusPlace] it has no
 * translations: names and addresses are as the sources wrote them.
 */
data class FoodPlace(
    val id: String,
    val name: String,
    val location: GeoPoint,
    /** The kind of food, as the source words it, such as "漢堡". */
    val kind: String?,
    val osmId: String?,
    /** Null means nobody checked, not that the place is not halal. */
    val halal: HalalCertificate?,
    val address: String?,
    val phone: String?,
    val mapLinks: MapLinks,
)

/** The food places plus the attribution their data requires. */
data class FoodPlaces(
    val attribution: String,
    val places: List<FoodPlace>,
)
