package org.mpc.presentation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.SlotMatch
import org.mpc.presentation.state.CoursePlanUiState
import org.mpc.presentation.viewModel.CoursePlanViewModel
import org.mpc.presentation.viewModel.CourseSearchViewModel
import org.mpc.presentation.views.coursePlanning.CourseSearchResultView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseCatalogScreen(
    modifier: Modifier,
    onCourseClick: (semester: String, course: CourseSummary) -> Unit = { _, _ -> },
    isCompact: Boolean = false,
    searchViewModel: CourseSearchViewModel = metroViewModel(),
    planViewModel: CoursePlanViewModel = metroViewModel(),
) {
    val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val planUiState by planViewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(searchUiState.query) }
    var showsFilterSheet by rememberSaveable { mutableStateOf(false) }
    val filterCount = searchUiState.filters.size
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // pickSuggestion clears the query in the view model; mirror it into the field.
    LaunchedEffect(searchUiState.query) {
        if (query != searchUiState.query) query = searchUiState.query
    }

    val runSearch = {
        searchViewModel.updateQuery(semester = searchUiState.semester, query = query)
        searchViewModel.onSearch()
    }
    val submitSearch = {
        runSearch()
        // Leaving the field closes the suggestions so the new results show.
        focusManager.clearFocus()
    }
    val selectedCourseSerialNumbers =
        (planUiState as? CoursePlanUiState.Success)?.plan?.selectedCourses?.keys.orEmpty()

    val searchContainerColor = SearchBarDefaults.colors().containerColor

    Column(modifier = modifier) {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = { value ->
                query = value
                searchViewModel.updateQuery(semester = searchUiState.semester, query = value)
                if (value.isEmpty()) runSearch()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜尋課程") },
            colors = SearchBarDefaults.inputFieldColors(
                focusedContainerColor = searchContainerColor,
                unfocusedContainerColor = searchContainerColor,
            ),
            leadingIcon = {
                IconButton(onClick = { submitSearch() }) {
                    Icon(Icons.Default.Search, contentDescription = "搜尋課程")
                }
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            submitSearch()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "清除搜尋")
                        }
                    }
                    IconButton(onClick = { showsFilterSheet = true }) {
                        BadgedBox(badge = {
                            if (filterCount > 0) Badge { Text("$filterCount") }
                        }) {
                            Icon(Icons.Default.FilterList, contentDescription = "篩選條件，已套用 $filterCount 項")
                        }
                    }
                }
            },
            onSearch = { submitSearch() },
            interactionSource = interactionSource,
            // Results stay in the catalog pane instead of opening an expanded search surface.
            expanded = false,
            onExpandedChange = {},
        )
        if (searchUiState.filters.isNotEmpty()) {
            CourseFilterChipRow(
                filters = searchUiState.filters,
                onRemove = searchViewModel::removeFilter,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        // Suggestions take over the results area while typing; results are stale until the next search.
        if (isFocused && searchUiState.suggestions.isNotEmpty()) {
            CourseFilterSuggestionList(
                suggestions = searchUiState.suggestions,
                onSelect = searchViewModel::pickSuggestion,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            CourseSearchResultView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                uiState = searchUiState.result,
                selectedCourseSerialNumbers = selectedCourseSerialNumbers,
                onCourseClick = { course -> onCourseClick(searchUiState.semester, course) },
                onToggleCourse = planViewModel::toggleCourse,
                isCompact = isCompact,
                canEditPlan = planUiState is CoursePlanUiState.Success,
                onRetry = { submitSearch() },
            )
        }
    }

    if (showsFilterSheet) {
        CourseFilterSheet(
            filters = searchUiState.filters,
            options = searchUiState.filterOptions,
            actions = CourseFilterActions(
                onAdd = searchViewModel::addFilter,
                onRemove = searchViewModel::removeFilter,
                onClear = searchViewModel::clearFilters,
                onScheduleChange = { slots -> searchViewModel.setSchedule(slots, SlotMatch.FITS_WITHIN) },
            ),
            onDismissRequest = { showsFilterSheet = false },
        )
    }
}
