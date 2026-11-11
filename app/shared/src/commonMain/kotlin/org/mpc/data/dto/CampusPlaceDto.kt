package org.mpc.data.dto

import kotlinx.serialization.Serializable

/** The backend's `GET /api/v1/campus/places`. */
@Serializable
internal data class CampusPlacesDto(
    val attribution: String = "",
    val places: List<CampusPlaceDto> = emptyList(),
)

/** One place. The newer fields are optional so an older backend still decodes. */
@Serializable
internal data class CampusPlaceDto(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val nameVi: String? = null,
    val code: String? = null,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val osmId: String? = null,
    val aliases: List<String> = emptyList(),
    val description: LocalizedTextDto? = null,
    val openingHours: String? = null,
    val priceRangeTwd: PriceRangeDto? = null,
    val priceNote: LocalizedTextDto? = null,
    val tags: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val mapLinks: MapLinksDto,
)

@Serializable
internal data class LocalizedTextDto(
    val zh: String = "",
    val en: String = "",
    val vi: String? = null,
)

@Serializable
internal data class PriceRangeDto(
    val min: Int,
    val max: Int,
)

@Serializable
internal data class MapLinksDto(
    val googleMaps: String,
    val googleWalkingDirections: String,
    val appleMaps: String,
)
