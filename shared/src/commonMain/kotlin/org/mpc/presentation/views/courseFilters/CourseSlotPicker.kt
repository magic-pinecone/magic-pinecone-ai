package org.mpc.presentation.views.courseFilters

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CourseTime
import org.mpc.presentation.preview.ComposePreviewScenario
import org.mpc.presentation.preview.ComposePreviewScenarioContent
import org.mpc.presentation.theme.MpcShapes
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import kotlin.math.floor

private val WEEKDAYS = CourseDay.entries.filter { it.order <= CourseDay.FRIDAY.order }
private val FULL_WEEK = CourseDay.entries.filter { it != CourseDay.UNKNOWN }
private val PERIODS = CoursePeriod.entries.sortedBy { it.order }

/** A day-by-period grid where tapping toggles a slot and dragging paints across slots. */
@Composable
fun CourseSlotPicker(
    selected: Set<CourseTime>,
    onSelectedChange: (Set<CourseTime>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasWeekend = selected.any { it.day !in WEEKDAYS }
    var prefersFullWeek by rememberSaveable { mutableStateOf(false) }
    val showsFullWeek = prefersFullWeek || hasWeekend
    val days = if (showsFullWeek) FULL_WEEK else WEEKDAYS

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeekSpanToggle(
                showsFullWeek = showsFullWeek,
                onShowsFullWeekChange = { fullWeek ->
                    prefersFullWeek = fullWeek
                    // Weekend slots hidden by the weekday view would still filter results.
                    if (!fullWeek && hasWeekend) onSelectedChange(selected.filterTo(mutableSetOf()) { it.day in WEEKDAYS })
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onSelectedChange(emptySet()) },
                enabled = selected.isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(contentColor = MpcTheme.colors.accent),
            ) {
                Text("清除時段", style = MpcTheme.typography.label)
            }
        }
        SlotGrid(days = days, selected = selected, onSelectedChange = onSelectedChange)
    }
}

@Composable
private fun WeekSpanToggle(
    showsFullWeek: Boolean,
    onShowsFullWeekChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = 220.dp)
            .clip(TRACK_SHAPE)
            .background(MpcTheme.colors.fillQuiet)
            .padding(2.dp)
            .selectableGroup(),
    ) {
        listOf("平日" to false, "全週" to true).forEach { (label, fullWeek) ->
            val isSelected = showsFullWeek == fullWeek
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 32.dp)
                    .clip(TRACK_SHAPE)
                    .background(if (isSelected) MpcTheme.colors.surface else MpcTheme.colors.fillQuiet)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onShowsFullWeekChange(fullWeek) },
                    ),
            ) {
                Text(
                    text = label,
                    style = MpcTheme.typography.label,
                    color = if (isSelected) MpcTheme.colors.label else MpcTheme.colors.onFillQuiet,
                )
            }
        }
    }
}

private val TRACK_SHAPE = RoundedCornerShape(10.dp)
private val CELL_HEIGHT = 30.dp
private val CELL_GAP = 4.dp
private val PERIOD_LABEL_WIDTH = 20.dp

@Composable
private fun SlotGrid(
    days: List<CourseDay>,
    selected: Set<CourseTime>,
    onSelectedChange: (Set<CourseTime>) -> Unit,
) {
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelectedChange by rememberUpdatedState(onSelectedChange)

    Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
            Box(Modifier.width(PERIOD_LABEL_WIDTH))
            days.forEach { day ->
                Text(
                    text = day.description,
                    style = MpcTheme.typography.label,
                    color = MpcTheme.colors.labelSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
            Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP), modifier = Modifier.width(PERIOD_LABEL_WIDTH)) {
                PERIODS.forEach { period ->
                    Box(Modifier.height(CELL_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(period.description, style = MpcTheme.typography.micro, color = MpcTheme.colors.labelSecondary)
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(CELL_GAP),
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(days) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val first = slotAt(down.position, size, days) ?: return@awaitEachGesture
                            val stroke = SlotStroke(currentSelected, first)
                            down.consume()
                            currentOnSelectedChange(stroke.result)
                            do {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                if (change?.pressed == true) {
                                    change.consume()
                                    val slot = slotAt(change.position, size, days)
                                    if (slot != null && stroke.paint(slot)) currentOnSelectedChange(stroke.result)
                                }
                            } while (change?.pressed == true)
                        }
                    },
            ) {
                PERIODS.forEach { period ->
                    Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                        days.forEach { day ->
                            val slot = CourseTime(day, period)
                            SlotCell(
                                slot = slot,
                                isSelected = slot in selected,
                                onToggle = { onSelectedChange(SlotStroke(selected, slot).result) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotCell(
    slot: CourseTime,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = remember(slot) { "星期${slot.day.description} 第 ${slot.period.description} 節" }
    Box(
        modifier = modifier
            .height(CELL_HEIGHT)
            .clip(MpcShapes.badge)
            .background(if (isSelected) MpcTheme.colors.accent else MpcTheme.colors.fillQuiet)
            .semantics {
                contentDescription = label
                role = Role.Checkbox
                toggleableState = ToggleableState(isSelected)
                onClick {
                    onToggle()
                    true
                }
            },
    )
}

private fun slotAt(
    position: Offset,
    size: IntSize,
    days: List<CourseDay>,
): CourseTime? {
    val day = days.getOrNull(floor(position.x / size.width * days.size).toInt()) ?: return null
    val period = PERIODS.getOrNull(floor(position.y / size.height * PERIODS.size).toInt()) ?: return null
    return CourseTime(day, period)
}

/** One tap or drag: the first slot touched decides whether the whole stroke adds or removes. */
internal class SlotStroke(
    private val base: Set<CourseTime>,
    first: CourseTime,
) {
    private val adding = first !in base
    private val painted = mutableSetOf(first)

    val result: Set<CourseTime> get() = if (adding) base + painted else base - painted

    fun paint(slot: CourseTime): Boolean = painted.add(slot)
}

@Preview
@Composable
internal fun PreviewCourseSlotPicker() {
    MpcTheme {
        Box(Modifier.background(MpcTheme.colors.background)) {
            ComposePreviewScenarioContent(ComposePreviewScenario.SLOT_PICKER)
        }
    }
}
