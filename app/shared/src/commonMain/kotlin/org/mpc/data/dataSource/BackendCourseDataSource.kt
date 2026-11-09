package org.mpc.data.dataSource

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import org.mpc.data.dto.CourseDetailDto
import org.mpc.data.dto.CourseResultDto

/**
 * Reads the catalog endpoints of a Magic Pinecone backend. They answer with the same JSON as the
 * static catalog, so both sources share their DTOs.
 */
@Inject
internal class BackendCourseDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun getAllCourses(
        baseUrl: String,
        semester: String,
    ): CourseResultDto = httpClient
        .get("$baseUrl/api/v1/catalog/$semester/courses") {
            // An error body is JSON too; fail on the status instead of on a missing field.
            expectSuccess = true
        }.body<CourseResultDto>()

    suspend fun getCourseDetails(
        baseUrl: String,
        semester: String,
        serialNo: String,
    ): CourseDetailDto = httpClient
        .get("$baseUrl/api/v1/catalog/$semester/courses/$serialNo/detail") {
            expectSuccess = true
        }.body<CourseDetailDto>()
}
