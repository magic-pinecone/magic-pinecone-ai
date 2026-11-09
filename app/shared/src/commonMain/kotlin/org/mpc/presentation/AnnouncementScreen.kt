package org.mpc.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.compose.stringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.domain.model.AnnouncementScope
import org.mpc.domain.model.UserAffiliation
import org.mpc.presentation.state.AnnouncementLoadStatus
import org.mpc.presentation.state.AnnouncementUiState
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.viewModel.AnnouncementViewModel
import org.mpc.shared.MR
import org.mpc.shared.announcements_all_categories
import org.mpc.shared.announcements_all_departments
import org.mpc.shared.announcements_empty_description
import org.mpc.shared.announcements_empty_title
import org.mpc.shared.announcements_needs_backend_description
import org.mpc.shared.announcements_needs_backend_title
import org.mpc.shared.announcements_search_affiliation_placeholder
import org.mpc.shared.announcements_search_department_placeholder
import org.mpc.shared.announcements_unavailable_description
import org.mpc.shared.announcements_unavailable_title

@Composable
fun AnnouncementScreen(
    onOpenUrl: (title: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnnouncementViewModel = metroViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        AnnouncementFilterHeader(
            uiState = uiState,
            onSelectScope = viewModel::selectScope,
            onSelectAffiliation = viewModel::selectAffiliation,
            onSelectDepartment = viewModel::selectDepartment,
            onSelectCategory = viewModel::selectCategory,
            onSearchQueryChange = viewModel::updateSearchQuery,
        )

        AnnouncementBodyContent(
            uiState = uiState,
            onRefresh = { viewModel.loadAnnouncements(forceRefresh = true) },
            onOpenUrl = onOpenUrl,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AnnouncementFilterHeader(
    uiState: AnnouncementUiState,
    onSelectScope: (AnnouncementScope) -> Unit = {},
    onSelectAffiliation: (UserAffiliation) -> Unit = {},
    onSelectDepartment: (String) -> Unit = {},
    onSelectCategory: (String?) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MpcSpacing.space4, vertical = MpcSpacing.space2),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AnnouncementScope.entries.forEachIndexed { index, scope ->
                SegmentedButton(
                    selected = uiState.selectedScope == scope,
                    onClick = { onSelectScope(scope) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AnnouncementScope.entries.size,
                    ),
                    label = { Text(scope.label) },
                )
            }
        }

        val placeholderRes = if (uiState.selectedScope == AnnouncementScope.AFFILIATION) {
            MR.strings.announcements_search_affiliation_placeholder
        } else {
            MR.strings.announcements_search_department_placeholder
        }

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(placeholderRes),
                    color = MpcTheme.colors.labelSecondary,
                )
            },
        )

        when (uiState.selectedScope) {
            AnnouncementScope.AFFILIATION -> AffiliationSelectorRow(
                selected = uiState.selectedAffiliation,
                onSelect = onSelectAffiliation,
            )

            AnnouncementScope.DEPARTMENT -> DepartmentSelectorRow(
                selectedDepartment = uiState.selectedDepartment,
                departments = uiState.availableDepartments,
                onSelect = onSelectDepartment,
            )
        }

        if (uiState.availableCategories.size > 1) {
            CategorySelectorRow(
                selectedCategory = uiState.selectedCategory,
                categories = uiState.availableCategories,
                onSelect = onSelectCategory,
            )
        }
    }
}

@Composable
private fun AffiliationSelectorRow(
    selected: UserAffiliation,
    onSelect: (UserAffiliation) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
    ) {
        UserAffiliation.entries.forEach { affiliation ->
            FilterChip(
                selected = selected == affiliation,
                onClick = { onSelect(affiliation) },
                label = { Text(affiliation.label) },
            )
        }
    }
}

@Composable
private fun DepartmentSelectorRow(
    selectedDepartment: String,
    departments: List<String>,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
    ) {
        FilterChip(
            selected = selectedDepartment.isBlank(),
            onClick = { onSelect("") },
            label = { Text(stringResource(MR.strings.announcements_all_departments)) },
        )
        departments.forEach { department ->
            FilterChip(
                selected = selectedDepartment == department,
                onClick = { onSelect(department) },
                label = { Text(department) },
            )
        }
    }
}

@Composable
private fun CategorySelectorRow(
    selectedCategory: String?,
    categories: List<String>,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(MR.strings.announcements_all_categories)) },
        )
        categories.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelect(category) },
                label = { Text(category) },
            )
        }
    }
}

@Composable
private fun AnnouncementBodyContent(
    uiState: AnnouncementUiState,
    onRefresh: () -> Unit,
    onOpenUrl: (title: String, url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.status == AnnouncementLoadStatus.LOADING && uiState.filteredAnnouncements.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val triggerRefresh: () -> Unit = {
        isRefreshing = true
        onRefresh()
        isRefreshing = false
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing || uiState.status == AnnouncementLoadStatus.LOADING,
        onRefresh = triggerRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when (uiState.status) {
            AnnouncementLoadStatus.NEEDS_BACKEND -> EeclassMessage(
                title = stringResource(MR.strings.announcements_needs_backend_title),
                description = stringResource(MR.strings.announcements_needs_backend_description),
                onRetry = onRefresh,
            )

            AnnouncementLoadStatus.UNAVAILABLE -> EeclassMessage(
                title = stringResource(MR.strings.announcements_unavailable_title),
                description = stringResource(MR.strings.announcements_unavailable_description),
                onRetry = onRefresh,
            )

            AnnouncementLoadStatus.LOADING,
            AnnouncementLoadStatus.LOADED,
            -> {
                if (uiState.filteredAnnouncements.isEmpty()) {
                    EeclassMessage(
                        title = stringResource(MR.strings.announcements_empty_title),
                        description = stringResource(MR.strings.announcements_empty_description),
                        onRetry = onRefresh,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = MpcSpacing.space4,
                            vertical = MpcSpacing.space2,
                        ),
                        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
                    ) {
                        items(
                            items = uiState.filteredAnnouncements,
                            key = { it.announcementId },
                        ) { announcement ->
                            AnnouncementCard(
                                announcement = announcement,
                                onOpenUrl = onOpenUrl,
                            )
                        }
                    }
                }
            }
        }
    }
}
