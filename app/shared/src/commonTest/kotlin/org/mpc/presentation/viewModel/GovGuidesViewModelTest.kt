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
import org.mpc.domain.model.GovGuideArticle
import org.mpc.domain.model.GovGuides
import org.mpc.domain.repository.GovGuidesRepository
import org.mpc.presentation.state.GovGuidesUiState
import kotlin.test.assertEquals

private val guides = GovGuides(
    topics = listOf("work"),
    articles = listOf(
        GovGuideArticle(id = "a", title = "A", org = "MOL", lang = "zh", topic = "work", url = "https://example.gov.tw"),
    ),
)

/** Serves the guides only while a backend is set, and counts how often it was asked. */
private class BackendGuides(
    backend: String?,
) : GovGuidesRepository {
    override val backend = MutableStateFlow(backend)
    var failure: Throwable? = null
    var fetches = 0
    val languages = mutableListOf<String?>()

    override suspend fun fetchGuides(language: String?): GovGuides? {
        fetches++
        languages += language
        failure?.let { throw it }
        return guides.takeIf { backend.value != null }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun govGuidesTest(
    backend: String?,
    body: suspend TestScope.(GovGuidesViewModel, BackendGuides) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val repository = BackendGuides(backend)
        val viewModel = GovGuidesViewModel(repository)
        testScheduler.advanceUntilIdle()
        body(viewModel, repository)
    } finally {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
val govGuidesViewModelTests by testSuite {
    test("without a backend the screen asks for one") {
        govGuidesTest(backend = null) { viewModel, _ ->
            assertEquals(GovGuidesUiState.NeedsBackend, viewModel.uiState.value)
        }
    }

    test("setting a backend later loads the guides") {
        govGuidesTest(backend = null) { viewModel, repository ->
            repository.backend.value = "http://10.0.2.2:8000"
            testScheduler.advanceUntilIdle()

            assertEquals(GovGuidesUiState.Loaded(guides), viewModel.uiState.value)
            assertEquals(2, repository.fetches)
        }
    }

    test("choosing a language reloads the guides in it, once") {
        govGuidesTest(backend = "http://10.0.2.2:8000") { viewModel, repository ->
            viewModel.setLanguage("vi")
            viewModel.setLanguage("vi")
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(null, "vi"), repository.languages)
            assertEquals(GovGuidesUiState.Loaded(guides), viewModel.uiState.value)
        }
    }

    test("a failed load shows the retry state, and retrying recovers") {
        govGuidesTest(backend = "http://10.0.2.2:8000") { viewModel, repository ->
            repository.failure = IllegalStateException("offline")
            viewModel.refresh()
            testScheduler.advanceUntilIdle()
            assertEquals(GovGuidesUiState.Failed, viewModel.uiState.value)

            repository.failure = null
            viewModel.refresh()
            testScheduler.advanceUntilIdle()
            assertEquals(GovGuidesUiState.Loaded(guides), viewModel.uiState.value)
        }
    }
}
