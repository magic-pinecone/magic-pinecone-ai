package org.mpc.presentation.viewModel

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
    category = "A",
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
    fetch: suspend (language: String?) -> List<Club>?,
    body: suspend TestScope.(ClubViewModel) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val repository = object : ClubRepository {
            override suspend fun fetchClubs(language: String?): List<Club>? = fetch(language)
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
        clubTest(fetch = { _ -> null }) { viewModel ->
            assertEquals(ClubUiState.NoBackend, viewModel.uiState.value)
        }
    }

    test("clubs are grouped by category in the backend's order") {
        val clubs = listOf(club(1, "聯誼性"), club(2, "聯誼性"), club(3, "學術性"))
        clubTest(fetch = { _ -> clubs }) { viewModel ->
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
        clubTest(fetch = { _ -> error("connection refused") }) { viewModel ->
            assertEquals(ClubUiState.Failed, viewModel.uiState.value)
        }
    }

    test("choosing a language reloads the clubs in it, once") {
        val asked = mutableListOf<String?>()
        clubTest(fetch = { language -> asked += language; listOf(club(1, language ?: "device")) }) { viewModel ->
            viewModel.setLanguage("vi")
            viewModel.setLanguage("vi")
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(null, "vi"), asked)
            assertEquals(ClubUiState.Loaded(listOf("vi" to listOf(club(1, "vi")))), viewModel.uiState.value)
        }
    }

    test("a slower answer for the previous language does not replace the new one") {
        clubTest(
            fetch = { language ->
                if (language == "en") delay(1_000)
                listOf(club(1, language ?: "device"))
            },
        ) { viewModel ->
            viewModel.setLanguage("en")
            testScheduler.runCurrent()
            viewModel.setLanguage("vi")
            testScheduler.advanceUntilIdle()

            assertEquals(ClubUiState.Loaded(listOf("vi" to listOf(club(1, "vi")))), viewModel.uiState.value)
        }
    }
}
