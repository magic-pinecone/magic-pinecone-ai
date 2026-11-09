package org.mpc.data.dataSource

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.mpc.data.dto.CourseDetailDto
import org.mpc.data.dto.CourseResultDto

@ContributesBinding(AppScope::class)
@Inject
internal class StaticRemoteCourseDataSource(
    private val httpClient: HttpClient,
) : CourseDataSource {
    override suspend fun getAllCourses(semester: String): CourseResultDto {
        // TODO: verify semester
        return httpClient
            .get("https://raw.githubusercontent.com/magic-pinecone/magic-pinecone-lite/refs/heads/$semester/courses.json")
            .body<CourseResultDto>()
    }

    override suspend fun getCourseDetails(
        semester: String,
        serialNo: String,
    ): CourseDetailDto {
        // TODO: verify semester and serialNo
        return httpClient
            .get(
                "https://raw.githubusercontent.com/magic-pinecone/magic-pinecone-lite/refs/heads/$semester/detail/$serialNo.json",
            ).body<CourseDetailDto>()
    }
}
