package org.mpc.presentation.views.coursePlanning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePeriod
import org.mpc.domain.model.CoursePlan
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType
import org.mpc.domain.model.PasswordCardType
import org.mpc.domain.model.TimetableColorMode
import org.mpc.presentation.model.CourseTimetableBlock
import org.mpc.presentation.model.TimetableCourseCategory
import org.mpc.presentation.model.assignTimetableColorSlots
import org.mpc.presentation.model.shuffledTimetablePaletteIndices
import org.mpc.presentation.model.timetableCategory
import org.mpc.presentation.model.toTimetableBlocks
import org.mpc.presentation.theme.LocalTimetableColorMode
import org.mpc.presentation.theme.MpcShapes
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.theme.TimetableBlockColors
import org.mpc.presentation.theme.platformTimetablePalette

@Composable
fun CoursePlanningTimetableSuccessView(
    plan: CoursePlan,
    modifier: Modifier,
    onCourseClick: (semester: String, course: CourseSummary) -> Unit = { _, _ -> },
    colorMode: TimetableColorMode = LocalTimetableColorMode.current,
    palette: List<TimetableBlockColors> = platformTimetablePalette(
        MpcTheme.colors.background.luminance() < DARK_SURFACE_LUMINANCE_THRESHOLD,
    ),
) {
    require(palette.isNotEmpty()) { "A timetable palette must contain at least one color pair" }
    val neutralColors = TimetableBlockColors(
        MpcTheme.colors.fillQuiet,
        MpcTheme.colors.onFillQuiet,
    )
    var savedSlots by rememberSaveable(plan.semester, stateSaver = TimetableColorSlotsSaver) {
        mutableStateOf<Map<String, Int>>(emptyMap())
    }
    val slots = assignTimetableColorSlots(savedSlots, plan.selectedCourses.keys.map { it.value }, palette.size)
    SideEffect { if (savedSlots != slots) savedSlots = slots }
    val shuffledIndices = remember(plan.semester, palette.size) {
        shuffledTimetablePaletteIndices(plan.semester, palette.size)
    }
    val rows = CoursePeriod.entries.size
    val timetableBlocks = plan.toTimetableBlocks()
    val columns = timetableBlocks.maxOfOrNull { it.time.day.order }?.coerceAtLeast(5) ?: 5
    val coursesWithoutTime = plan.selectedCourses.values.count { course ->
        course.classTimes.none { it.day != CourseDay.UNKNOWN }
    }

    Box(
        modifier =
        modifier
            .padding(horizontal = MpcSpacing.space4, vertical = MpcSpacing.space2),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (coursesWithoutTime > 0) {
                Text(
                    text = "$coursesWithoutTime 門課程尚無上課時間，可在已選課程中查看。",
                    style = MpcTheme.typography.caption,
                    color = MpcTheme.colors.labelSecondary,
                    modifier = Modifier.padding(bottom = MpcSpacing.space2),
                )
            }

            if (colorMode == TimetableColorMode.CATEGORY && plan.selectedCourses.isNotEmpty()) {
                TimetableCategoryLegend(
                    categories = plan.selectedCourses.values.map { it.timetableCategory() }.distinct().sortedBy { it.ordinal },
                    palette = palette,
                    neutralColors = neutralColors,
                )
            }

            Row {
                Spacer(modifier = Modifier.width(MpcSpacing.space4))
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                ) {
                    CourseDay.entries
                        .take(columns)
                        .forEach {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f),
                            ) {
                                Text(
                                    text = it.description,
                                    color = MpcTheme.colors.labelSecondary,
                                    style = MpcTheme.typography.label,
                                )
                            }
                        }
                }
            }

            Spacer(Modifier.height(MpcSpacing.space1))

            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier
                        .width(16.dp)
                        .fillMaxHeight(),
                ) {
                    CoursePeriod.entries
                        .forEach {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                Text(
                                    text = it.description,
                                    color = MpcTheme.colors.labelSecondary,
                                    style = MpcTheme.typography.micro,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                }
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val columnWidth = maxWidth / columns
                    val rowHeight = maxHeight / rows

                    TimetableBackground(
                        columns = columns,
                        rows = rows,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        repeat(columns * rows) {
                            TimetableBackgroundCell()
                        }
                    }

                    timetableBlocks.forEach { block ->
                        val course = plan.selectedCourses[block.serialNo]
                        val (colors, categoryLabel) = timetableCourseAppearance(
                            course = course,
                            colorMode = colorMode,
                            palette = palette,
                            neutralColors = neutralColors,
                            shuffledPaletteIndex = shuffledIndices[(slots[block.serialNo.value] ?: 0) % palette.size],
                        )
                        TimetableForegroundCell(
                            block = block,
                            colors = colors,
                            categoryLabel = categoryLabel,
                            onClick = course?.let { selectedCourse ->
                                { onCourseClick(plan.semester, selectedCourse) }
                            },
                            modifier =
                            Modifier
                                .offset(
                                    x = columnWidth * (block.time.day.order - 1),
                                    y = rowHeight * (block.time.period.order - 1),
                                ).width(columnWidth)
                                .height(rowHeight * block.span),
                        )
                    }

                    if (plan.selectedCourses.isEmpty()) {
                        Text(
                            text = "搜尋課程並加入，開始安排課表",
                            color = MpcTheme.colors.labelSecondary,
                            style = MpcTheme.typography.supporting,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
    }
}

private const val DARK_SURFACE_LUMINANCE_THRESHOLD = 0.5f

private fun timetableCourseAppearance(
    course: CourseSummary?,
    colorMode: TimetableColorMode,
    palette: List<TimetableBlockColors>,
    neutralColors: TimetableBlockColors,
    shuffledPaletteIndex: Int,
): Pair<TimetableBlockColors, String?> = when (colorMode) {
    TimetableColorMode.CATEGORY -> {
        val category = course?.timetableCategory() ?: TimetableCourseCategory.UNKNOWN
        categoryColors(category, palette, neutralColors) to category.label
    }

    TimetableColorMode.SHUFFLED -> palette[shuffledPaletteIndex] to null
}

private val TimetableColorSlotsSaver = mapSaver<Map<String, Int>>(
    save = { it },
    restore = { saved -> saved.mapValues { (_, value) -> value as Int } },
)

private fun categoryColors(
    category: TimetableCourseCategory,
    palette: List<TimetableBlockColors>,
    neutralColors: TimetableBlockColors,
): TimetableBlockColors = if (category == TimetableCourseCategory.UNKNOWN) {
    neutralColors
} else {
    palette[category.ordinal % palette.size]
}

@Composable
private fun TimetableCategoryLegend(
    categories: List<TimetableCourseCategory>,
    palette: List<TimetableBlockColors>,
    neutralColors: TimetableBlockColors,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = MpcSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
    ) {
        items(categories, key = { it.name }) { category ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space1),
            ) {
                Surface(
                    color = categoryColors(category, palette, neutralColors).container,
                    shape = MpcShapes.swatch,
                    modifier = Modifier.width(12.dp).height(12.dp),
                ) {}
                Text(
                    text = category.label,
                    color = MpcTheme.colors.labelSecondary,
                    style = MpcTheme.typography.micro,
                )
            }
        }
    }
}

@Composable
fun TimetableBackground(
    columns: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gridColor = MpcTheme.colors.separator
    Layout(
        modifier = modifier.drawBehind {
            val strokeWidth = 0.5.dp.toPx()
            for (column in 0..columns) {
                val x = size.width * column / columns
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth)
            }
            for (row in 0..rows) {
                val y = size.height * row / rows
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth)
            }
        },
        content = content,
    ) { measurables, constraints ->
        val cellWidth = constraints.maxWidth / columns
        val cellHeight = constraints.maxHeight / rows

        val cellConstraints = Constraints.fixed(
            width = cellWidth,
            height = cellHeight,
        )

        val placeable = measurables.map { measurable ->
            measurable.measure(cellConstraints)
        }

        layout(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        ) {
            placeable.forEachIndexed { index, placeable ->
                val column = index % columns
                val row = index / columns

                placeable.placeRelative(
                    x = column * cellWidth,
                    y = row * cellHeight,
                )
            }
        }
    }
}

@Composable
fun TimetableBackgroundCell() {
    Box(modifier = Modifier.fillMaxSize())
}

@Composable
fun TimetableForegroundCell(
    block: CourseTimetableBlock,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    colors: TimetableBlockColors = TimetableBlockColors(
        MpcTheme.colors.accentContainer,
        MpcTheme.colors.onAccentContainer,
    ),
    categoryLabel: String? = null,
) {
    Surface(
        modifier =
        modifier
            .semantics { categoryLabel?.let { stateDescription = it } }
            .then(
                onClick?.let { click ->
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "查看課程詳細資訊",
                        onClick = click,
                    )
                } ?: Modifier,
            )
            .padding(MpcSpacing.hairline),
        shape = MpcShapes.block,
        color = colors.container,
    ) {
        Box(
            contentAlignment = Alignment.TopStart,
            modifier = Modifier.fillMaxSize(),
        ) {
            colors.leadingAccent?.let { accent ->
                Surface(
                    color = accent,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier
                        .padding(start = 2.dp, top = 3.dp, bottom = 3.dp)
                        .width(3.dp)
                        .fillMaxHeight(),
                ) {}
            }
            Text(
                modifier = Modifier.padding(
                    start = if (colors.leadingAccent != null) MpcSpacing.space2 else MpcSpacing.space1,
                    end = MpcSpacing.space1,
                    top = 3.dp,
                    bottom = 3.dp,
                ),
                text = block.title,
                color = colors.content,
                fontWeight = FontWeight.Medium,
                maxLines = block.span.coerceAtLeast(1),
                overflow = TextOverflow.Ellipsis,
                style = MpcTheme.typography.micro,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Preview
@Composable
internal fun PreviewCoursePlanningTimetableSuccessView(
    plan: CoursePlan =
        CoursePlan(
            semester = "115-1",
            selectedCourses =
            mapOf(
                CourseSerialNo("36019")
                    to
                    CourseSummary(
                        serialNo = CourseSerialNo("36019"),
                        classNo = "ENA103-*",
                        title = "專題討論（III）",
                        credit = 0.0,
                        passwordCard = PasswordCardType.NONE,
                        teachers = listOf("鄭明敏", "林居慶", "林進榮", "林伯勳"),
                        classTimes =
                        listOf(
                            CourseTime(CourseDay.FRIDAY, CoursePeriod.A),
                            CourseTime(CourseDay.FRIDAY, CoursePeriod.B),
                            CourseTime(CourseDay.FRIDAY, CoursePeriod.C),
                        ),
                        limitCnt = 0,
                        admitCnt = 0,
                        waitCnt = 0,
                        collegeName = "工學院",
                        departmentName = "環境工程研究所碩士班",
                        courseType = CourseType.ELECTIVE,
                        detailUrl = "https://cis.ncu.edu.tw/Course/main/support/courseDetail.html?crs=36019",
                    ),
            ),
        ),
) {
    MpcTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text("我的課表")
                    },
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            CoursePlanningTimetableSuccessView(
                plan = plan,
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}
