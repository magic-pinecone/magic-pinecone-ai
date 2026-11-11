package org.mpc.data.mapper.dto

import org.mpc.data.dto.FoodPlaceDto
import org.mpc.data.dto.FoodPlacesDto
import org.mpc.domain.model.FoodPlace
import org.mpc.domain.model.FoodPlaces
import org.mpc.domain.model.GeoPoint
import org.mpc.domain.model.HalalCertificate
import org.mpc.domain.model.MapLinks

internal fun FoodPlacesDto.toDomain(): FoodPlaces = FoodPlaces(attribution = attribution, places = places.map { it.toDomain() })

internal fun FoodPlaceDto.toDomain(): FoodPlace = FoodPlace(
    id = id,
    name = name,
    location = GeoPoint(latitude = latitude, longitude = longitude),
    kind = kind?.takeIf { it.isNotBlank() },
    osmId = osmId,
    halal = halal?.let { HalalCertificate(certificates = it.certificates, certifier = it.certifier) },
    address = address?.takeIf { it.isNotBlank() },
    phone = phone?.takeIf { it.isNotBlank() },
    mapLinks = MapLinks(
        googleMaps = mapLinks.googleMaps,
        googleWalkingDirections = mapLinks.googleWalkingDirections,
        appleMaps = mapLinks.appleMaps,
    ),
)
