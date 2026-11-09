package org.mpc.domain.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * An ee-class `/ajax` reply. Only its payload and error text cross: handler flags such as `customJs`, which the site's
 * own script would run, are dropped. A payload can hold token-bearing links, such as an upload's delete link, so read it
 * inside [CampusSessions.fetchThen] and return only what was parsed from it.
 */
sealed interface EeclassAjax {
    class Done(val data: JsonElement) : EeclassAjax

    class Refused(val message: String?) : EeclassAjax
}

/** Reads `{status, msg, data}` and `{ret: {status, msg, data}}`, as the site's own `fs.respHandler` does; null for anything else. */
fun eeclassAjax(text: String): EeclassAjax? {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
    val envelope = root["ret"] as? JsonObject ?: root
    // Editor saves send the string "true"; submit and withdraw send a boolean. booleanOrNull reads either.
    return when ((envelope["status"] as? JsonPrimitive)?.booleanOrNull) {
        true -> EeclassAjax.Done(envelope["data"] ?: JsonNull)
        false -> EeclassAjax.Refused((envelope["msg"] as? JsonPrimitive)?.takeIf { it.isString }?.content)
        null -> null
    }
}
