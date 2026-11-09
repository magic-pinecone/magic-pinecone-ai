package org.mpc.presentation.model

import org.mpc.domain.model.CourseDay
import org.mpc.domain.model.CoursePlan
import org.mpc.domain.model.CourseSerialNo
import org.mpc.domain.model.CourseSummary
import org.mpc.domain.model.CourseTime
import org.mpc.domain.model.CourseType

data class CourseTimetableBlock(
    val title: String,
    val serialNo: CourseSerialNo,
    val time: CourseTime,
    val span: Int,
    val type: CourseType,
)

internal fun CoursePlan.toTimetableBlocks(): List<CourseTimetableBlock> = selectedCourses.values
    .flatMap(CourseSummary::toTimetableBlocks)
    .sortedWith(
        compareBy(
            { it.time.day.order },
            { it.time.period.order },
            { it.serialNo.value },
        ),
    )

internal fun CourseSummary.toTimetableBlocks(): List<CourseTimetableBlock> = classTimes
    .asSequence()
    .filter { it.day != CourseDay.UNKNOWN }
    .sortedWith(
        compareBy(
            { it.day.order },
            { it.period.order },
        ),
    ).distinct()
    .fold(mutableListOf()) { blocks, current ->
        val previous = blocks.lastOrNull()

        if (
            previous == null ||
            current.day != previous.time.day ||
            // every entry is within the same course, so we don't need to worry if it extends on other courses
            current.period.order != previous.time.period.order + previous.span
        ) {
            blocks +=
                CourseTimetableBlock(
                    title = title,
                    serialNo = serialNo,
                    time = current,
                    span = 1,
                    type = courseType,
                )
        } else {
            blocks[blocks.lastIndex] =
                previous.copy(
                    span = previous.span + 1,
                )
        }

        blocks
    }
