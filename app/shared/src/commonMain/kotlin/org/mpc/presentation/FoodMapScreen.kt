package org.mpc.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mohamedrejeb.calf.ui.web.WebView
import com.mohamedrejeb.calf.ui.web.rememberWebViewStateWithHTMLData
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.readTextAsState
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.shared.MR
import org.mpc.shared.food_cert_mfr
import org.mpc.shared.food_cert_mft
import org.mpc.shared.food_cert_mr
import org.mpc.shared.food_certified_by
import org.mpc.shared.food_clear_search
import org.mpc.shared.food_halal
import org.mpc.shared.food_halal_only
import org.mpc.shared.food_nearby
import org.mpc.shared.food_no_match
import org.mpc.shared.food_offline
import org.mpc.shared.food_search
import org.mpc.shared.food_sources
import org.mpc.shared.food_view_list
import org.mpc.shared.food_view_map
import org.mpc.shared.ncu_food_map_html
import org.mpc.shared.ncu_food_places_json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val METERS_PER_DEGREE = 111_320.0
private const val METERS_PER_KM = 1000
private const val KM_DECIMALS = 10.0

@Serializable
internal data class FoodPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    val kind: String = "",
    val certificates: List<String> = emptyList(),
    val certifier: String = "",
    val address: String = "",
    val phone: String = "",
)

@Serializable
internal data class FoodPlaces(
    val fetched: String,
    val center: List<Double>,
    val halal: List<FoodPlace>,
    val nearby: List<FoodPlace>,
)

internal fun parseFoodPlaces(json: String): FoodPlaces = Json.decodeFromString(json)

/** Straight-line metres from campus; flat-earth maths is exact enough within the few kilometres the list covers. */
internal fun FoodPlaces.metersTo(place: FoodPlace): Int {
    val north = (place.lat - center[0]) * METERS_PER_DEGREE
    val east = (place.lon - center[1]) * METERS_PER_DEGREE * cos(center[0] * PI / 180)
    return sqrt(north * north + east * east).roundToInt()
}

/** The places in [group] that mention [query] in their name, food or address, nearest first. */
internal fun FoodPlaces.matching(
    group: List<FoodPlace>,
    query: String,
): List<FoodPlace> {
    val wanted = query.trim()
    return group
        .filter { place -> listOf(place.name, place.kind, place.address).any { it.contains(wanted, ignoreCase = true) } }
        .sortedBy { metersTo(it) }
}

internal fun distanceText(meters: Int): String = if (meters < METERS_PER_KM) "$meters m" else "${(meters / (METERS_PER_KM / KM_DECIMALS)).roundToInt() / KM_DECIMALS} km"

/**
 * Places to eat around NCU, with the halal-certified ones marked: a searchable list first, and a map for browsing by area.
 * The page and its places are bundled; `scripts/update_food_map.py` refreshes the places.
 */
@Composable
fun FoodMapScreen(modifier: Modifier = Modifier) {
    val page by MR.files.ncu_food_map_html.readTextAsState()
    val placesJson by MR.files.ncu_food_places_json.readTextAsState()
    var showMap by rememberSaveable { mutableStateOf(false) }
    // Kept here so a look at the map does not throw the search away.
    var query by rememberSaveable { mutableStateOf("") }
    var halalOnly by rememberSaveable { mutableStateOf(false) }
    val loadedPage = page
    val loadedPlaces = placesJson ?: return

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MpcSpacing.space4, vertical = MpcSpacing.space2),
        ) {
            listOf(MR.strings.food_view_list, MR.strings.food_view_map).forEachIndexed { index, label ->
                SegmentedButton(
                    selected = showMap == (index == 1),
                    onClick = { showMap = index == 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                ) { Text(stringResource(label)) }
            }
        }
        if (!showMap) {
            FoodList(
                places = remember(loadedPlaces) { parseFoodPlaces(loadedPlaces) },
                query = query,
                onQueryChange = { query = it },
                halalOnly = halalOnly,
                onHalalOnlyChange = { halalOnly = it },
            )
        } else if (loadedPage != null) {
            FoodMap(html = loadedPage.replace("__LABELS__", mapLabels()).replace("__PLACES__", loadedPlaces))
        }
    }
}

@Composable
private fun FoodList(
    places: FoodPlaces,
    query: String,
    onQueryChange: (String) -> Unit,
    halalOnly: Boolean,
    onHalalOnlyChange: (Boolean) -> Unit,
) {
    val halal = remember(places, query) { places.matching(places.halal, query) }
    val nearby = remember(places, query, halalOnly) { if (halalOnly) emptyList() else places.matching(places.nearby, query) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = MpcSpacing.space4, vertical = MpcSpacing.space2),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(MR.strings.food_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(MR.strings.food_clear_search))
                        }
                    }
                },
                singleLine = true,
            )
            FilterChip(
                selected = halalOnly,
                onClick = { onHalalOnlyChange(!halalOnly) },
                label = { Text(stringResource(MR.strings.food_halal_only)) },
            )
        }
        if (halal.isEmpty() && nearby.isEmpty()) {
            item(key = "no-match") { Text(stringResource(MR.strings.food_no_match, query.trim())) }
        }
        foodSection(MR.strings.food_halal, halal) { FoodCard(it, places.metersTo(it), halal = true) }
        foodSection(MR.strings.food_nearby, nearby) { FoodCard(it, places.metersTo(it), halal = false) }
        item(key = "sources") {
            Text(
                stringResource(MR.strings.food_sources, places.fetched),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LazyListScope.foodSection(
    title: StringResource,
    places: List<FoodPlace>,
    card: @Composable (FoodPlace) -> Unit,
) {
    if (places.isEmpty()) return
    item {
        Text(
            text = "${stringResource(title)} · ${places.size}",
            modifier = Modifier.padding(top = MpcSpacing.space2).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    items(places) { card(it) }
}

@Composable
private fun FoodCard(
    place: FoodPlace,
    meters: Int,
    halal: Boolean,
) {
    val uriHandler = LocalUriHandler.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(MpcSpacing.space4), verticalArrangement = Arrangement.spacedBy(MpcSpacing.space1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
                Text(place.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (halal) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                        Text(
                            stringResource(MR.strings.food_halal),
                            modifier = Modifier.padding(horizontal = MpcSpacing.space2, vertical = MpcSpacing.space1),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Text(
                listOf(place.kind, distanceText(meters)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
            )
            if (halal) {
                val certificates = place.certificates.map { code -> certificateLabel(code)?.let { stringResource(it) } ?: code }
                Text(certificates.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(MR.strings.food_certified_by, place.certifier), style = MaterialTheme.typography.bodySmall, color = secondary)
            }
            if (place.address.isNotBlank()) Text(place.address, style = MaterialTheme.typography.bodySmall, color = secondary)
            if (place.phone.isNotBlank()) {
                Text(
                    place.phone,
                    modifier = Modifier.clickable(role = Role.Button) { uriHandler.openUri("tel:${place.phone}") }.padding(vertical = MpcSpacing.space2),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun certificateLabel(code: String): StringResource? = when (code) {
    "MR" -> MR.strings.food_cert_mr
    "MFR" -> MR.strings.food_cert_mfr
    "MFT" -> MR.strings.food_cert_mft
    else -> null
}

// Each platform writes an unfilled placeholder differently, so the page gets one it can rely on.
@Composable
private fun mapLabels(): String = buildJsonObject {
    put("halal", stringResource(MR.strings.food_halal))
    put("halalOnly", stringResource(MR.strings.food_halal_only))
    put("cert_MR", stringResource(MR.strings.food_cert_mr))
    put("cert_MFR", stringResource(MR.strings.food_cert_mfr))
    put("cert_MFT", stringResource(MR.strings.food_cert_mft))
    put("certifiedBy", stringResource(MR.strings.food_certified_by, "{0}"))
    put("sources", stringResource(MR.strings.food_sources, "{0}"))
    put("offline", stringResource(MR.strings.food_offline))
}.toString()

@Composable
private fun FoodMap(html: String) {
    val webViewState = rememberWebViewStateWithHTMLData(data = html)
    if (!webViewState.settings.javaScriptEnabled) {
        webViewState.settings.javaScriptEnabled = true
    }
    WebView(state = webViewState, modifier = Modifier.fillMaxSize())
}
