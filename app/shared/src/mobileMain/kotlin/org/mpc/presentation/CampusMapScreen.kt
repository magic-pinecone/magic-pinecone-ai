package org.mpc.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.compose.stringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.asBoolean
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.neq
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FeaturesClickHandler
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import org.mpc.domain.model.GeoPoint
import org.mpc.domain.model.PlaceCategory
import org.mpc.presentation.model.toFoodGeoJson
import org.mpc.presentation.model.toGeoJson
import org.mpc.presentation.state.CampusMapUiState
import org.mpc.presentation.state.FoodLayer
import org.mpc.presentation.theme.FoodMarkerColors
import org.mpc.presentation.theme.MpcShapes
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.theme.style
import org.mpc.presentation.viewModel.AppSettingsViewModel
import org.mpc.presentation.viewModel.CampusMapViewModel
import org.mpc.shared.MR
import org.mpc.shared.campus_map_all
import org.mpc.shared.campus_map_attribution
import org.mpc.shared.campus_map_clear_search
import org.mpc.shared.campus_map_failed
import org.mpc.shared.campus_map_food_nearby
import org.mpc.shared.campus_map_halal_only
import org.mpc.shared.campus_map_no_results
import org.mpc.shared.campus_map_search
import org.mpc.shared.campus_map_tag_halal
import org.mpc.shared.intl_needs_backend
import org.mpc.shared.intl_retry
import org.mpc.shared.settings_course_source

private const val LIBERTY_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val NCU_LATITUDE = 24.9682
private const val NCU_LONGITUDE = 121.1920
private const val CAMPUS_ZOOM = 15.5
private const val PLACE_ZOOM = 17.0
private const val SEARCH_SETTLE_MILLIS = 400L

private suspend fun MapState.fitTo(points: List<GeoPoint>) {
    val only = points.singleOrNull()
    if (only != null) {
        animateCamera(CameraUpdate(center = Position(longitude = only.longitude, latitude = only.latitude), zoom = PLACE_ZOOM))
        return
    }
    animateCameraToBounds(
        boundingBox = BoundingBox(
            west = points.minOf { it.longitude },
            south = points.minOf { it.latitude },
            east = points.maxOf { it.longitude },
            north = points.maxOf { it.latitude },
        ),
        fit = CameraFit(fitPadding = DpPadding(left = 48.dp, top = 160.dp, right = 48.dp, bottom = 96.dp)),
    )
}
private val SPORTS_FILL = Color(0xFF2F9E44)
private const val LABEL_MIN_ZOOM = 16f
private val LABEL_FONT = listOf("Noto Sans Regular")

/** The NCU campus map: places from the backend on an OpenFreeMap basemap, with category filter, search and a detail sheet. */
@Composable
fun CampusMapScreen(
    modifier: Modifier = Modifier,
    viewModel: CampusMapViewModel = metroViewModel(),
) {
    val settingsViewModel: AppSettingsViewModel = metroViewModel()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // SYSTEM has an empty tag and means the device language.
    val languageTag = settings.language.tag.ifEmpty { Locale.current.toLanguageTag() }
    val actions = remember(viewModel) {
        CampusMapActions(
            onRetry = viewModel::refresh,
            onSelectCategory = viewModel::selectCategory,
            onSearch = viewModel::search,
            onSelectPlace = viewModel::selectPlace,
            onSelectFood = viewModel::selectFood,
            onShowFood = viewModel::showFood,
        )
    }
    CampusMapContent(state = state, languageTag = languageTag, actions = actions, modifier = modifier)
}

class CampusMapActions(
    val onRetry: () -> Unit,
    val onSelectCategory: (PlaceCategory?) -> Unit,
    val onSearch: (String) -> Unit,
    val onSelectPlace: (String?) -> Unit,
    val onSelectFood: (String?) -> Unit,
    val onShowFood: (FoodLayer) -> Unit,
)

@Composable
fun CampusMapContent(
    state: CampusMapUiState,
    languageTag: String,
    actions: CampusMapActions,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CampusMapUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        CampusMapUiState.NeedsBackend -> MapMessage(
            stringResource(MR.strings.intl_needs_backend, stringResource(MR.strings.settings_course_source)),
            modifier = modifier,
        )

        CampusMapUiState.Failed -> MapMessage(
            stringResource(MR.strings.campus_map_failed),
            modifier = modifier,
            onRetry = actions.onRetry,
        )

        is CampusMapUiState.Loaded -> LoadedMap(state = state, languageTag = languageTag, actions = actions, modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedMap(
    state: CampusMapUiState.Loaded,
    languageTag: String,
    actions: CampusMapActions,
    modifier: Modifier,
) {
    val currentOnSelectPlace by rememberUpdatedState(actions.onSelectPlace)
    val currentOnSelectFood by rememberUpdatedState(actions.onSelectFood)
    val halalLabel = stringResource(MR.strings.campus_map_tag_halal)
    val foodGeoJson = remember(state.visibleFood, halalLabel) { state.visibleFood.toFoodGeoJson(halalLabel) }
    val areasGeoJson = state.areasGeoJson
    val geoJson = remember(state.visiblePlaces, languageTag) { state.visiblePlaces.toGeoJson(languageTag) }
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(LIBERTY_STYLE),
        initialCameraPosition = CameraPosition(
            center = Position(longitude = NCU_LONGITUDE, latitude = NCU_LATITUDE),
            zoom = CAMPUS_ZOOM,
        ),
    ) {
        if (areasGeoJson != null) {
            val areas = rememberGeoJsonSource(GeoJsonData.JsonString(areasGeoJson))
            val kind = feature["kind"].asString()
            val openPlaceOfArea: FeaturesClickHandler = { features ->
                val id = features.firstOrNull()?.properties?.get("place_id")?.jsonPrimitive?.contentOrNull
                if (id == null) {
                    ClickResult.Pass
                } else {
                    currentOnSelectPlace(id)
                    ClickResult.Consume
                }
            }
            LineLayer(
                id = "campus-boundary",
                source = areas,
                filter = kind eq const("campus"),
                color = const(MpcTheme.colors.borderStrong),
                width = const(1.5.dp),
                dasharray = const(listOf(4, 3)),
            )
            FillLayer(
                id = "campus-buildings",
                source = areas,
                filter = kind eq const("building"),
                color = const(MpcTheme.colors.accent),
                opacity = const(0.22f),
                onClick = openPlaceOfArea,
            )
            FillLayer(
                id = "campus-sports",
                source = areas,
                filter = kind eq const("sports"),
                color = const(SPORTS_FILL),
                opacity = const(0.3f),
                onClick = openPlaceOfArea,
            )
            LineLayer(
                id = "campus-outlines",
                source = areas,
                filter = kind neq const("campus"),
                color = const(MpcTheme.colors.accent),
                width = const(1.dp),
            )
        }
        val food = rememberGeoJsonSource(GeoJsonData.JsonString(foodGeoJson))
        val isHalal = feature["halal"].asBoolean()
        val openFood: FeaturesClickHandler = { features ->
            val id = features.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.contentOrNull
            if (id == null) {
                ClickResult.Pass
            } else {
                currentOnSelectFood(id)
                ClickResult.Consume
            }
        }
        CircleLayer(
            id = "food-places",
            source = food,
            filter = isHalal eq const(false),
            radius = const(4.dp),
            color = const(FoodMarkerColors.regular),
            strokeColor = const(Color.White),
            strokeWidth = const(1.dp),
            hitPadding = 10.dp,
            onClick = openFood,
        )
        CircleLayer(
            id = "food-places-halal",
            source = food,
            filter = isHalal eq const(true),
            radius = const(7.dp),
            color = const(FoodMarkerColors.halal),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
            hitPadding = 12.dp,
            onClick = openFood,
        )
        SymbolLayer(
            id = "food-halal-labels",
            source = food,
            filter = isHalal eq const(true),
            textField = format(span(feature["label"].asString())),
            textFont = const(LABEL_FONT),
            textSize = const(0.85f.em),
            textOffset = textOffset(0f.em, 0.9f.em),
            textAnchor = const(SymbolAnchor.Top),
            textColor = const(FoodMarkerColors.halal),
            textHaloColor = const(Color.White),
            textHaloWidth = const(1.5.dp),
        )
        val places = rememberGeoJsonSource(GeoJsonData.JsonString(geoJson))
        CircleLayer(
            id = "campus-places",
            source = places,
            radius = const(8.dp),
            color = feature["color"].convertToColor(),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
            hitPadding = 12.dp,
            onClick = { features ->
                val id = features.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.content
                if (id == null) {
                    ClickResult.Pass
                } else {
                    currentOnSelectPlace(id)
                    ClickResult.Consume
                }
            },
        )
        SymbolLayer(
            id = "campus-place-labels",
            source = places,
            minZoom = LABEL_MIN_ZOOM,
            textField = format(span(feature["label"].asString())),
            textFont = const(LABEL_FONT),
            textSize = const(0.85f.em),
            textOffset = textOffset(0f.em, 0.9f.em),
            textAnchor = const(SymbolAnchor.Top),
            textColor = const(MpcTheme.colors.label),
            textHaloColor = const(Color.White),
            textHaloWidth = const(1.5.dp),
        )
    }

    CameraFollowsState(state, mapState)
    val selected = state.selectedPlace
    val selectedFood = state.selectedFood

    Box(modifier.fillMaxSize()) {
        MaplibreMap(state = mapState, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MpcSpacing.space3, vertical = MpcSpacing.space2),
            verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
        ) {
            SearchField(query = state.query, onSearch = actions.onSearch)
            CategoryChips(state = state, actions = actions)
            if (state.visiblePlaces.isEmpty()) {
                Surface(shape = MpcShapes.badge, color = MpcTheme.colors.surface, shadowElevation = 2.dp) {
                    Text(
                        stringResource(MR.strings.campus_map_no_results),
                        modifier = Modifier.padding(MpcSpacing.space3),
                        style = MpcTheme.typography.supporting,
                        color = MpcTheme.colors.labelSecondary,
                    )
                }
            }
        }
        Text(
            state.shownAttribution.ifBlank { stringResource(MR.strings.campus_map_attribution) },
            // Raised above MapLibre's own logo and tile attribution, which sit on the bottom edge.
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = MpcSpacing.space4, end = MpcSpacing.space4, bottom = 48.dp),
            style = MpcTheme.typography.micro,
            color = MpcTheme.colors.labelSecondary,
            textAlign = TextAlign.Center,
        )
    }

    if (selected != null) {
        ModalBottomSheet(onDismissRequest = { actions.onSelectPlace(null) }) {
            CampusPlaceSheetContent(
                place = selected,
                languageTag = languageTag,
                modifier = Modifier.padding(bottom = MpcSpacing.space6),
                tenants = state.tenantsOf(selected),
                onSelectTenant = actions.onSelectPlace,
            )
        }
    }
    if (selectedFood != null) {
        ModalBottomSheet(onDismissRequest = { actions.onSelectFood(null) }) {
            FoodPlaceSheetContent(place = selectedFood, modifier = Modifier.padding(bottom = MpcSpacing.space6))
        }
    }
}

/** Moves the camera to what the state points at: the open place or restaurant, the halal filter, the search results. */
@Composable
private fun CameraFollowsState(
    state: CampusMapUiState.Loaded,
    mapState: MapState,
) {
    val selected = state.selectedPlace
    LaunchedEffect(selected?.id) {
        selected?.let { place ->
            mapState.animateCamera(
                CameraUpdate(
                    center = Position(longitude = place.location.longitude, latitude = place.location.latitude),
                    zoom = maxOf(mapState.cameraPosition.zoom, PLACE_ZOOM),
                ),
            )
        }
    }

    val certified = state.food?.places.orEmpty().filter { it.halal != null }
    var halalFitStarted by remember { mutableStateOf(false) }
    val halalOnly = state.foodLayer == FoodLayer.HALAL_ONLY
    LaunchedEffect(halalOnly) {
        // The certified places are kilometres away, so turning the filter on moves the camera out to them
        // and turning it off brings it home. The first run is the initial state and moves nothing.
        if (!halalFitStarted) {
            halalFitStarted = true
            return@LaunchedEffect
        }
        if (halalOnly && certified.isNotEmpty()) {
            mapState.fitTo(certified.map { it.location })
        } else if (!halalOnly) {
            mapState.animateCamera(
                CameraUpdate(center = Position(longitude = NCU_LONGITUDE, latitude = NCU_LATITUDE), zoom = CAMPUS_ZOOM),
            )
        }
    }
    LaunchedEffect(state.query) {
        // A match can be anywhere on campus, so the camera follows the results once typing pauses.
        val matches = state.visiblePlaces.map { it.location }
        if (state.query.isBlank() || matches.isEmpty()) return@LaunchedEffect
        delay(SEARCH_SETTLE_MILLIS)
        mapState.fitTo(matches)
    }
    val selectedFood = state.selectedFood
    LaunchedEffect(selectedFood?.id) {
        selectedFood?.let { place ->
            mapState.animateCamera(
                CameraUpdate(center = Position(longitude = place.location.longitude, latitude = place.location.latitude)),
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onSearch: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onSearch,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MpcShapes.card,
        placeholder = { Text(stringResource(MR.strings.campus_map_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onSearch("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(MR.strings.campus_map_clear_search))
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MpcTheme.colors.surface,
            unfocusedContainerColor = MpcTheme.colors.surface,
        ),
    )
}

@Composable
private fun CategoryChips(
    state: CampusMapUiState.Loaded,
    actions: CampusMapActions,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
        if (state.food != null) {
            item {
                FilterChip(
                    selected = state.foodLayer != FoodLayer.HIDDEN,
                    onClick = {
                        actions.onShowFood(if (state.foodLayer == FoodLayer.HIDDEN) FoodLayer.ALL else FoodLayer.HIDDEN)
                    },
                    label = { Text(stringResource(MR.strings.campus_map_food_nearby)) },
                    leadingIcon = { Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            item {
                FilterChip(
                    selected = state.foodLayer == FoodLayer.HALAL_ONLY,
                    onClick = {
                        actions.onShowFood(if (state.foodLayer == FoodLayer.HALAL_ONLY) FoodLayer.ALL else FoodLayer.HALAL_ONLY)
                    },
                    label = { Text(stringResource(MR.strings.campus_map_halal_only)) },
                    leadingIcon = {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = FoodMarkerColors.halal, modifier = Modifier.size(18.dp))
                    },
                )
            }
        }
        item {
            FilterChip(
                selected = state.selectedCategory == null,
                onClick = { actions.onSelectCategory(null) },
                label = { Text(stringResource(MR.strings.campus_map_all)) },
            )
        }
        items(state.categories, key = { it.wire }) { category ->
            val style = category.style
            FilterChip(
                selected = state.selectedCategory == category,
                onClick = { actions.onSelectCategory(category) },
                label = { Text(stringResource(style.label)) },
                leadingIcon = { Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun MapMessage(
    text: String,
    modifier: Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(MpcSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MpcTheme.typography.body, color = MpcTheme.colors.labelSecondary, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(MR.strings.intl_retry)) }
        }
    }
}
