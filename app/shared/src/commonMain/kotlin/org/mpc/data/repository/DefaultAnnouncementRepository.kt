package org.mpc.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mpc.data.dataSource.BackendAnnouncementDataSource
import org.mpc.data.mapper.dto.toDomain
import org.mpc.domain.model.AnnouncementFeed
import org.mpc.domain.repository.AnnouncementRepository
import org.mpc.domain.repository.BackendEndpointRepository

class BackendNotConfiguredException : IllegalStateException("Backend endpoint is not configured")

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
internal class DefaultAnnouncementRepository(
    private val backendDataSource: BackendAnnouncementDataSource,
    private val backendEndpoint: BackendEndpointRepository,
) : AnnouncementRepository {
    private val cacheMutex = Mutex()
    private var cachedBaseUrl: String? = null
    private var cachedFeed: AnnouncementFeed? = null

    override suspend fun fetchAnnouncements(forceRefresh: Boolean): AnnouncementFeed = cacheMutex.withLock {
        val baseUrl = backendEndpoint.baseUrl.first() ?: throw BackendNotConfiguredException()
        val currentCached = cachedFeed
        if (!forceRefresh && cachedBaseUrl == baseUrl && currentCached != null) {
            return currentCached
        }
        val fresh = backendDataSource.getAnnouncements(baseUrl = baseUrl).toDomain()
        cachedBaseUrl = baseUrl
        cachedFeed = fresh
        fresh
    }
}
