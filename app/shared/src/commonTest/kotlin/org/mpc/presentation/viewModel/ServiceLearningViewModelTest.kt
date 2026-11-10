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
import org.mpc.domain.model.LocalizedText
import org.mpc.domain.model.RuleContact
import org.mpc.domain.model.SourcedRules
import org.mpc.domain.repository.ServiceLearningRepository
import org.mpc.presentation.state.SourcedRulesUiState
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val rules = SourcedRules(
    contact = RuleContact(LocalizedText("", ""), website = "", sources = emptyList()),
    rules = emptyList(),
    openQuestions = emptyList(),
)

/** Serves the rules only while a backend is set, and counts how often it was asked. */
private class BackendRules(
    backend: String?,
    var failure: Throwable? = null,
) : ServiceLearningRepository {
    override val backend = MutableStateFlow(backend)
    var fetches = 0

    override suspend fun fetchRules(): SourcedRules? {
        fetches++
        failure?.let { throw it }
        return rules.takeIf { backend.value != null }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun serviceLearningTest(
    backend: String?,
    body: suspend TestScope.(ServiceLearningViewModel, BackendRules) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val repository = BackendRules(backend)
        val viewModel = ServiceLearningViewModel(repository)
        testScheduler.advanceUntilIdle()
        body(viewModel, repository)
    } finally {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
val serviceLearningViewModelTests by testSuite {
    test("without a backend the screen asks for one") {
        serviceLearningTest(backend = null) { viewModel, _ ->
            assertEquals(SourcedRulesUiState.NeedsBackend, viewModel.uiState.value)
        }
    }

    test("setting a backend later loads the rules without a restart") {
        serviceLearningTest(backend = null) { viewModel, repository ->
            repository.backend.value = "http://10.0.2.2:8000"
            testScheduler.advanceUntilIdle()

            assertEquals(SourcedRulesUiState.Loaded(rules), viewModel.uiState.value)
            assertEquals(2, repository.fetches)
        }
    }

    test("a failed load can be retried") {
        serviceLearningTest(backend = "http://10.0.2.2:8000") { viewModel, repository ->
            repository.failure = IllegalStateException("down")
            viewModel.refresh()
            testScheduler.advanceUntilIdle()
            assertEquals(SourcedRulesUiState.Failed, viewModel.uiState.value)

            repository.failure = null
            viewModel.refresh()
            testScheduler.advanceUntilIdle()
            assertIs<SourcedRulesUiState.Loaded>(viewModel.uiState.value)
        }
    }
}
