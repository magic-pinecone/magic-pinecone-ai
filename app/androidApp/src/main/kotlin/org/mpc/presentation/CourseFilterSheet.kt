package org.mpc.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.localized
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.domain.model.CourseFilter
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.SeatAvailability
import org.mpc.presentation.formatting.labelDesc
import org.mpc.presentation.state.CourseFilterOptions
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.views.courseFilters.CourseSlotPicker
import org.mpc.shared.MR
import org.mpc.shared.catalog_all
import org.mpc.shared.catalog_clear
import org.mpc.shared.catalog_picker_college
import org.mpc.shared.catalog_picker_department
import org.mpc.shared.catalog_picker_teacher
import org.mpc.shared.catalog_schedule_hint
import org.mpc.shared.catalog_search_colleges
import org.mpc.shared.catalog_search_departments
import org.mpc.shared.catalog_search_teachers
import org.mpc.shared.catalog_seats_full
import org.mpc.shared.catalog_seats_open
import org.mpc.shared.catalog_section_credits
import org.mpc.shared.catalog_section_seats
import org.mpc.shared.catalog_section_type
import org.mpc.shared.catalog_selected_count
import org.mpc.shared.catalog_sheet_title
import org.mpc.shared.catalog_tab_filters
import org.mpc.shared.catalog_tab_schedule
import org.mpc.shared.catalog_type_elective
import org.mpc.shared.catalog_type_required

internal enum class FilterTab(
    val label: StringResource,
) {
    FILTERS(MR.strings.catalog_tab_filters),
    SCHEDULE(MR.strings.catalog_tab_schedule),
}

private enum class OptionPicker(
    val text: FilterPickerText,
) {
    DEPARTMENT(FilterPickerText(MR.strings.catalog_picker_department, MR.strings.catalog_search_departments)),
    COLLEGE(FilterPickerText(MR.strings.catalog_picker_college, MR.strings.catalog_search_colleges)),
    TEACHER(FilterPickerText(MR.strings.catalog_picker_teacher, MR.strings.catalog_search_teachers)),
}

internal class CourseFilterActions(
    val onAdd: (CourseFilter) -> Unit,
    val onRemove: (CourseFilter) -> Unit,
    val onClear: () -> Unit,
    val onScheduleChange: (Set<CourseTime>) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION") // The replacement state API has no skipPartiallyExpanded equivalent in this alpha.
@Composable
internal fun CourseFilterSheet(
    filters: List<CourseFilter>,
    options: CourseFilterOptions,
    actions: CourseFilterActions,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        // The option pickers scroll long lists, so open at full height instead of a half-way detent.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = if (isWideWindow()) WIDE_SHEET_WIDTH else BottomSheetDefaults.SheetMaxWidth,
    ) {
        CourseFilterSheetContent(filters = filters, options = options, actions = actions)
    }
}

private val WIDE_SHEET_WIDTH = 880.dp

@Composable
private fun isWideWindow(): Boolean = currentWindowAdaptiveInfo().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

@Composable
internal fun CourseFilterSheetContent(
    filters: List<CourseFilter>,
    options: CourseFilterOptions,
    actions: CourseFilterActions,
    isWide: Boolean = isWideWindow(),
    initialTab: FilterTab = FilterTab.FILTERS,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var picker by rememberSaveable { mutableStateOf<OptionPicker?>(null) }
    BackHandler(enabled = picker != null) { picker = null }

    // A picker needs a bounded height for its list; other pages size to their content.
    val heightModifier = if (picker != null) Modifier.fillMaxHeight(PICKER_HEIGHT_FRACTION) else Modifier
    Column(modifier = Modifier.fillMaxWidth().then(heightModifier)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(MR.strings.catalog_sheet_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = actions.onClear, enabled = filters.isNotEmpty()) { Text(stringResource(MR.strings.catalog_clear)) }
        }
        val filtersPane = @Composable { modifier: Modifier ->
            FiltersPane(
                filters = filters,
                options = options,
                actions = actions,
                picker = picker,
                onPickerChange = { picker = it },
                modifier = modifier,
            )
        }
        val schedulePane = @Composable { modifier: Modifier ->
            SchedulePane(filters = filters, onScheduleChange = actions.onScheduleChange, modifier = modifier)
        }
        if (isWide) {
            Row(modifier = Modifier.weight(1f, fill = picker != null).padding(top = 8.dp)) {
                filtersPane(Modifier.weight(1f))
                VerticalDivider(modifier = Modifier.padding(vertical = 8.dp))
                schedulePane(Modifier.weight(1f))
            }
        } else {
            val tabLabels = FilterTab.entries.associateWith { stringResource(it.label) }
            SectionPicker(
                items = FilterTab.entries,
                selected = tab,
                onSelectedChange = { tab = it },
                label = { tabLabels.getValue(it) },
                icon = { if (it == FilterTab.FILTERS) Icons.Default.FilterList else Icons.Default.Schedule },
            )
            when (tab) {
                FilterTab.FILTERS -> filtersPane(Modifier.weight(1f, fill = picker != null))
                FilterTab.SCHEDULE -> schedulePane(Modifier.weight(1f, fill = false))
            }
        }
    }
}

private const val PICKER_HEIGHT_FRACTION = 0.9f

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersPane(
    filters: List<CourseFilter>,
    options: CourseFilterOptions,
    actions: CourseFilterActions,
    picker: OptionPicker?,
    onPickerChange: (OptionPicker?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (picker != null) {
        CourseFilterMultiSelect(
            text = picker.text,
            options = picker.choices(options),
            selected = selectedNames(picker, filters),
            onToggle = { name ->
                val filter = picker.toFilter(name)
                if (filter in filters) actions.onRemove(filter) else actions.onAdd(filter)
            },
            onBack = { onPickerChange(null) },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val type = filters.filterIsInstance<CourseFilter.Type>()
        FilterSection(MR.strings.catalog_section_type) {
            ExclusiveChoice(
                choices = listOf(
                    null to MR.strings.catalog_all,
                    CourseFilter.Type(CourseType.REQUIRED) to MR.strings.catalog_type_required,
                    CourseFilter.Type(CourseType.ELECTIVE) to MR.strings.catalog_type_elective,
                ),
                isSelected = { choice -> if (choice == null) type.isEmpty() else type == listOf(choice) },
                onSelect = { choice ->
                    type.forEach(actions.onRemove)
                    choice?.let(actions.onAdd)
                },
            )
        }
        val seats = filters.filterIsInstance<CourseFilter.Seats>()
        FilterSection(MR.strings.catalog_section_seats) {
            ExclusiveChoice(
                choices = listOf(
                    null to MR.strings.catalog_all,
                    CourseFilter.Seats(SeatAvailability.OPEN) to MR.strings.catalog_seats_open,
                    CourseFilter.Seats(SeatAvailability.FULL) to MR.strings.catalog_seats_full,
                ),
                isSelected = { choice -> if (choice == null) seats.isEmpty() else seats == listOf(choice) },
                onSelect = { choice ->
                    seats.forEach(actions.onRemove)
                    choice?.let(actions.onAdd)
                },
            )
        }
        if (options.credits.isNotEmpty()) {
            FilterSection(MR.strings.catalog_section_credits) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.credits.forEach { credits ->
                        val filter = CourseFilter.Credits(credits)
                        val isSelected = filter in filters
                        FilterChip(
                            selected = isSelected,
                            onClick = { if (isSelected) actions.onRemove(filter) else actions.onAdd(filter) },
                            label = { Text(filter.labelDesc().localized()) },
                        )
                    }
                }
            }
        }
        OptionPicker.entries.forEach { entry ->
            val count = selectedNames(entry, filters).size
            ListItem(
                headlineContent = { Text(stringResource(entry.text.title)) },
                supportingContent = {
                    Text(
                        if (count == 0) stringResource(MR.strings.catalog_all) else stringResource(MR.strings.catalog_selected_count, count),
                    )
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickable { onPickerChange(entry) },
            )
        }
    }
}

private fun OptionPicker.toFilter(name: String): CourseFilter = when (this) {
    OptionPicker.DEPARTMENT -> CourseFilter.Department(name)
    OptionPicker.COLLEGE -> CourseFilter.College(name)
    OptionPicker.TEACHER -> CourseFilter.Teacher(name)
}

private fun OptionPicker.choices(options: CourseFilterOptions): List<String> = when (this) {
    OptionPicker.DEPARTMENT -> options.departments
    OptionPicker.COLLEGE -> options.colleges
    OptionPicker.TEACHER -> options.teachers
}

private fun selectedNames(
    picker: OptionPicker,
    filters: List<CourseFilter>,
): Set<String> = when (picker) {
    OptionPicker.DEPARTMENT -> filters.filterIsInstance<CourseFilter.Department>().map { it.name }
    OptionPicker.COLLEGE -> filters.filterIsInstance<CourseFilter.College>().map { it.name }
    OptionPicker.TEACHER -> filters.filterIsInstance<CourseFilter.Teacher>().map { it.name }
}.toSet()

@Composable
private fun SchedulePane(
    filters: List<CourseFilter>,
    onScheduleChange: (Set<CourseTime>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = filters.filterIsInstance<CourseFilter.Schedule>().firstOrNull()?.slots.orEmpty()
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CourseSlotPicker(selected = slots, onSelectedChange = onScheduleChange)
        Text(
            text = stringResource(MR.strings.catalog_schedule_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterSection(
    title: StringResource,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun <T : CourseFilter> ExclusiveChoice(
    choices: List<Pair<T?, StringResource>>,
    isSelected: (T?) -> Boolean,
    onSelect: (T?) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, (choice, label) ->
            SegmentedButton(
                selected = isSelected(choice),
                onClick = { onSelect(choice) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                label = { Text(stringResource(label)) },
            )
        }
    }
}

private val PreviewOptions = CourseFilterOptions(
    teachers = listOf("王小明", "李大華"),
    departments = listOf("資訊工程學系", "數學系"),
    colleges = listOf("理學院", "資訊電機學院"),
    credits = listOf(1.0, 2.0, 3.0),
)

private val PreviewActions = CourseFilterActions({}, {}, {}, {})

@Preview(showBackground = true, name = "Filters")
@Composable
internal fun PreviewCourseFilterSheetFilters() {
    MpcTheme {
        CourseFilterSheetContent(PreviewFilters, PreviewOptions, PreviewActions, isWide = false)
    }
}

@Preview(showBackground = true, name = "Schedule")
@Composable
internal fun PreviewCourseFilterSheetSchedule() {
    MpcTheme {
        CourseFilterSheetContent(PreviewFilters, PreviewOptions, PreviewActions, isWide = false, initialTab = FilterTab.SCHEDULE)
    }
}

@Preview(showBackground = true, widthDp = 840, name = "Wide")
@Composable
internal fun PreviewCourseFilterSheetWide() {
    MpcTheme {
        CourseFilterSheetContent(PreviewFilters, PreviewOptions, PreviewActions, isWide = true)
    }
}
