package org.mpc.data.dataSource

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.mpc.data.dto.AnnouncementListResponseDto
import org.mpc.data.dto.AnnouncementScopeDto
import org.mpc.data.dto.UserAffiliationDto

private const val DEFAULT_ANNOUNCEMENT_LIMIT = 500

/** Reads campus affiliation and department announcements from the FastAPI backend (`/api/v1/announcements`). */
@Inject
internal class BackendAnnouncementDataSource(
    private val httpClient: HttpClient,
) {
    suspend fun getAnnouncements(
        baseUrl: String,
        scope: AnnouncementScopeDto? = null,
        affiliation: UserAffiliationDto? = null,
        departmentName: String? = null,
        limit: Int = DEFAULT_ANNOUNCEMENT_LIMIT,
    ): AnnouncementListResponseDto = httpClient
        .get("$baseUrl/api/v1/announcements") {
            expectSuccess = true
            scope?.let { parameter("scope", it.name) }
            affiliation?.let { parameter("affiliation", it.name) }
            departmentName?.takeIf { it.isNotBlank() }?.let { parameter("department_name", it) }
            parameter("limit", limit)
        }.body()
}
