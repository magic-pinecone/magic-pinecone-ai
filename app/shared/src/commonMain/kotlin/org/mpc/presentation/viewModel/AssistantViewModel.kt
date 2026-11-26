package org.mpc.presentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mpc.domain.repository.AssistantRepository
import org.mpc.presentation.state.AssistantUiState

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class AssistantViewModel(
    private val repository: AssistantRepository,
) : ViewModel() {
    private val state = MutableStateFlow<AssistantUiState>(AssistantUiState.Idle)
    val uiState: StateFlow<AssistantUiState> = state.asStateFlow()

    private var asking: Job? = null

    /** The backend the message on screen is about, while that message is "set a backend" or "could not reach it". */
    private var messageAbout: MessageAbout? = null

    init {
        // Those two messages stop being true once the address changes. Only a different address clears them: the
        // address being re-emitted, or arriving late from storage, must not wipe a message the user has just got.
        viewModelScope.launch {
            repository.backend.collect { backend ->
                val about = messageAbout
                if (about != null && about.backend != backend) {
                    messageAbout = null
                    state.value = AssistantUiState.Idle
                }
            }
        }
    }

    fun ask(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return
        // Cancelling the previous question keeps its slower answer from replacing this one's.
        asking?.cancel()
        messageAbout = null
        state.value = AssistantUiState.Asking
        asking = viewModelScope.launch {
            val backend = repository.backend.first()
            val outcome = runCatching { repository.ask(trimmed) }
                .fold(
                    onSuccess = { answer -> answer?.let(AssistantUiState::Answered) ?: AssistantUiState.NeedsBackend },
                    onFailure = { cause ->
                        cause.rethrowIfCancellationOrFatal()
                        Logger.i(cause.toString())
                        AssistantUiState.Failed
                    },
                )
            if (outcome !is AssistantUiState.Answered) messageAbout = MessageAbout(backend)
            state.value = outcome
        }
    }

    private class MessageAbout(val backend: String?)
}
