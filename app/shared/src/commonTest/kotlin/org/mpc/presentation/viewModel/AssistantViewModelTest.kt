package org.mpc.presentation.viewModel

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.IOException
import org.mpc.domain.model.AssistantAnswer
import org.mpc.domain.repository.AssistantRepository
import org.mpc.presentation.state.AssistantUiState
import kotlin.test.assertEquals

private const val FIRST_BACKEND = "http://10.0.2.2:8000"
private const val SECOND_BACKEND = "http://10.0.2.2:9000"

/** Answers only while a backend is set. The address is a shared flow so a test can emit the same one twice. */
private class ScriptedAssistant(
    backend: String?,
) : AssistantRepository {
    override val backend = MutableSharedFlow<String?>(replay = 1).apply { tryEmit(backend) }
    var failure: Throwable? = null
    val asked = mutableListOf<String>()

    override suspend fun ask(question: String): AssistantAnswer? {
        asked += question
        failure?.let { throw it }
        return backend.replayCache.single()?.let { AssistantAnswer(question = question, answer = "- yes [1]") }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun assistantTest(
    backend: String?,
    body: suspend TestScope.(AssistantViewModel, ScriptedAssistant) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val repository = ScriptedAssistant(backend)
        val viewModel = AssistantViewModel(repository)
        testScheduler.advanceUntilIdle()
        body(viewModel, repository)
    } finally {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
val assistantViewModelTests by testSuite {
    test("nothing is asked until the user asks, and a blank question is ignored") {
        assistantTest(FIRST_BACKEND) { viewModel, repository ->
            viewModel.ask("   ")
            testScheduler.advanceUntilIdle()

            assertEquals(AssistantUiState.Idle, viewModel.uiState.value)
            assertEquals(emptyList(), repository.asked)
        }
    }

    test("a question shows as being asked, then its answer") {
        assistantTest(FIRST_BACKEND) { viewModel, repository ->
            viewModel.ask(" Where is E6? ")
            assertEquals(AssistantUiState.Asking, viewModel.uiState.value)
            testScheduler.advanceUntilIdle()

            assertEquals(listOf("Where is E6?"), repository.asked)
            assertEquals(
                AssistantUiState.Answered(AssistantAnswer(question = "Where is E6?", answer = "- yes [1]")),
                viewModel.uiState.value,
            )
        }
    }

    test("without a backend the screen asks for one, and the address arriving again does not wipe that") {
        assistantTest(backend = null) { viewModel, repository ->
            viewModel.ask("Where is E6?")
            testScheduler.advanceUntilIdle()
            assertEquals(AssistantUiState.NeedsBackend, viewModel.uiState.value)

            repository.backend.emit(null)
            testScheduler.advanceUntilIdle()

            assertEquals(AssistantUiState.NeedsBackend, viewModel.uiState.value)
        }
    }

    test("setting a backend clears the message, and the same question can then be asked again") {
        assistantTest(backend = null) { viewModel, repository ->
            viewModel.ask("Where is E6?")
            testScheduler.advanceUntilIdle()

            repository.backend.emit(FIRST_BACKEND)
            testScheduler.advanceUntilIdle()
            assertEquals(AssistantUiState.Idle, viewModel.uiState.value)

            viewModel.ask("Where is E6?")
            testScheduler.advanceUntilIdle()
            assertEquals(
                AssistantUiState.Answered(AssistantAnswer(question = "Where is E6?", answer = "- yes [1]")),
                viewModel.uiState.value,
            )
        }
    }

    test("a failure is shown until the address changes, and an answer is never cleared by it") {
        assistantTest(FIRST_BACKEND) { viewModel, repository ->
            repository.failure = IOException("unreachable")
            viewModel.ask("Where is E6?")
            testScheduler.advanceUntilIdle()
            assertEquals(AssistantUiState.Failed, viewModel.uiState.value)

            repository.backend.emit(SECOND_BACKEND)
            testScheduler.advanceUntilIdle()
            assertEquals(AssistantUiState.Idle, viewModel.uiState.value)

            repository.failure = null
            viewModel.ask("Where is E6?")
            testScheduler.advanceUntilIdle()
            val answered = viewModel.uiState.value
            repository.backend.emit(FIRST_BACKEND)
            testScheduler.advanceUntilIdle()

            assertEquals(answered, viewModel.uiState.value)
            assertEquals(AssistantUiState.Answered(AssistantAnswer(question = "Where is E6?", answer = "- yes [1]")), answered)
        }
    }
}
