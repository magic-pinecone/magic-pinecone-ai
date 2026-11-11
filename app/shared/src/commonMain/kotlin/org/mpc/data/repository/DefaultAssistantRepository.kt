package org.mpc.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.mpc.data.dataSource.AssistantDataSource
import org.mpc.domain.model.AssistantAnswer
import org.mpc.domain.repository.AssistantRepository
import org.mpc.domain.repository.BackendEndpointRepository

@ContributesBinding(AppScope::class)
@Inject
internal class DefaultAssistantRepository(
    private val backendEndpoint: BackendEndpointRepository,
    private val dataSource: AssistantDataSource,
) : AssistantRepository {
    override val backend: Flow<String?> = backendEndpoint.baseUrl

    override suspend fun ask(question: String): AssistantAnswer? = backendEndpoint.baseUrl.first()?.let { baseUrl ->
        dataSource.ask(baseUrl, question)
    }
}
