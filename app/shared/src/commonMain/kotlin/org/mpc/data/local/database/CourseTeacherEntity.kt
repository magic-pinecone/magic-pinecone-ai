package org.mpc.data.local.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "course_teacher",
    primaryKeys = ["semester", "serialNo", "position"],
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["semester", "serialNo"],
            childColumns = ["semester", "serialNo"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["teacherName"]),
    ],
)
data class CourseTeacherEntity(
    val semester: String,
    val serialNo: String,
    val position: Int,
    val teacherName: String,
)
