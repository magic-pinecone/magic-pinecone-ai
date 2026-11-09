package org.mpc.data.mapper.entities

import org.mpc.data.local.database.CoursePlanEntity
import org.mpc.data.local.database.CoursePlanItemEntity
import org.mpc.domain.model.CoursePlan
import kotlin.time.Clock

internal fun CoursePlan.toCoursePlanEntity(): CoursePlanEntity = CoursePlanEntity(
    semester = semester,
    updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
)

internal fun CoursePlan.toCoursePlanItemsEntity(): List<CoursePlanItemEntity> = selectedCourses.values.map {
    CoursePlanItemEntity(
        semester = semester,
        serialNo = it.serialNo.value,
    )
}
