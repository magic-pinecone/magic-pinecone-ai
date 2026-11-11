package org.mpc.presentation.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.mpc.domain.model.FoodPlace

/**
 * The restaurants as a GeoJSON FeatureCollection. A feature carries its id, whether it is halal-certified, and
 * [halalLabel] as `label` when it is, so the layers can colour and caption certified places differently.
 */
fun List<FoodPlace>.toFoodGeoJson(halalLabel: String): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        forEach { place ->
            addJsonObject {
                put("type", "Feature")
                putJsonObject("geometry") {
                    put("type", "Point")
                    putJsonArray("coordinates") {
                        add(JsonPrimitive(place.location.longitude))
                        add(JsonPrimitive(place.location.latitude))
                    }
                }
                putJsonObject("properties") {
                    put("id", place.id)
                    put("halal", place.halal != null)
                    put("label", if (place.halal != null) halalLabel else "")
                }
            }
        }
    }
}.toString()
