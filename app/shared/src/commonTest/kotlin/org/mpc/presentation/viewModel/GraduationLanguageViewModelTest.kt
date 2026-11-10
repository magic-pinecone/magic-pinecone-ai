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
import org.mpc.domain.model.ChineseRequirements
import org.mpc.domain.model.GraduationLanguage
import org.mpc.domain.model.GraduationLanguageGeneral
import org.mpc.domain.model.LocalizedText
import org.mpc.domain.repository.GraduationLanguageRepository
import org.mpc.presentation.state.GraduationLanguageUiState
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val requirements = GraduationLanguage(
    verifiedOn = "2026-11-10",
    disclaimer = LocalizedText("", ""),
    tests = emptyMap(),
    general = GraduationLanguageGeneral(summary = LocalizedText("", "")),
    chinese = ChineseRequirements(summary = LocalizedText("", "")),
    rules = emptyList(),
    sources = emptyList(),
)

/** Serves the requirements only while a backend is set, and counts how often it was asked. */
private class BackendRequirements(
    backend: String?,
    var failure: Throwable? = null,
) : GraduationLanguageRepository {
    override val backend = MutableStateFlow(backend)
    var fetches = 0

    override suspend fun fetchRequirements(): GraduationLanguage? {
        fetches++
        failure?.let { throw it }
        return requirements.takeIf { backend.value != null }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun graduationLanguageTest(
    backend: String?,
    body: suspend TestScope.(GraduationLanguageViewModel, BackendRequirements) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val repository = BackendRequirements(backend)
        val viewModel = GraduationLanguageViewModel(repository)
        testScheduler.advanceUntilIdle()
        body(viewModel, repository)
    } finally {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
val graduationLanguageViewModelTests by testSuite {
    test("without a backend the screen asks for one") {
        graduationLanguageTest(backend = null) { viewModel, _ ->
            assertEquals(GraduationLanguageUiState.NeedsBackend, viewModel.uiState.value)
        }
    }

    test("setting a backend later loads the requirements without a restart") {
        graduationLanguageTest(backend = null) { viewModel, repository ->
            repository.backend.value = "http://10.0.2.2:8000"
            testScheduler.advanceUntilIdle()

            assertIs<GraduationLanguageUiState.Loaded>(viewModel.uiState.value)
            assertEquals(2, repository.fetches)
        }
    }

    test("clearing the backend goes back to asking for one") {
        graduationLanguageTest(backend = "http://10.0.2.2:8000") { viewModel, repository ->
            assertIs<GraduationLanguageUiState.Loaded>(viewModel.uiState.value)

            repository.backend.value = null
            testScheduler.advanceUntilIdle()

            assertEquals(GraduationLanguageUiState.NeedsBackend, viewModel.uiState.value)
        }
    }

    test("a failed load can be retried") {
        graduationLanguageTest(backend = "http://10.0.2.2:8000") { viewModel, repository ->
            repository.failure = IllegalStateException("down")
            viewModel.refresh()
            testScheduler.advanceUntilIdle()
            assertEquals(GraduationLanguageUiState.Failed, viewModel.uiState.value)

            repository.failure = null
            viewModel.refresh()
            testScheduler.advanceUntilIdle()
            assertIs<GraduationLanguageUiState.Loaded>(viewModel.uiState.value)
        }
    }
}
