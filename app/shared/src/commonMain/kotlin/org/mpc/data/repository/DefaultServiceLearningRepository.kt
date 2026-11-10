package org.mpc.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.mpc.data.dataSource.IntlDataSource
import org.mpc.domain.model.SourcedRules
import org.mpc.domain.model.toSourcedRules
import org.mpc.domain.repository.BackendEndpointRepository
import org.mpc.domain.repository.ServiceLearningRepository

@ContributesBinding(AppScope::class)
@Inject
internal class DefaultServiceLearningRepository(
    private val backendEndpoint: BackendEndpointRepository,
    private val dataSource: IntlDataSource,
) : ServiceLearningRepository {
    override val backend: Flow<String?> = backendEndpoint.baseUrl

    override suspend fun fetchRules(): SourcedRules? = backendEndpoint.baseUrl.first()?.let { baseUrl -> dataSource.getServiceLearning(baseUrl).toSourcedRules() }
}
