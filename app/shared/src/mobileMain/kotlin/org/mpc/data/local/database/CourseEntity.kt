package org.mpc.data.local.database

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "course",
    primaryKeys = ["semester", "serialNo"],
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
data class CourseEntity(
    val semester: String,
    val serialNo: String,
    val classNo: String,
    val title: String,
    val credit: Double,
    val passwordCard: String,
    val limitCnt: Int,
    val admitCnt: Int,
    val waitCnt: Int,
    val collegeName: String,
    val departmentName: String,
    val courseType: String,
    val detailUrl: String,
)
