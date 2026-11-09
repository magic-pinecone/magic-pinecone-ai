package org.mpc.data.mapper.dto

import org.mpc.data.dto.AnnouncementDto
import org.mpc.data.dto.AnnouncementListResponseDto
import org.mpc.data.dto.AnnouncementScopeDto
import org.mpc.data.dto.UserAffiliationDto
import org.mpc.domain.model.AnnouncementFeed
import org.mpc.domain.model.AnnouncementScope
import org.mpc.domain.model.AnnouncementSummary
import org.mpc.domain.model.UserAffiliation

internal fun AnnouncementListResponseDto.toDomain(): AnnouncementFeed = AnnouncementFeed(
    lastUpdated = lastUpdated,
    announcements = announcements.map { it.toDomain() },
)

internal fun AnnouncementDto.toDomain(): AnnouncementSummary = AnnouncementSummary(
    announcementId = announcementId,
    scope = scope.toDomain(),
    sourceId = sourceId,
    category = category,
    unitName = unitName,
    collegeId = collegeId,
    collegeName = collegeName,
    departmentId = departmentId,
    departmentName = departmentName,
    affiliations = affiliations.map { it.toDomain() }.ifEmpty { listOf(UserAffiliation.ALL) },
    title = title,
    summary = summary,
    publishedDate = publishedDate,
    url = url,
    attachmentUrl = attachmentUrl,
    isPinned = isPinned,
)

internal fun AnnouncementScopeDto.toDomain(): AnnouncementScope = when (this) {
    AnnouncementScopeDto.AFFILIATION -> AnnouncementScope.AFFILIATION
    AnnouncementScopeDto.DEPARTMENT -> AnnouncementScope.DEPARTMENT
}

internal fun UserAffiliationDto.toDomain(): UserAffiliation = when (this) {
    UserAffiliationDto.ALL -> UserAffiliation.ALL
    UserAffiliationDto.UNDERGRADUATE -> UserAffiliation.UNDERGRADUATE
    UserAffiliationDto.GRADUATE -> UserAffiliation.GRADUATE
    UserAffiliationDto.INTERNATIONAL -> UserAffiliation.INTERNATIONAL
    UserAffiliationDto.FACULTY -> UserAffiliation.FACULTY
}
