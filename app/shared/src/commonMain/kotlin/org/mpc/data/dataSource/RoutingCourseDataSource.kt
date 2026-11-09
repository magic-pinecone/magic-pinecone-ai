package org.mpc.data.dataSource

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import org.mpc.data.dto.CourseDetailDto
import org.mpc.data.dto.CourseResultDto
import org.mpc.domain.repository.BackendEndpointRepository

/** Reads from the configured backend, or from the static catalog while none is configured. */
@ContributesBinding(AppScope::class)
@Inject
internal class RoutingCourseDataSource(
    private val staticSource: StaticRemoteCourseDataSource,
    private val backendSource: BackendCourseDataSource,
    private val backendEndpoint: BackendEndpointRepository,
) : CourseDataSource {
    override suspend fun getAllCourses(semester: String): CourseResultDto = when (val baseUrl = backendEndpoint.baseUrl.first()) {
        null -> staticSource.getAllCourses(semester)
        else -> backendSource.getAllCourses(baseUrl, semester)
    }

    override suspend fun getCourseDetails(
        semester: String,
        serialNo: String,
    ): CourseDetailDto = when (val baseUrl = backendEndpoint.baseUrl.first()) {
        null -> staticSource.getCourseDetails(semester, serialNo)
        else -> backendSource.getCourseDetails(baseUrl, semester, serialNo)
    }
}
