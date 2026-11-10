package org.mpc.data.local.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "course_plan",
    foreignKeys = [
        ForeignKey(
            entity = CourseCatalogEntity::class,
            parentColumns = ["semester"],
            childColumns = ["semester"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class CoursePlanEntity(
    @PrimaryKey
    val semester: String,
    val updatedAtEpochMillis: Long,
)
