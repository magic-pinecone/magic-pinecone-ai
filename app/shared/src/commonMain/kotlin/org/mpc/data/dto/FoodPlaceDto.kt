package org.mpc.data.dto

import kotlinx.serialization.Serializable

/** The backend's `GET /api/v1/campus/food`. */
@Serializable
internal data class FoodPlacesDto(
    val attribution: String = "",
    val places: List<FoodPlaceDto> = emptyList(),
)

@Serializable
internal data class FoodPlaceDto(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val kind: String? = null,
    val osmId: String? = null,
    val halal: HalalDto? = null,
    val address: String? = null,
    val phone: String? = null,
    val mapLinks: MapLinksDto,
)

@Serializable
internal data class HalalDto(
    val certificates: List<String> = emptyList(),
    val certifier: String = "",
)
