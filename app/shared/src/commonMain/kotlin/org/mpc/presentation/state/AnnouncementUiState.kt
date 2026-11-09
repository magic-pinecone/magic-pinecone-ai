package org.mpc.presentation.state

import org.mpc.domain.model.AnnouncementScope
import org.mpc.domain.model.AnnouncementSummary
import org.mpc.domain.model.DEFAULT_NCU_DEPARTMENTS
import org.mpc.domain.model.UserAffiliation

enum class AnnouncementLoadStatus {
    LOADING,
    LOADED,
    NEEDS_BACKEND,
    UNAVAILABLE,
}

data class AnnouncementUiState(
    val status: AnnouncementLoadStatus = AnnouncementLoadStatus.LOADING,
    val selectedScope: AnnouncementScope = AnnouncementScope.AFFILIATION,
    val selectedAffiliation: UserAffiliation = UserAffiliation.ALL,
    val selectedDepartment: String = "",
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val availableCategories: List<String> = emptyList(),
    val availableDepartments: List<String> = DEFAULT_NCU_DEPARTMENTS,
    val filteredAnnouncements: List<AnnouncementSummary> = emptyList(),
)

internal data class AnnouncementFilterSpec(
    val selectedScope: AnnouncementScope = AnnouncementScope.AFFILIATION,
    val selectedAffiliation: UserAffiliation = UserAffiliation.ALL,
    val selectedDepartment: String = "",
    val selectedCategory: String? = null,
    val searchQuery: String = "",
)

internal fun buildAnnouncementUiState(
    status: AnnouncementLoadStatus,
    allAnnouncements: List<AnnouncementSummary>,
    filter: AnnouncementFilterSpec = AnnouncementFilterSpec(),
): AnnouncementUiState {
    val dynamicDepartments = allAnnouncements
        .filter { it.scope == AnnouncementScope.DEPARTMENT }
        .map { it.departmentName.ifBlank { it.unitName } }
        .filter { it.isNotBlank() }
    val availableDepartments = (DEFAULT_NCU_DEPARTMENTS + dynamicDepartments).distinct()

    val scopeMatched = allAnnouncements.filter { item ->
        when (filter.selectedScope) {
            AnnouncementScope.AFFILIATION ->
                item.scope == AnnouncementScope.AFFILIATION && matchesAffiliation(item, filter.selectedAffiliation)

            AnnouncementScope.DEPARTMENT ->
                item.scope == AnnouncementScope.DEPARTMENT && matchesDepartment(item, filter.selectedDepartment)
        }
    }

    val availableCategories = scopeMatched.map { it.category }.filter { it.isNotBlank() }.distinct()
    val effectiveCategory = filter.selectedCategory?.takeIf { it in availableCategories }
    val needle = filter.searchQuery.trim().lowercase()

    val filtered = scopeMatched
        .filter { item -> effectiveCategory == null || item.category == effectiveCategory }
        .filter { item -> needle.isEmpty() || matchesSearchQuery(item, needle) }
        .sortedWith(
            compareByDescending<AnnouncementSummary> { it.isPinned }
                .thenByDescending { it.publishedDate.orEmpty() },
        )

    return AnnouncementUiState(
        status = status,
        selectedScope = filter.selectedScope,
        selectedAffiliation = filter.selectedAffiliation,
        selectedDepartment = filter.selectedDepartment,
        selectedCategory = effectiveCategory,
        searchQuery = filter.searchQuery,
        availableCategories = availableCategories,
        availableDepartments = availableDepartments,
        filteredAnnouncements = filtered,
    )
}

private fun matchesAffiliation(item: AnnouncementSummary, affiliation: UserAffiliation): Boolean = affiliation == UserAffiliation.ALL ||
    UserAffiliation.ALL in item.affiliations ||
    affiliation in item.affiliations

private fun matchesDepartment(item: AnnouncementSummary, department: String): Boolean = department.isBlank() ||
    item.departmentName == department ||
    item.unitName == department

private fun matchesSearchQuery(item: AnnouncementSummary, needle: String): Boolean = item.title.lowercase().contains(needle) ||
    item.unitName.lowercase().contains(needle) ||
    item.category.lowercase().contains(needle) ||
    item.departmentName.lowercase().contains(needle) ||
    item.summary.values.any { it.lowercase().contains(needle) }
