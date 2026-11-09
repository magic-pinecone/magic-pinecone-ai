package org.mpc.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
internal data class AnnouncementListResponseDto(
    val lastUpdated: Instant,
    val total: Int,
    val offset: Int,
    val limit: Int,
    val announcements: List<AnnouncementDto>,
)

@Serializable
internal data class AnnouncementDto(
    val announcementId: String,
    val scope: AnnouncementScopeDto,
    val sourceId: String,
    val category: String,
    val unitName: String,
    val collegeId: String? = null,
    val collegeName: String = "",
    val departmentId: String? = null,
    val departmentName: String = "",
    val affiliations: List<UserAffiliationDto> = emptyList(),
    val title: String,
    val summary: Map<String, String> = emptyMap(),
    val publishedDate: String? = null,
    val url: String,
    val attachmentUrl: String? = null,
    val isPinned: Boolean = false,
)

@Serializable
internal enum class AnnouncementScopeDto {
    @SerialName("AFFILIATION")
    AFFILIATION,

    @SerialName("DEPARTMENT")
    DEPARTMENT,
}

@Serializable
internal enum class UserAffiliationDto {
    @SerialName("ALL")
    ALL,

    @SerialName("UNDERGRADUATE")
    UNDERGRADUATE,

    @SerialName("GRADUATE")
    GRADUATE,

    @SerialName("INTERNATIONAL")
    INTERNATIONAL,

    @SerialName("FACULTY")
    FACULTY,
}
