package org.mpc.presentation.model

import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.mpc.domain.model.CampusPlace
import org.mpc.presentation.theme.style

private const val RGB_MASK = 0xFFFFFF
private const val HEX_RADIX = 16
private const val HEX_DIGITS = 6

/**
 * The places as a GeoJSON FeatureCollection for the map. Each feature carries its [CampusPlace.id], the label
 * in [languageTag], its category and the category's colour as `#rrggbb`, so the layers style from the data.
 */
fun List<CampusPlace>.toGeoJson(languageTag: String): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        forEach { place ->
            addJsonObject {
                put("type", "Feature")
                putJsonObject("geometry") {
                    put("type", "Point")
                    // GeoJSON orders a position longitude first.
                    putJsonArray("coordinates") {
                        add(JsonPrimitive(place.location.longitude))
                        add(JsonPrimitive(place.location.latitude))
                    }
                }
                putJsonObject("properties") {
                    put("id", place.id)
                    put("label", place.name.resolve(languageTag))
                    put("category", place.category.wire)
                    val rgb = place.category.style.color.toArgb() and RGB_MASK
                    put("color", "#" + rgb.toString(HEX_RADIX).padStart(HEX_DIGITS, '0'))
                }
            }
        }
    }
}.toString()
