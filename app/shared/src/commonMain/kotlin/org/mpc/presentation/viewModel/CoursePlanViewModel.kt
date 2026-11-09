package org.mpc.presentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mpc.domain.model.CoursePlan
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.repository.CoursePlanRepository
import org.mpc.presentation.state.CoursePlanDraftStore
import org.mpc.presentation.state.CoursePlanUiState

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class CoursePlanViewModel(
    private val coursePlanRepository: CoursePlanRepository,
    private val draftStore: CoursePlanDraftStore,
) : ViewModel() {
    // TODO: stop hard code semester and load plan from app storage

    private val semester = "115-1"

    val uiState = draftStore.uiState

    private val saveResults = Channel<Boolean>(capacity = Channel.BUFFERED)
    val saveResult = saveResults.receiveAsFlow()

    private val saveRequests = Channel<CoursePlan>(capacity = Channel.CONFLATED)

    init {
        loadPlan()
        collectSaveRequests()
    }

    private fun loadPlan() {
        if (!draftStore.shouldLoad(semester)) {
            return
        }

        viewModelScope.launch {
            runCatching {
                coursePlanRepository.loadPlan(semester)
            }.onSuccess(draftStore::acceptLoadedPlan)
                .onFailure { cause ->
                    cause.rethrowIfCancellationOrFatal()
                    draftStore.acceptLoadFailure(cause)
                }
        }
    }

    fun toggleCourse(course: CourseSummary) {
        draftStore.toggleCourse(course)
    }

    fun savePlan() {
        val plan = (uiState.value as? CoursePlanUiState.Success)?.plan
            ?: return

        saveRequests.trySend(plan)
    }

    private fun collectSaveRequests() {
        viewModelScope.launch {
            for (plan in saveRequests) {
                runCatching {
                    withContext(NonCancellable) {
                        var pendingPlan: CoursePlan? = plan

                        while (pendingPlan != null) {
                            coursePlanRepository.savePlan(pendingPlan)
                            pendingPlan = saveRequests.tryReceive().getOrNull()
                        }
                    }
                }.onSuccess {
                    saveResults.trySend(true)
                }.onFailure { cause ->
                    cause.rethrowIfCancellationOrFatal()
                    Logger.e(cause.toString())
                    saveResults.trySend(false)
                }
            }
        }
    }
}
