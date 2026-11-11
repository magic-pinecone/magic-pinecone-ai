package org.mpc.presentation.viewModel

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mpc.domain.model.CampusPlace
import org.mpc.domain.model.CampusPlaces
import org.mpc.domain.model.GeoPoint
import org.mpc.domain.model.LocalizedText
import org.mpc.domain.model.MapLinks
import org.mpc.domain.model.PlaceCategory
import org.mpc.domain.repository.CampusPlacesRepository
import org.mpc.presentation.state.CampusMapUiState
import kotlin.test.assertEquals
import kotlin.test.assertIs

private fun place(
    id: String,
    name: String,
    category: PlaceCategory,
) = CampusPlace(
    id = id,
    name = LocalizedText(name, name),
    code = null,
    category = category,
    location = GeoPoint(24.968, 121.192),
    osmId = null,
    aliases = emptyList(),
    description = null,
    openingHours = null,
    priceRange = null,
    priceNote = null,
    tags = emptySet(),
    mapLinks = MapLinks("g", "w", "a"),
)

private val places = CampusPlaces(
    attribution = "© OpenStreetMap contributors",
    places = listOf(
        place("canteen", "Pine Canteen", PlaceCategory.DINING),
        place("cafe", "Pine Cafe", PlaceCategory.DINING),
        place("library", "Main Library", PlaceCategory.LIBRARY),
    ),
)

private class BackendPlaces(
    backend: String?,
    var failure: Throwable? = null,
) : CampusPlacesRepository {
    override val backend = MutableStateFlow(backend)

    override suspend fun fetchPlaces(): CampusPlaces? {
        failure?.let { throw it }
        return places.takeIf { backend.value != null }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun campusMapTest(
    backend: String?,
    failure: Throwable? = null,
    body: suspend TestScope.(CampusMapViewModel, BackendPlaces) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val repository = BackendPlaces(backend, failure)
        val viewModel = CampusMapViewModel(repository)
        testScheduler.advanceUntilIdle()
        body(viewModel, repository)
    } finally {
        Dispatchers.resetMain()
    }
}

private fun CampusMapViewModel.loaded() = assertIs<CampusMapUiState.Loaded>(uiState.value)

@OptIn(ExperimentalCoroutinesApi::class)
val campusMapViewModelTests by testSuite {
    test("without a backend the screen asks for one") {
        campusMapTest(backend = null) { viewModel, _ ->
            assertEquals(CampusMapUiState.NeedsBackend, viewModel.uiState.value)
        }
    }

    test("a failed load shows the error and retrying loads the places") {
        campusMapTest(backend = "http://10.0.2.2:8000", failure = IllegalStateException("down")) { viewModel, repository ->
            assertEquals(CampusMapUiState.Failed, viewModel.uiState.value)

            repository.failure = null
            viewModel.refresh()
            testScheduler.advanceUntilIdle()

            assertEquals(3, viewModel.loaded().visiblePlaces.size)
        }
    }

    test("the category chips and the search narrow the places on the map") {
        campusMapTest(backend = "http://10.0.2.2:8000") { viewModel, _ ->
            assertEquals(listOf(PlaceCategory.LIBRARY, PlaceCategory.DINING), viewModel.loaded().categories)

            viewModel.selectCategory(PlaceCategory.DINING)
            testScheduler.advanceUntilIdle()
            assertEquals(listOf("canteen", "cafe"), viewModel.loaded().visiblePlaces.map { it.id })

            viewModel.search("cafe")
            testScheduler.advanceUntilIdle()
            assertEquals(listOf("cafe"), viewModel.loaded().visiblePlaces.map { it.id })

            viewModel.selectCategory(null)
            viewModel.search("library")
            testScheduler.advanceUntilIdle()
            assertEquals(listOf("library"), viewModel.loaded().visiblePlaces.map { it.id })
        }
    }

    test("selecting a place opens it and null closes it") {
        campusMapTest(backend = "http://10.0.2.2:8000") { viewModel, _ ->
            viewModel.selectPlace("cafe")
            testScheduler.advanceUntilIdle()
            assertEquals("Pine Cafe", viewModel.loaded().selectedPlace?.name?.en)

            viewModel.selectPlace(null)
            testScheduler.advanceUntilIdle()
            assertEquals(null, viewModel.loaded().selectedPlace)
        }
    }
}
