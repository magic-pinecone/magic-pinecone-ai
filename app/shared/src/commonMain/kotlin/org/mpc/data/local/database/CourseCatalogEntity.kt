package org.mpc.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "course_catalog")
data class CourseCatalogEntity(
    @PrimaryKey
    val semester: String,
    val lastUpdatedEpochMillis: Long,
)
