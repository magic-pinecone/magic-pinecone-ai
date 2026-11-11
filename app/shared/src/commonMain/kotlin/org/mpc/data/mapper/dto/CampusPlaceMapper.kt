package org.mpc.data.mapper.dto

import org.mpc.data.dto.CampusPlaceDto
import org.mpc.data.dto.CampusPlacesDto
import org.mpc.data.dto.CampusUnitDto
import org.mpc.data.dto.LocalizedTextDto
import org.mpc.domain.model.CampusPlace
import org.mpc.domain.model.CampusPlaces
import org.mpc.domain.model.CampusUnit
import org.mpc.domain.model.GeoPoint
import org.mpc.domain.model.LocalizedText
import org.mpc.domain.model.MapLinks
import org.mpc.domain.model.PlaceCategory
import org.mpc.domain.model.PlaceTag
import org.mpc.domain.model.PriceRange

internal fun CampusPlacesDto.toDomain(): CampusPlaces = CampusPlaces(attribution = attribution, places = places.map { it.toDomain() })

internal fun CampusPlaceDto.toDomain(): CampusPlace = CampusPlace(
    id = id,
    name = LocalizedText(zh = nameZh, en = nameEn, vi = nameVi),
    code = code,
    category = PlaceCategory.fromWire(category),
    location = GeoPoint(latitude = latitude, longitude = longitude),
    osmId = osmId,
    aliases = aliases,
    description = description?.toDomain(),
    openingHours = openingHours?.takeIf { it.isNotBlank() },
    priceRange = priceRangeTwd?.let { PriceRange(min = it.min, max = it.max) },
    priceNote = priceNote?.toDomain(),
    tags = tags.mapNotNullTo(mutableSetOf(), PlaceTag::fromWire),
    mapLinks = MapLinks(
        googleMaps = mapLinks.googleMaps,
        googleWalkingDirections = mapLinks.googleWalkingDirections,
        appleMaps = mapLinks.appleMaps,
    ),
    locatedIn = locatedIn,
    units = units.map { it.toDomain() },
)

private fun CampusUnitDto.toDomain() = CampusUnit(
    name = LocalizedText(zh = nameZh, en = nameEn, vi = nameVi),
    aliases = aliases,
    floor = floor?.takeIf { it.isNotBlank() },
    website = website?.takeIf { it.isNotBlank() },
)

private fun LocalizedTextDto.toDomain() = LocalizedText(zh = zh, en = en, vi = vi)
