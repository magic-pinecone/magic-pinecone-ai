package org.mpc.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mohamedrejeb.calf.ui.web.WebView
import com.mohamedrejeb.calf.ui.web.rememberWebViewStateWithHTMLData
import dev.icerock.moko.resources.compose.readTextAsState
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.mpc.shared.MR
import org.mpc.shared.food_cert_mfr
import org.mpc.shared.food_cert_mft
import org.mpc.shared.food_cert_mr
import org.mpc.shared.food_certified_by
import org.mpc.shared.food_halal
import org.mpc.shared.food_halal_only
import org.mpc.shared.food_offline
import org.mpc.shared.food_sources
import org.mpc.shared.ncu_food_map_html
import org.mpc.shared.ncu_food_places_json

/**
 * Places to eat around NCU on a map, with the halal-certified ones marked.
 * The page and its places are bundled; `scripts/update_food_map.py` refreshes the places.
 */
@Composable
fun FoodMapScreen(modifier: Modifier = Modifier) {
    val page by MR.files.ncu_food_map_html.readTextAsState()
    val places by MR.files.ncu_food_places_json.readTextAsState()
    val labels = buildJsonObject {
        put("halal", stringResource(MR.strings.food_halal))
        put("halalOnly", stringResource(MR.strings.food_halal_only))
        put("cert_MR", stringResource(MR.strings.food_cert_mr))
        put("cert_MFR", stringResource(MR.strings.food_cert_mfr))
        put("cert_MFT", stringResource(MR.strings.food_cert_mft))
        put("certifiedBy", stringResource(MR.strings.food_certified_by))
        put("sources", stringResource(MR.strings.food_sources))
        put("offline", stringResource(MR.strings.food_offline))
    }.toString()
    val loadedPage = page
    val loadedPlaces = places
    if (loadedPage != null && loadedPlaces != null) {
        FoodMap(html = loadedPage.replace("__LABELS__", labels).replace("__PLACES__", loadedPlaces), modifier = modifier)
    }
}

@Composable
private fun FoodMap(
    html: String,
    modifier: Modifier,
) {
    val webViewState = rememberWebViewStateWithHTMLData(data = html)
    if (!webViewState.settings.javaScriptEnabled) {
        webViewState.settings.javaScriptEnabled = true
    }
    WebView(state = webViewState, modifier = modifier)
}
