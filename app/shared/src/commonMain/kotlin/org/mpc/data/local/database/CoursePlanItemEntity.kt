package org.mpc.data.local.database

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "course_plan_item",
    primaryKeys = ["semester", "serialNo"],
    foreignKeys = [
        ForeignKey(
            entity = CoursePlanEntity::class,
            parentColumns = ["semester"],
            childColumns = ["semester"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["semester", "serialNo"],
            childColumns = ["semester", "serialNo"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class CoursePlanItemEntity(
    val semester: String,
    val serialNo: String,
)
