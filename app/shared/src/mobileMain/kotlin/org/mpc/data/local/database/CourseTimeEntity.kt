package org.mpc.data.local.database

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "course_time",
    primaryKeys = ["semester", "serialNo", "dayCode", "periodCode"],
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["semester", "serialNo"],
            childColumns = ["semester", "serialNo"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class CourseTimeEntity(
    val semester: String,
    val serialNo: String,
    val dayCode: Int,
    val periodCode: String,
)
