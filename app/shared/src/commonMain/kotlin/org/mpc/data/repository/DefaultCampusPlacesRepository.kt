package org.mpc.data.repository

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
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

    override suspend fun fetchPlaces(): CampusPlaces? {
        val baseUrl = backendEndpoint.baseUrl.first() ?: return null
        val places = dataSource.getPlaces(baseUrl).toDomain()
        // The shapes only decorate the markers, so a failure here must not hide the places.
        val areas = try {
            dataSource.getAreasGeoJson(baseUrl)
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            Logger.i(cause.toString())
            null
        }
        return places.copy(areasGeoJson = areas)
    }
}
