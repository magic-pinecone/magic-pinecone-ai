package org.mpc.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.mpc.data.dataSource.CampusPlacesDataSource
import org.mpc.data.mapper.dto.toDomain
import org.mpc.domain.model.CampusPlaces
import org.mpc.domain.repository.BackendEndpointRepository
import org.mpc.domain.repository.CampusPlacesRepository

@ContributesBinding(AppScope::class)
@Inject
internal class DefaultCampusPlacesRepository(
    private val backendEndpoint: BackendEndpointRepository,
    private val dataSource: CampusPlacesDataSource,
) : CampusPlacesRepository {
    override val backend: Flow<String?> = backendEndpoint.baseUrl

    override suspend fun fetchPlaces(): CampusPlaces? = backendEndpoint.baseUrl.first()?.let { baseUrl -> dataSource.getPlaces(baseUrl).toDomain() }
}
