package org.mpc.presentation.viewModel

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import org.mpc.domain.repository.ServiceLearningRepository

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class ServiceLearningViewModel(
    repository: ServiceLearningRepository,
) : SourcedRulesViewModel(repository)
