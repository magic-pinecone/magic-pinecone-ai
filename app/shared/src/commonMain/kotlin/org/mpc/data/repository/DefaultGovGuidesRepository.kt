package org.mpc.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.mpc.data.dataSource.IntlDataSource
import org.mpc.domain.model.GovGuides
import org.mpc.domain.repository.BackendEndpointRepository
import org.mpc.domain.repository.GovGuidesRepository

@ContributesBinding(AppScope::class)
@Inject
internal class DefaultGovGuidesRepository(
    private val backendEndpoint: BackendEndpointRepository,
    private val dataSource: IntlDataSource,
) : GovGuidesRepository {
    override val backend: Flow<String?> = backendEndpoint.baseUrl

    override suspend fun fetchGuides(): GovGuides? = backendEndpoint.baseUrl.first()?.let { baseUrl -> dataSource.getGuides(baseUrl) }
}
