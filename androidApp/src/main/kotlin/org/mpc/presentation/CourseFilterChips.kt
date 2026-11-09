package org.mpc.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.SeatAvailability
import org.mpc.presentation.formatting.label
import org.mpc.presentation.formatting.stableKey
import org.mpc.presentation.theme.MpcTheme

@Composable
internal fun CourseFilterChipRow(
    filters: List<CourseFilter>,
    onRemove: (CourseFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters, key = { it.stableKey }) { filter ->
            InputChip(
                selected = false,
                onClick = { onRemove(filter) },
                label = { Text(filter.label()) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "移除${filter.label()}",
                    )
                },
            )
        }
    }
}

@Composable
internal fun CourseFilterSuggestionList(
    suggestions: List<CourseFilter>,
    onSelect: (CourseFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(suggestions, key = { it.stableKey }) { filter ->
            ListItem(
                headlineContent = { Text(filter.label()) },
                leadingContent = { Icon(filter.icon(), contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable(onClickLabel = "加入篩選") { onSelect(filter) },
            )
        }
    }
}

internal fun CourseFilter.icon(): ImageVector = when (this) {
    is CourseFilter.Teacher -> Icons.Default.Person
    is CourseFilter.Department -> Icons.Default.School
    is CourseFilter.College -> Icons.Default.AccountBalance
    is CourseFilter.Type -> Icons.Default.Category
    is CourseFilter.Credits -> Icons.Default.Grade
    is CourseFilter.Seats -> Icons.Default.EventSeat
    is CourseFilter.Schedule -> Icons.Default.Schedule
}

internal val PreviewFilters = listOf(
    CourseFilter.Teacher("王小明"),
    CourseFilter.Department("資訊工程學系"),
    CourseFilter.Type(CourseType.REQUIRED),
    CourseFilter.Seats(SeatAvailability.OPEN),
)

@Preview(showBackground = true)
@Composable
internal fun PreviewCourseFilterChipRow() {
    MpcTheme {
        Column {
            CourseFilterChipRow(filters = PreviewFilters, onRemove = {})
            CourseFilterSuggestionList(suggestions = PreviewFilters, onSelect = {})
        }
    }
}
