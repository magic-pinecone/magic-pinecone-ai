package org.mpc.presentation.viewModel

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mpc.domain.model.Club
import org.mpc.domain.repository.ClubRepository
import org.mpc.presentation.state.ClubUiState
import kotlin.test.assertEquals

private fun club(
    id: Int,
    category: String,
) = Club(
    id = id,
    categoryName = category,
    name = "社團$id",
    nameEn = "",
    description = "",
    address = "",
    website = "",
    pictureUrl = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
private fun clubTest(
    fetch: suspend () -> List<Club>?,
    body: suspend TestScope.(ClubViewModel) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val repository = object : ClubRepository {
            override suspend fun fetchClubs(): List<Club>? = fetch()
        }
        val viewModel = ClubViewModel(repository)
        testScheduler.advanceUntilIdle()
        body(viewModel)
    } finally {
        Dispatchers.resetMain()
    }
}

val clubViewModelTests by testSuite {
    test("without a backend the screen asks for one") {
        clubTest(fetch = { null }) { viewModel ->
            assertEquals(ClubUiState.NoBackend, viewModel.uiState.value)
        }
    }

    test("clubs are grouped by category in the backend's order") {
        val clubs = listOf(club(1, "聯誼性"), club(2, "聯誼性"), club(3, "學術性"))
        clubTest(fetch = { clubs }) { viewModel ->
            assertEquals(
                ClubUiState.Loaded(
                    listOf(
                        "聯誼性" to listOf(clubs[0], clubs[1]),
                        "學術性" to listOf(clubs[2]),
                    ),
                ),
                viewModel.uiState.value,
            )
        }
    }

    test("a backend error shows the failure state") {
        clubTest(fetch = { error("connection refused") }) { viewModel ->
            assertEquals(ClubUiState.Failed, viewModel.uiState.value)
        }
    }
}
