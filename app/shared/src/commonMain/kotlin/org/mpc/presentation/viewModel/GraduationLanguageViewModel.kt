package org.mpc.presentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mpc.domain.model.Degree
import org.mpc.domain.model.GraduationLanguage
import org.mpc.domain.model.collegesFor
import org.mpc.domain.model.rulesFor
import org.mpc.domain.repository.GraduationLanguageRepository
import org.mpc.presentation.state.GraduationLanguageFilter
import org.mpc.presentation.state.GraduationLanguageUiState

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class GraduationLanguageViewModel(
    private val repository: GraduationLanguageRepository,
) : ViewModel() {
    private sealed interface Load {
        data object Loading : Load

        data object NeedsBackend : Load

        data object Failed : Load

        data class Done(val requirements: GraduationLanguage) : Load
    }

    private val load = MutableStateFlow<Load>(Load.Loading)
    private val filter = MutableStateFlow(GraduationLanguageFilter())

    val uiState: StateFlow<GraduationLanguageUiState> = combine(load, filter) { load, filter ->
        when (load) {
            Load.Loading -> GraduationLanguageUiState.Loading

            Load.NeedsBackend -> GraduationLanguageUiState.NeedsBackend

            Load.Failed -> GraduationLanguageUiState.Failed

            is Load.Done -> GraduationLanguageUiState.Loaded(
                requirements = load.requirements,
                filter = filter,
                colleges = load.requirements.collegesFor(filter.degree),
                rules = load.requirements.rulesFor(filter.degree, filter.college, filter.cohort),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = GraduationLanguageUiState.Loading,
    )

    init {
        refresh()
    }

    fun refresh() {
        load.value = Load.Loading
        viewModelScope.launch {
            load.value = runCatching { repository.fetchRequirements() }
                .fold(
                    onSuccess = { requirements -> requirements?.let(Load::Done) ?: Load.NeedsBackend },
                    onFailure = { cause ->
                        cause.rethrowIfCancellationOrFatal()
                        Logger.i(cause.toString())
                        Load.Failed
                    },
                )
        }
    }

    /** Selects [degree] and clears the college, because each degree has its own set of colleges. */
    fun selectDegree(degree: Degree) = filter.update { it.copy(degree = degree, college = null) }

    /** Selects the college with Chinese name [college], or every college when null. */
    fun selectCollege(college: String?) = filter.update { it.copy(college = college) }

    fun selectCohort(cohort: Int) = filter.update { it.copy(cohort = cohort.coerceIn(GraduationLanguageFilter.Cohorts)) }
}
